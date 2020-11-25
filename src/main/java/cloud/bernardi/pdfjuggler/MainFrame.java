/*
 * Copyright (C) 2020 Paolo Bernardi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package cloud.bernardi.pdfjuggler;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 *
 * @author rnd
 */
public class MainFrame extends JFrame {

    /**
     * Default thumbnail width, in pixels.
     */
    public static final int DEFAULT_THUMB_WIDTH = 85;

    /**
     * Default thumbnail height, in pixels.
     */
    public static final int DEFAULT_THUMB_HEIGHT = 110;

    private static final int MIN_THUMB_WIDTH = DEFAULT_THUMB_WIDTH - 50;
    private static final int MIN_THUMB_HEIGHT = DEFAULT_THUMB_HEIGHT - 50;
    private static final int MAX_THUMB_WIDTH = DEFAULT_THUMB_WIDTH + 50;
    private static final int MAX_THUMB_HEIGHT = DEFAULT_THUMB_HEIGHT + 50;
    private static final int DEFAULT_THUMB_DPI = 10;

    private final Map<String, Page> pageMap = new HashMap<>();
    private final DefaultListModel pageListModel;

    // Package-local parameters for the persistent configuration
    boolean isMaximized;
    File lastOpenDir;
    int thumbWidth = DEFAULT_THUMB_WIDTH;
    int thumbHeight = DEFAULT_THUMB_HEIGHT;
    private final Config config = new Config();

    class Page {

        private static final int MAX_FILENAME_LENGTH = 15;

        public int index;
        public File file;
        public BufferedImage thumbnail;
        public int rotation;

        public Page(int index, File file, BufferedImage thumbnail) {
            this.index = index;
            this.file = file;
            this.thumbnail = thumbnail;
        }

        private BufferedImage resize(BufferedImage img, int newW, int newH) {
            Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
            BufferedImage dimg = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = dimg.createGraphics();
            g2d.drawImage(tmp, 0, 0, null);
            g2d.dispose();
            return dimg;
        }

        public void rotate(int degrees) {
            rotation += degrees;
            AffineTransform tx = new AffineTransform();
            double radians = degrees * Math.PI / 180;
            tx.rotate(radians, thumbnail.getWidth() / 2, thumbnail.getHeight() / 2);
            AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
            thumbnail = op.filter(thumbnail, null);
        }

        public String getCaption() {
            String fileName = this.file.getName();
            int fileNameLen = fileName.length();
            if (fileNameLen > MAX_FILENAME_LENGTH) {
                fileName = fileName.substring(0, MAX_FILENAME_LENGTH - 2) + "…";
            }
            int page = this.index + 1;
            return String.format("<html><div style='text-align: center'><small>%s</small><br>Page %d</div></html>", fileName, page);
        }

        public ImageIcon getThumbnail() {
            int w = thumbnail.getWidth();
            int h = thumbnail.getHeight();
            int newW;
            int newH;
            if (h > w) {
                newH = thumbHeight;
                newW = w * thumbHeight / h;
            } else {
                newW = thumbWidth;
                newH = h * thumbWidth / w;
            }
            return new ImageIcon(this.resize(thumbnail, newW, newH));
        }
    }

    class ImageListRenderer extends DefaultListCellRenderer {

        private final Font font = new Font("helvetica", Font.PLAIN, 12);

        @Override
        public Component getListCellRendererComponent(
                JList list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {

            Page page = pageMap.get(value.toString());
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, page.getCaption(), index, isSelected, cellHasFocus);
            label.setIcon(page.getThumbnail());
            label.setHorizontalTextPosition(JLabel.CENTER);
            label.setVerticalTextPosition(JLabel.BOTTOM);
            label.setFont(font);
            return label;
        }
    }

    /**
     * Creates new form NewJFrame
     */
    public MainFrame() {
        initComponents();
        setTitle(Const.APPNAME);
        BufferedImage image;
        try {
            image = ImageIO.read(getClass().getResource("/cloud/bernardi/pdfjuggler/icons/icon-32.png"));
            setIconImage(image);
        } catch (IOException ex) {
            // NOP
        }
        pageList.setCellRenderer(new ImageListRenderer());
        pageListModel = (DefaultListModel) pageList.getModel();
        pageList.setTransferHandler(new DndTransferHandler(pageList, pageListModel, this));
        pbStatus.setVisible(false);
        config.load(this);
    }

    private void addPdfFiles() {
        JFileChooser fileChooser = new JFileChooser();
        if (lastOpenDir == null) {
            lastOpenDir = new File(System.getProperty("user.home"));
        }
        fileChooser.setCurrentDirectory(lastOpenDir);
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setFileFilter(new FileNameExtensionFilter("PDF Files", "pdf"));
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            if (selectedFiles.length > 0) {
                addPdfFiles(selectedFiles);
                lastOpenDir = selectedFiles[0].getParentFile();
                config.set(this);
            }
        }
    }

    void addPdfFiles(File[] pdfFiles) {
        final MainFrame mainFrame = this;
        new Thread(() -> {
            int offset = pageListModel.size();
            for (File pdfFile : pdfFiles) {
                try (PDDocument document = PDDocument.load(pdfFile)) {
                    PDFRenderer pdfRenderer = new PDFRenderer(document);
                    int numPages = document.getNumberOfPages();
                    pbStatus.setVisible(true);
                    pbStatus.setString("Loading PDF...");
                    pbStatus.setMaximum(numPages);
                    for (int page = 0; page < numPages; ++page) {
                        BufferedImage bim = pdfRenderer.renderImageWithDPI(page, DEFAULT_THUMB_DPI, ImageType.RGB);
                        final String key = "" + (offset + page);
                        final int realPage = page + 1;
                        final int offsettedPage = offset + page;
                        pageMap.put(key, new Page(page, pdfFile, bim));
                        SwingUtilities.invokeLater(() -> {
                            pbStatus.setValue(realPage);
                            pageListModel.add(offsettedPage, key);
                        });
                    }
                    SwingUtilities.invokeLater(() -> {
                        pbStatus.setValue(0);
                        pbStatus.setString("");
                        pbStatus.setVisible(false);
                    });
                    offset += numPages;
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(mainFrame, "Error while reading PDF file", Const.APPNAME, JOptionPane.ERROR_MESSAGE);
                    });
                }
            }
        }).start();
    }

    private void savePdf() {
        if (pageListModel.isEmpty()) {
            return;
        }
        JFileChooser fileChooser = new JFileChooser();
        if (lastOpenDir == null) {
            lastOpenDir = new File(System.getProperty("user.home"));
        }
        fileChooser.setCurrentDirectory(lastOpenDir);
        fileChooser.setFileFilter(new FileNameExtensionFilter("PDF Files", "pdf"));
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            if (!selectedFile.getName().toLowerCase().endsWith(".pdf")) {
                selectedFile = new File(selectedFile.getAbsolutePath() + ".pdf");
            }
            savePdf(selectedFile);
            lastOpenDir = selectedFile.getParentFile();
            config.set(this);
        }
    }

    private void savePdf(File file) {
        final MainFrame mainFrame = this;
        new Thread(() -> {
            Map<File, PDPageTree> pdfMap = new HashMap<>();
            PDDocument newDoc = new PDDocument();
            Enumeration e = pageListModel.elements();
            int numPages = pageListModel.size();
            pbStatus.setString("Saving PDF...");
            pbStatus.setMaximum(numPages);
            try {
                int count = 0;
                while (e.hasMoreElements()) {
                    String key = (String) e.nextElement();
                    Page page = pageMap.get(key);
                    PDPageTree allPages;
                    if (!pdfMap.containsKey(page.file)) {
                        PDDocument sourcePdf = PDDocument.load(page.file);
                        allPages = sourcePdf.getDocumentCatalog().getPages();
                        pdfMap.put(page.file, allPages);
                    } else {
                        allPages = pdfMap.get(page.file);
                    }
                    PDPage sourcePage = allPages.get(page.index);
                    sourcePage.setRotation(page.rotation);
                    newDoc.addPage(sourcePage);
                    ++count;
                    final int realPage = count;
                    SwingUtilities.invokeLater(() -> {
                        pbStatus.setValue(realPage);
                    });
                }
                newDoc.save(file);
                SwingUtilities.invokeLater(() -> {
                    pbStatus.setValue(0);
                    pbStatus.setString("");
                    JOptionPane.showMessageDialog(mainFrame, "The PDF file was saved correctly", Const.APPNAME, JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(mainFrame, "Error while reading PDF file", Const.APPNAME, JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }

    private void removeAllPages() {
        if (pageListModel.isEmpty()) {
            return;
        }
        int result = JOptionPane.showConfirmDialog(this, "Do you really want to remove all pages?", Const.APPNAME,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (result == JOptionPane.YES_OPTION) {
            pageListModel.removeAllElements();
            pageMap.clear();
        }
    }

    private void removeSelectedPages() {
        if (pageListModel.isEmpty()) {
            return;
        }
        int[] selection = pageList.getSelectedIndices();
        if (selection.length == 0) {
            return;
        }
        for (int i = 0; i < selection.length; ++i) {
            String key = (String) pageListModel.remove(selection[i]);
            pageMap.remove(key);
            for (int k = i + 1; k < selection.length; k++) {
                if (selection[k] > selection[i]) {
                    --selection[k];
                }
            }
        }
        int size = pageListModel.size();
        if (selection[0] < size) {
            pageList.setSelectedIndex(selection[0]);
        } else {
            pageList.setSelectedIndex(size - 1);
        }
    }

    private void closeApplication() {
        config.save(this);
        dispose();
    }

    private void showAboutDialog() {
        AboutDialog ad = new AboutDialog(this, true);
        Rectangle mfBounds = getBounds();
        int mfcx = mfBounds.x + (mfBounds.width / 2);
        int mfcy = mfBounds.y + (mfBounds.height / 2);
        Rectangle adBounds = ad.getBounds();
        Dimension preferred = ad.getPreferredSize();
        adBounds.width = preferred.width;
        adBounds.height = preferred.height;
        adBounds.x = mfcx - adBounds.width / 2;
        adBounds.y = mfcy - adBounds.height / 2;
        ad.setBounds(adBounds);
        ad.setVisible(true);
    }

    private void openOnlineHelp() {
        Web.openWebsite(Const.ONLINE_HELP);
    }

    private void rotateSelection(int degrees) {
        int[] selection = pageList.getSelectedIndices();
        for (int i : selection) {
            String key = (String) pageListModel.get(i);
            Page page = pageMap.get(key);
            page.rotate(degrees);
        }
        pageList.repaint();
    }

    private void zoom(int pixels) {
        if (pageListModel.isEmpty()) {
            return;
        }
        int newThumbWidth = thumbWidth + pixels;
        int newThumbHeight = thumbHeight + pixels;
        if (newThumbWidth >= MIN_THUMB_WIDTH
                && newThumbWidth <= MAX_THUMB_WIDTH
                && newThumbHeight >= MIN_THUMB_HEIGHT
                && newThumbHeight <= MAX_THUMB_HEIGHT) {
            thumbWidth = newThumbWidth;
            thumbHeight = newThumbHeight;
            // TODO: this could be done way better... however, repaint/revalidate won't do
            pageListModel.set(0, pageListModel.get(0));
            config.set(this);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.JToolBar jToolBar1 = new javax.swing.JToolBar();
        btAddFile = new javax.swing.JButton();
        btSave = new javax.swing.JButton();
        btZoomIn = new javax.swing.JButton();
        btZoomOut = new javax.swing.JButton();
        btRotateClockwise = new javax.swing.JButton();
        btRotateCounterclockwise = new javax.swing.JButton();
        btRemoveSelectedPages = new javax.swing.JButton();
        javax.swing.JScrollPane jScrollPane1 = new javax.swing.JScrollPane();
        pageList = new javax.swing.JList<>();
        pbStatus = new javax.swing.JProgressBar();
        javax.swing.JMenuBar jMenuBar1 = new javax.swing.JMenuBar();
        javax.swing.JMenu jMenu1 = new javax.swing.JMenu();
        jmiAddFile = new javax.swing.JMenuItem();
        jmiSave = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        jmiExit = new javax.swing.JMenuItem();
        javax.swing.JMenu jMenu4 = new javax.swing.JMenu();
        jmiZoomIn = new javax.swing.JMenuItem();
        jmiZoomOut = new javax.swing.JMenuItem();
        javax.swing.JMenu jMenu3 = new javax.swing.JMenu();
        jmiRotateClockwise = new javax.swing.JMenuItem();
        jmiRotateCounterclockwise = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        jmiRemoveSelectedPages = new javax.swing.JMenuItem();
        jmiRemoveAllPages = new javax.swing.JMenuItem();
        javax.swing.JMenu jMenu2 = new javax.swing.JMenu();
        jmiOnlineHelp = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JPopupMenu.Separator();
        jmiAbout = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                formComponentResized(evt);
            }
        });
        addWindowStateListener(new java.awt.event.WindowStateListener() {
            public void windowStateChanged(java.awt.event.WindowEvent evt) {
                formWindowStateChanged(evt);
            }
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
        });

        jToolBar1.setFloatable(false);
        jToolBar1.setRollover(true);
        jToolBar1.setMaximumSize(new java.awt.Dimension(295, 32));
        jToolBar1.setMinimumSize(new java.awt.Dimension(295, 32));
        jToolBar1.setPreferredSize(new java.awt.Dimension(295, 32));

        btAddFile.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cloud/bernardi/pdfjuggler/icons/open.png"))); // NOI18N
        btAddFile.setToolTipText("Add File...");
        btAddFile.setFocusable(false);
        btAddFile.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btAddFile.setMaximumSize(new java.awt.Dimension(32, 32));
        btAddFile.setMinimumSize(new java.awt.Dimension(32, 32));
        btAddFile.setPreferredSize(new java.awt.Dimension(32, 32));
        btAddFile.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btAddFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btAddFileActionPerformed(evt);
            }
        });
        jToolBar1.add(btAddFile);

        btSave.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cloud/bernardi/pdfjuggler/icons/saveas.png"))); // NOI18N
        btSave.setToolTipText("Save As...");
        btSave.setFocusable(false);
        btSave.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btSave.setMaximumSize(new java.awt.Dimension(32, 32));
        btSave.setMinimumSize(new java.awt.Dimension(32, 32));
        btSave.setPreferredSize(new java.awt.Dimension(32, 32));
        btSave.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btSaveActionPerformed(evt);
            }
        });
        jToolBar1.add(btSave);

        btZoomIn.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cloud/bernardi/pdfjuggler/icons/zoomin.png"))); // NOI18N
        btZoomIn.setToolTipText("Zoom In");
        btZoomIn.setFocusable(false);
        btZoomIn.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btZoomIn.setMaximumSize(new java.awt.Dimension(32, 32));
        btZoomIn.setMinimumSize(new java.awt.Dimension(32, 32));
        btZoomIn.setPreferredSize(new java.awt.Dimension(32, 32));
        btZoomIn.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btZoomIn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btZoomInActionPerformed(evt);
            }
        });
        jToolBar1.add(btZoomIn);

        btZoomOut.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cloud/bernardi/pdfjuggler/icons/zoomout.png"))); // NOI18N
        btZoomOut.setToolTipText("Zoom Out");
        btZoomOut.setFocusable(false);
        btZoomOut.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btZoomOut.setMaximumSize(new java.awt.Dimension(32, 32));
        btZoomOut.setMinimumSize(new java.awt.Dimension(32, 32));
        btZoomOut.setPreferredSize(new java.awt.Dimension(32, 32));
        btZoomOut.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btZoomOut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btZoomOutActionPerformed(evt);
            }
        });
        jToolBar1.add(btZoomOut);

        btRotateClockwise.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cloud/bernardi/pdfjuggler/icons/rotateright.png"))); // NOI18N
        btRotateClockwise.setToolTipText("Rotate Clockwise");
        btRotateClockwise.setFocusable(false);
        btRotateClockwise.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btRotateClockwise.setMaximumSize(new java.awt.Dimension(32, 32));
        btRotateClockwise.setMinimumSize(new java.awt.Dimension(32, 32));
        btRotateClockwise.setPreferredSize(new java.awt.Dimension(32, 32));
        btRotateClockwise.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btRotateClockwise.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btRotateClockwiseActionPerformed(evt);
            }
        });
        jToolBar1.add(btRotateClockwise);

        btRotateCounterclockwise.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cloud/bernardi/pdfjuggler/icons/rotateleft.png"))); // NOI18N
        btRotateCounterclockwise.setToolTipText("Rotate Counterclockwise");
        btRotateCounterclockwise.setFocusable(false);
        btRotateCounterclockwise.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btRotateCounterclockwise.setMaximumSize(new java.awt.Dimension(32, 32));
        btRotateCounterclockwise.setMinimumSize(new java.awt.Dimension(32, 32));
        btRotateCounterclockwise.setPreferredSize(new java.awt.Dimension(32, 32));
        btRotateCounterclockwise.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btRotateCounterclockwise.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btRotateCounterclockwiseActionPerformed(evt);
            }
        });
        jToolBar1.add(btRotateCounterclockwise);

        btRemoveSelectedPages.setIcon(new javax.swing.ImageIcon(getClass().getResource("/cloud/bernardi/pdfjuggler/icons/delete.png"))); // NOI18N
        btRemoveSelectedPages.setToolTipText("Remove Selected Pages");
        btRemoveSelectedPages.setFocusable(false);
        btRemoveSelectedPages.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btRemoveSelectedPages.setMaximumSize(new java.awt.Dimension(32, 32));
        btRemoveSelectedPages.setMinimumSize(new java.awt.Dimension(32, 32));
        btRemoveSelectedPages.setPreferredSize(new java.awt.Dimension(32, 32));
        btRemoveSelectedPages.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btRemoveSelectedPages.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btRemoveSelectedPagesActionPerformed(evt);
            }
        });
        jToolBar1.add(btRemoveSelectedPages);

        pageList.setModel(new DefaultListModel<String>());
        pageList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        pageList.setDragEnabled(true);
        pageList.setDropMode(javax.swing.DropMode.INSERT);
        pageList.setLayoutOrientation(javax.swing.JList.HORIZONTAL_WRAP);
        pageList.setVisibleRowCount(-1);
        jScrollPane1.setViewportView(pageList);

        pbStatus.setString("");
        pbStatus.setStringPainted(true);

        jMenu1.setText("File");

        jmiAddFile.setText("Add File...");
        jmiAddFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jmiAddFileActionPerformed(evt);
            }
        });
        jMenu1.add(jmiAddFile);

        jmiSave.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        jmiSave.setText("Save As...");
        jmiSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jmiSaveActionPerformed(evt);
            }
        });
        jMenu1.add(jmiSave);
        jMenu1.add(jSeparator2);

        jmiExit.setText("Exit");
        jmiExit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jmiExitActionPerformed(evt);
            }
        });
        jMenu1.add(jmiExit);

        jMenuBar1.add(jMenu1);

        jMenu4.setText("View");

        jmiZoomIn.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_PLUS, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        jmiZoomIn.setText("Zoom In");
        jmiZoomIn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jmiZoomInActionPerformed(evt);
            }
        });
        jMenu4.add(jmiZoomIn);

        jmiZoomOut.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_MINUS, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        jmiZoomOut.setText("Zoom Out");
        jmiZoomOut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jmiZoomOutActionPerformed(evt);
            }
        });
        jMenu4.add(jmiZoomOut);

        jMenuBar1.add(jMenu4);

        jMenu3.setText("Edit");

        jmiRotateClockwise.setText("Rotate Clockwise");
        jmiRotateClockwise.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jmiRotateClockwiseActionPerformed(evt);
            }
        });
        jMenu3.add(jmiRotateClockwise);

        jmiRotateCounterclockwise.setText("Rotate Counterclockwise");
        jmiRotateCounterclockwise.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jmiRotateCounterclockwiseActionPerformed(evt);
            }
        });
        jMenu3.add(jmiRotateCounterclockwise);
        jMenu3.add(jSeparator1);

        jmiRemoveSelectedPages.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0));
        jmiRemoveSelectedPages.setText("Remove Selected Pages");
        jmiRemoveSelectedPages.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jmiRemoveSelectedPagesActionPerformed(evt);
            }
        });
        jMenu3.add(jmiRemoveSelectedPages);

        jmiRemoveAllPages.setText("Remove All Pages");
        jmiRemoveAllPages.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jmiRemoveAllPagesActionPerformed(evt);
            }
        });
        jMenu3.add(jmiRemoveAllPages);

        jMenuBar1.add(jMenu3);

        jMenu2.setText("Help");

        jmiOnlineHelp.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F1, 0));
        jmiOnlineHelp.setText("Online Help");
        jmiOnlineHelp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jmiOnlineHelpActionPerformed(evt);
            }
        });
        jMenu2.add(jmiOnlineHelp);
        jMenu2.add(jSeparator3);

        jmiAbout.setText("About");
        jmiAbout.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jmiAboutActionPerformed(evt);
            }
        });
        jMenu2.add(jmiAbout);

        jMenuBar1.add(jMenu2);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jToolBar1, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
            .addComponent(pbStatus, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jToolBar1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 403, Short.MAX_VALUE)
                .addGap(2, 2, 2)
                .addComponent(pbStatus, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btAddFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btAddFileActionPerformed
        addPdfFiles();
    }//GEN-LAST:event_btAddFileActionPerformed

    private void btSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btSaveActionPerformed
        savePdf();
    }//GEN-LAST:event_btSaveActionPerformed

    private void btRemoveSelectedPagesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btRemoveSelectedPagesActionPerformed
        removeSelectedPages();
    }//GEN-LAST:event_btRemoveSelectedPagesActionPerformed

    private void jmiAddFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jmiAddFileActionPerformed
        addPdfFiles();
    }//GEN-LAST:event_jmiAddFileActionPerformed

    private void jmiRemoveAllPagesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jmiRemoveAllPagesActionPerformed
        removeAllPages();
    }//GEN-LAST:event_jmiRemoveAllPagesActionPerformed

    private void jmiSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jmiSaveActionPerformed
        savePdf();
    }//GEN-LAST:event_jmiSaveActionPerformed

    private void jmiRemoveSelectedPagesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jmiRemoveSelectedPagesActionPerformed
        removeSelectedPages();
    }//GEN-LAST:event_jmiRemoveSelectedPagesActionPerformed

    private void jmiExitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jmiExitActionPerformed
        closeApplication();
    }//GEN-LAST:event_jmiExitActionPerformed

    private void jmiAboutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jmiAboutActionPerformed
        showAboutDialog();
    }//GEN-LAST:event_jmiAboutActionPerformed

    private void btRotateCounterclockwiseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btRotateCounterclockwiseActionPerformed
        rotateSelection(-90);
    }//GEN-LAST:event_btRotateCounterclockwiseActionPerformed

    private void btRotateClockwiseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btRotateClockwiseActionPerformed
        rotateSelection(90);
    }//GEN-LAST:event_btRotateClockwiseActionPerformed

    private void jmiRotateClockwiseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jmiRotateClockwiseActionPerformed
        rotateSelection(90);
    }//GEN-LAST:event_jmiRotateClockwiseActionPerformed

    private void jmiRotateCounterclockwiseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jmiRotateCounterclockwiseActionPerformed
        rotateSelection(-90);
    }//GEN-LAST:event_jmiRotateCounterclockwiseActionPerformed

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        config.save(this);
    }//GEN-LAST:event_formWindowClosed

    private void formComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentResized
        config.set(this);
    }//GEN-LAST:event_formComponentResized

    private void formWindowStateChanged(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowStateChanged
        int oldState = evt.getOldState();
        int newState = evt.getNewState();
        isMaximized = (oldState & JFrame.MAXIMIZED_BOTH) == 0 && (newState & JFrame.MAXIMIZED_BOTH) != 0;
    }//GEN-LAST:event_formWindowStateChanged

    private void jmiOnlineHelpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jmiOnlineHelpActionPerformed
        openOnlineHelp();
    }//GEN-LAST:event_jmiOnlineHelpActionPerformed

    private void jmiZoomInActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jmiZoomInActionPerformed
        zoom(10);
    }//GEN-LAST:event_jmiZoomInActionPerformed

    private void btZoomInActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btZoomInActionPerformed
        zoom(10);
    }//GEN-LAST:event_btZoomInActionPerformed

    private void jmiZoomOutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jmiZoomOutActionPerformed
        zoom(-10);
    }//GEN-LAST:event_jmiZoomOutActionPerformed

    private void btZoomOutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btZoomOutActionPerformed
        zoom(-10);
    }//GEN-LAST:event_btZoomOutActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(MainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        java.awt.EventQueue.invokeLater(() -> {
            new MainFrame().setVisible(true);
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btAddFile;
    private javax.swing.JButton btRemoveSelectedPages;
    private javax.swing.JButton btRotateClockwise;
    private javax.swing.JButton btRotateCounterclockwise;
    private javax.swing.JButton btSave;
    private javax.swing.JButton btZoomIn;
    private javax.swing.JButton btZoomOut;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JPopupMenu.Separator jSeparator3;
    private javax.swing.JMenuItem jmiAbout;
    private javax.swing.JMenuItem jmiAddFile;
    private javax.swing.JMenuItem jmiExit;
    private javax.swing.JMenuItem jmiOnlineHelp;
    private javax.swing.JMenuItem jmiRemoveAllPages;
    private javax.swing.JMenuItem jmiRemoveSelectedPages;
    private javax.swing.JMenuItem jmiRotateClockwise;
    private javax.swing.JMenuItem jmiRotateCounterclockwise;
    private javax.swing.JMenuItem jmiSave;
    private javax.swing.JMenuItem jmiZoomIn;
    private javax.swing.JMenuItem jmiZoomOut;
    private javax.swing.JList<String> pageList;
    private javax.swing.JProgressBar pbStatus;
    // End of variables declaration//GEN-END:variables
}

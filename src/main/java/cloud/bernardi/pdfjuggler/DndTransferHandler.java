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

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.TransferHandler;
import static javax.swing.TransferHandler.MOVE;

/**
 * Drag&drop transfer handler that supports both internal item reordering and
 * external files import.
 *
 * Adapted from
 * https://stackoverflow.com/questions/16586562/reordering-jlist-with-drag-and-drop
 *
 * @author rnd
 */
public class DndTransferHandler extends TransferHandler {

    private final JList<String> dndList;
    private final DefaultListModel<String> strings;
    private int index;
    private boolean beforeIndex = false; // Start with `false` therefore if it is removed from or added to the list it still works
    private MainFrame mainFrame;

    /**
     * Create a new drag&drop transfer handler.
     *
     * @param dndList
     * @param strings
     * @param mainFrame
     */
    public DndTransferHandler(JList<String> dndList, DefaultListModel<String> strings, MainFrame mainFrame) {
        this.dndList = dndList;
        this.strings = strings;
        this.mainFrame = mainFrame;
    }

    @Override
    public int getSourceActions(JComponent comp) {
        return MOVE;
    }

    @Override
    public Transferable createTransferable(JComponent comp) {
        index = dndList.getSelectedIndex();
        return new StringSelection(dndList.getSelectedValue());
    }

    @Override
    public void exportDone(JComponent comp, Transferable trans, int action) {
        if (action == MOVE) {
            if (beforeIndex) {
                strings.remove(index + 1);
            } else {
                strings.remove(index);
            }
        }
    }

    @Override
    public boolean canImport(TransferHandler.TransferSupport support) {
        return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || support.isDataFlavorSupported(DataFlavor.stringFlavor);
    }

    @Override
    public boolean importData(TransferHandler.TransferSupport support) {
        try {
            String s = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
            JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
            strings.add(dl.getIndex(), s);
            beforeIndex = dl.getIndex() < index;
            return true;
        } catch (UnsupportedFlavorException ex0) {
            try {
                List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                mainFrame.addPdfFiles(files.toArray(new File[0]));
            } catch (UnsupportedFlavorException | IOException ex1) {
                java.util.logging.Logger.getLogger(MainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex1);
            }
        } catch (IOException ex2) {
            java.util.logging.Logger.getLogger(MainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex2);
        }

        return false;
    }
}

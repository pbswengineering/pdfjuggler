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
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
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
    private final MainFrame mainFrame;
    private final ListSelectionModel selection;

    /**
     * Create a new drag&drop transfer handler.
     *
     * @param dndList
     * @param strings
     * @param mainFrame
     */
    public DndTransferHandler(JList<String> dndList, DefaultListModel<String> strings, MainFrame mainFrame) {
        if (dndList.getSelectionMode() == ListSelectionModel.MULTIPLE_INTERVAL_SELECTION) {
            throw new IllegalArgumentException("Multiple interval selection is not supported, please use single or single interval selection.");
        }
        this.dndList = dndList;
        this.strings = strings;
        this.mainFrame = mainFrame;
        selection = dndList.getSelectionModel();
    }

    @Override
    public int getSourceActions(JComponent comp) {
        return MOVE;
    }

    @Override
    public Transferable createTransferable(JComponent comp) {
        selection.getSelectedIndices();
        return new StringSelection(dndList.getSelectedValue());
    }

    @Override
    public boolean canImport(TransferHandler.TransferSupport support) {
        return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || support.isDataFlavorSupported(DataFlavor.stringFlavor);
    }

    @Override
    public boolean importData(TransferHandler.TransferSupport support) {
        try {
            // Assume a MOVE operation if the flavor is string
            support.getTransferable().getTransferData(DataFlavor.stringFlavor);  // raise an exception if the flavor is not supported
            JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
            int[] selectedIndeces = selection.getSelectedIndices();
            int selectedIndecesLength = selectedIndeces.length;
            int dropLocationIndex = dl.getIndex();
            if (dropLocationIndex >= selectedIndeces[0] && dropLocationIndex <= selectedIndeces[selectedIndeces.length - 1]) {
                return false;
            }
            List<String> selectedValues = new ArrayList<>(selectedIndeces.length);
            for (int i : selectedIndeces) {
                selectedValues.add(strings.get(i));
            }
            strings.removeRange(selectedIndeces[0], selectedIndeces[selectedIndecesLength - 1]);
            if (dropLocationIndex > selectedIndeces[0]) {
                strings.addAll(dropLocationIndex - selectedIndecesLength, selectedValues);
                selection.setSelectionInterval(dropLocationIndex - selectedIndecesLength, dropLocationIndex - 1);
            } else {
                strings.addAll(dropLocationIndex, selectedValues);
                selection.setSelectionInterval(dropLocationIndex, dropLocationIndex + selectedIndecesLength - 1);
            }
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

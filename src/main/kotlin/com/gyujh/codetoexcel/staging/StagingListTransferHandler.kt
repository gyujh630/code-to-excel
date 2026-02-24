package com.gyujh.codetoexcel.staging

import com.intellij.ui.components.JBList
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import javax.swing.DefaultListModel
import javax.swing.DropMode
import javax.swing.TransferHandler

class StagingListTransferHandler(
    private val list: JBList<StagedItem>,
    private val onReorder: (List<StagedItem>) -> Unit
) : TransferHandler() {

    override fun getSourceActions(c: javax.swing.JComponent): Int = MOVE

    override fun createTransferable(c: javax.swing.JComponent): Transferable {
        val indices = list.selectedIndices.joinToString(",")
        return StringSelection(indices)
    }

    override fun canImport(support: TransferSupport): Boolean {
        if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) return false
        support.dropAction = MOVE
        return true
    }

    override fun importData(support: TransferSupport): Boolean {
        if (!canImport(support)) return false
        val data = support.transferable.getTransferData(DataFlavor.stringFlavor) as? String ?: return false
        val indices = data.split(",").mapNotNull { it.toIntOrNull() }.sorted()
        if (indices.isEmpty()) return false

        val dropLocation = support.dropLocation as? javax.swing.JList.DropLocation ?: return false
        val model = list.model as? DefaultListModel<StagedItem> ?: return false

        var insertIndex = dropLocation.index
        val movedItems = indices.map { model.getElementAt(it) }

        for (i in indices.asReversed()) {
            model.remove(i)
            if (i < insertIndex) insertIndex -= 1
        }

        if (insertIndex < 0) insertIndex = 0
        if (insertIndex > model.size()) insertIndex = model.size()

        for (item in movedItems) {
            model.add(insertIndex, item)
            insertIndex += 1
        }

        onReorder(model.toList())
        return true
    }

    private fun DefaultListModel<StagedItem>.toList(): List<StagedItem> =
        (0 until size()).map { getElementAt(it) }

    companion object {
        fun install(list: JBList<StagedItem>, onReorder: (List<StagedItem>) -> Unit) {
            list.dragEnabled = true
            list.dropMode = DropMode.INSERT
            list.transferHandler = StagingListTransferHandler(list, onReorder)
        }
    }
}

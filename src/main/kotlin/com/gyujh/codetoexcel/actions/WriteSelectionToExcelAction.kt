package com.gyujh.codetoexcel.actions

import com.gyujh.codetoexcel.editor.CodeSelectionService
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor

class WriteSelectionToExcelAction : AnAction("Code To Excel") {

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() == true

        e.presentation.isVisible = hasSelection
        e.presentation.isEnabled = hasSelection
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor: Editor = e.getRequiredData(CommonDataKeys.EDITOR)
        val project = e.project ?: return

        CodeSelectionService.processSelection(project, editor)
    }
}

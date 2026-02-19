package com.gyujh.codetoexcel.editor

import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.project.Project

class CodeSelectionListener(
    private val project: Project
) : EditorMouseListener {

    override fun mouseReleased(event: EditorMouseEvent) {
        val editor = event.editor
        val selectionModel = editor.selectionModel

        if (!selectionModel.hasSelection()) {
            SelectionFloatingButtonManager.removeExisting()
            return
        }

        println("Mouse released with selection")

        SelectionFloatingButtonManager.showButton(project, editor)
    }
}

package com.gyujh.codetoexcel.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.project.Project
import javax.swing.JButton

object SelectionFloatingButtonManager {

    private var currentInlay: Inlay<*>? = null

    fun showButton(project: Project, editor: Editor) {
        removeExisting()

        val offset = editor.selectionModel.selectionEnd
        val inlayModel = editor.inlayModel

        val button = JButton("📤").apply {
            isFocusable = false
            isBorderPainted = false
            isContentAreaFilled = false

            addActionListener {
                CodeSelectionService.processSelection(project, editor)
            }
        }

        currentInlay = inlayModel.addInlineElement(
            offset,
            ButtonRenderer(button)
        )
    }

    fun removeExisting() {
        currentInlay?.dispose()
        currentInlay = null
    }
}

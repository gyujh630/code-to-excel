package com.gyujh.codetoexcel.editor

import com.gyujh.codetoexcel.excel.component.CodeImageExcelComponent
import com.gyujh.codetoexcel.excel.ExcelWriter
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project

object CodeSelectionService {

    fun processSelection(project: Project, editor: Editor) {

        val metadata = extractSelectionMetadata(project, editor) ?: return

        val image = CodeTextImageRenderer.render(
            project = project,
            code = metadata.code,
            virtualFile = metadata.virtualFile,
            firstLineNumber = metadata.startLine,
            options = RenderOptions(
                minCodeAreaWidthPx = 480
            )
        )

        val lineCount = (metadata.endLine - metadata.startLine + 1).coerceAtLeast(1)
        val component = CodeImageExcelComponent(
            title = metadata.fileName,
            sourceImage = image,
            lineCount = lineCount
        )
        val result = ExcelWriter().insertComponent(component)
        val notificationType = if (result.success) NotificationType.INFORMATION else NotificationType.ERROR

        NotificationGroupManager.getInstance()
            .getNotificationGroup("CodeToExcelNotification")
            .createNotification(
                "Code To Excel",
                result.message,
                notificationType
            )
            .notify(project)
    }

    private fun extractSelectionMetadata(
        project: Project,
        editor: Editor
    ): SelectionMetadata? {

        val selectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) return null

        val document = editor.document

        val startOffset = selectionModel.selectionStart
        val endOffset = selectionModel.selectionEnd

        val startLine = document.getLineNumber(startOffset) + 1
        val endLine = document.getLineNumber(endOffset) + 1

        val selectedText = selectionModel.selectedText ?: return null

        val virtualFile = FileDocumentManager
            .getInstance()
            .getFile(document) ?: return null

        val basePath = project.basePath ?: return null
        val relativePath = virtualFile.path.removePrefix("$basePath/").removePrefix(basePath)

        return SelectionMetadata(
            fileName = relativePath,
            startLine = startLine,
            endLine = endLine,
            code = selectedText,
            virtualFile = virtualFile
        )
    }
}

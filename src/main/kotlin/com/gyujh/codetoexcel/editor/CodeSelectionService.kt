package com.gyujh.codetoexcel.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import javax.imageio.ImageIO

object CodeSelectionService {

    fun processSelection(project: Project, editor: Editor) {

        val metadata = extractSelectionMetadata(project, editor) ?: return

        val image = CodeTextImageRenderer.render(
            project = project,
            code = metadata.code,
            virtualFile = metadata.virtualFile,
            firstLineNumber = metadata.startLine
        )
        val basePath = project.basePath ?: return
        val imageDir = Paths.get(basePath, ".codetoexcel")

        Files.createDirectories(imageDir)

        val fileName = "${UUID.randomUUID()}.png"
        val outputPath = imageDir.resolve(fileName)

        ImageIO.write(image, "png", outputPath.toFile())

        println("Image saved to: $outputPath")
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

        val relativePath = virtualFile.name

        return SelectionMetadata(
            fileName = relativePath,
            startLine = startLine,
            endLine = endLine,
            code = selectedText,
            virtualFile = virtualFile   // 🔥 추가
        )
    }
}
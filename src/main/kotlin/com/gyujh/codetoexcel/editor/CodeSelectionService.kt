package com.gyujh.codetoexcel.editor

import com.gyujh.codetoexcel.staging.StagingService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

object CodeSelectionService {
    private val log = Logger.getInstance(CodeSelectionService::class.java)

    fun processSelection(project: Project, editor: Editor) {

        val metadata = extractSelectionMetadata(project, editor) ?: return

        val image = CodeTextImageRenderer.render(
            project = project,
            code = metadata.code,
            virtualFile = metadata.virtualFile,
            firstLineNumber = metadata.startLine,
            options = RenderOptions(
                minCodeAreaWidthPx = 480,
                supersampleScale = 3
            )
        )
        saveRenderedImageForDebug(project, metadata, image)
        StagingService().saveSelection(project, metadata, image)
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

    private fun saveRenderedImageForDebug(
        project: Project,
        metadata: SelectionMetadata,
        image: java.awt.image.BufferedImage
    ) {
        val basePath = project.basePath ?: return
        val debugDir = Path.of(basePath, ".codetoexcel-debug", "code-images")
        val safeFileName = metadata.fileName
            .replace("\\", "_")
            .replace("/", "_")
            .replace(":", "_")
        val outFile = debugDir.resolve(
            "${System.currentTimeMillis()}_${safeFileName}_${metadata.startLine}-${metadata.endLine}.png"
        )

        runCatching {
            Files.createDirectories(debugDir)
            ImageIO.write(image, "png", outFile.toFile())
        }.onFailure {
            log.warn("Failed to save debug image: ${outFile.toAbsolutePath()}", it)
        }
    }
}

package com.gyujh.codetoexcel.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object CodeSelectionService {

    fun processSelection(project: Project, editor: Editor) {

        val metadata = extractSelectionMetadata(editor) ?: return

        logSelection(metadata)

        // 🔥 확장 포인트
        // CodeImageRenderer.render(metadata)
        // ExcelWriter.writeCode(metadata)
        // RowManager.increment()
    }

    /**
     * Selection 정보를 하나의 객체로 추출
     * 추후 이미지 변환 / Excel 기록에서 재사용
     */
    private fun extractSelectionMetadata(editor: Editor): SelectionMetadata? {

        val selectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) return null

        val document = editor.document

        val startOffset = selectionModel.selectionStart
        val endOffset = selectionModel.selectionEnd

        val startLine = document.getLineNumber(startOffset) + 1
        val endLine = document.getLineNumber(endOffset) + 1

        val selectedText = selectionModel.selectedText ?: return null

        val virtualFile: VirtualFile? =
            FileDocumentManager.getInstance().getFile(document)

        val fileName = virtualFile?.name ?: "Unknown"

        return SelectionMetadata(
            fileName = fileName,
            startLine = startLine,
            endLine = endLine,
            code = selectedText
        )
    }

    /**
     * 현재는 로그 출력만 수행
     * 추후 Logger로 교체 가능
     */
    private fun logSelection(metadata: SelectionMetadata) {

        println("===== Code Selection =====")
        println("File: ${metadata.fileName}")
        println("Start Line: ${metadata.startLine}")
        println("End Line: ${metadata.endLine}")
        println("Selected Code:")
        println(metadata.code)
    }
}

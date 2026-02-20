package com.gyujh.codetoexcel.editor

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.max

data class RenderOptions(
    val paddingX: Int = 16,
    val paddingY: Int = 14,
    val lineNumberPaddingRight: Int = 12,
    val showLineNumbers: Boolean = true,
    val maxWidthPx: Int = 900,   // wrap 기준 폭(최대 폭)
    val fontSize: Int? = null    // null이면 IDE 폰트 크기 사용
)

object CodeTextImageRenderer {

    fun render(
        project: Project,
        code: String,
        virtualFile: VirtualFile,
        firstLineNumber: Int,
        options: RenderOptions = RenderOptions()
    ): BufferedImage {

        val scheme = EditorColorsManager.getInstance().globalScheme

        val syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(
            virtualFile.fileType,
            project,
            virtualFile
        ) ?: error("No SyntaxHighlighter for ${virtualFile.fileType}")

        val baseFont = schemeToFont(scheme, options)
        val fm = fontMetricsFor(baseFont)

        val lineCount = max(1, code.count { it == '\n' } + 1)

        val lineNumberAreaWidth = if (options.showLineNumbers) {
            val maxLineNo = firstLineNumber + lineCount - 1
            val digits = maxLineNo.toString().length
            fm.stringWidth("9".repeat(digits)) + options.lineNumberPaddingRight + 8
        } else 0

        val availableTextWidth =
            (options.maxWidthPx - options.paddingX * 2 - lineNumberAreaWidth).coerceAtLeast(100)

        val spans = tokenizeToSpans(code, syntaxHighlighter, scheme, baseFont)

        val layout = LayoutEngine.layout(
            spans = spans,
            fm = fm,
            maxTextWidth = availableTextWidth,
            startX = options.paddingX + lineNumberAreaWidth,
            startY = options.paddingY,
            lineHeight = fm.height
        )

        // ✅ 우측 공백 제거: "실제 그려진 최대 x"까지만 사용 (padding 포함)
        val imgW = (layout.maxX + options.paddingX).coerceAtLeast(100)
        val imgH = (layout.maxY + options.paddingY).coerceAtLeast(fm.height + options.paddingY * 2)

        val image = UIUtil.createImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            prepareGraphics(g)

            g.color = scheme.defaultBackground
            g.fillRect(0, 0, imgW, imgH)

            if (options.showLineNumbers) {
                drawLineNumbers(
                    g = g,
                    scheme = scheme,
                    fm = fm,
                    lineNumberAreaWidth = lineNumberAreaWidth,
                    baselineOffset = fm.ascent,
                    lineYs = layout.logicalLineYStarts,
                    firstLineNumber = firstLineNumber,
                    paddingX = options.paddingX
                )
            }

            layout.draw(g)
        } finally {
            g.dispose()
        }

        return image
    }

    // --------- Tokenization ---------

    private fun tokenizeToSpans(
        code: String,
        sh: SyntaxHighlighter,
        scheme: EditorColorsScheme,
        baseFont: Font
    ): List<RenderSpan> {
        val lexer = sh.highlightingLexer
        lexer.start(code)

        val spans = ArrayList<RenderSpan>(1024)
        while (lexer.tokenType != null) {
            val start = lexer.tokenStart
            val end = lexer.tokenEnd
            val text = code.substring(start, end)

            val keys = sh.getTokenHighlights(lexer.tokenType!!)
            val attr = resolveAttributes(scheme, keys) ?: TextAttributes().apply {
                foregroundColor = scheme.defaultForeground
            }

            spans.add(RenderSpan(text, attr, baseFont))
            lexer.advance()
        }
        return spans
    }

    private fun resolveAttributes(
        scheme: EditorColorsScheme,
        keys: Array<com.intellij.openapi.editor.colors.TextAttributesKey>
    ): TextAttributes? {
        var merged: TextAttributes? = null
        for (k in keys) {
            val a = scheme.getAttributes(k) ?: continue
            if (merged == null) merged = a.clone() else mergeAttributes(dst = merged!!, src = a)
        }
        return merged
    }

    private fun mergeAttributes(dst: TextAttributes, src: TextAttributes) {
        if (dst.foregroundColor == null) dst.foregroundColor = src.foregroundColor
        if (dst.backgroundColor == null) dst.backgroundColor = src.backgroundColor
        if (dst.effectColor == null) dst.effectColor = src.effectColor
        if (dst.effectType == null) dst.effectType = src.effectType
        if (dst.errorStripeColor == null) dst.errorStripeColor = src.errorStripeColor
        dst.fontType = dst.fontType or src.fontType
    }

    // --------- Font / Graphics helpers ---------

    private fun schemeToFont(scheme: EditorColorsScheme, options: RenderOptions): Font {
        val family = scheme.editorFontName
        val size = options.fontSize ?: scheme.editorFontSize
        return Font(family, Font.PLAIN, size)
    }

    private fun fontMetricsFor(font: Font): FontMetrics {
        val tmp = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val g = tmp.createGraphics()
        try {
            return g.getFontMetrics(font)
        } finally {
            g.dispose()
        }
    }

    private fun prepareGraphics(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    }

    // --------- Line numbers ---------

    private fun drawLineNumbers(
        g: Graphics2D,
        scheme: EditorColorsScheme,
        fm: FontMetrics,
        lineNumberAreaWidth: Int,
        baselineOffset: Int,
        lineYs: List<Int>,
        firstLineNumber: Int,
        paddingX: Int
    ) {
        val fg = scheme.defaultForeground
        val lnColor = java.awt.Color(fg.red, fg.green, fg.blue, 140)

        g.font = fm.font
        g.color = lnColor

        for (i in lineYs.indices) {
            val lineNo = firstLineNumber + i
            val s = lineNo.toString()
            val textW = fm.stringWidth(s)
            val x = paddingX + lineNumberAreaWidth - textW - 8
            val y = lineYs[i] + baselineOffset
            g.drawString(s, x, y)
        }
    }

    // --------- Layout Engine (MVP, with trailing-space trim) ---------

    private data class RenderSpan(
        val text: String,
        val attr: TextAttributes,
        val font: Font
    )

    private data class DrawChunk(
        val text: String,
        val x: Int,
        val y: Int,
        val color: java.awt.Color,
        val font: Font
    )

    private class Layout(
        private val chunks: MutableList<DrawChunk>,
        val logicalLineYStarts: List<Int>,
        val maxX: Int,
        val maxY: Int
    ) {
        fun draw(g: Graphics2D) {
            for (c in chunks) {
                g.font = c.font
                g.color = c.color
                g.drawString(c.text, c.x, c.y)
            }
        }
    }

    private object LayoutEngine {

        fun layout(
            spans: List<RenderSpan>,
            fm: FontMetrics,
            maxTextWidth: Int,
            startX: Int,
            startY: Int,
            lineHeight: Int
        ): Layout {

            var spanIdx = 0
            var spanPos = 0

            var x = startX
            var y = startY

            val logicalLineYStarts = ArrayList<Int>()
            logicalLineYStarts.add(y)

            val chunks = ArrayList<DrawChunk>(2048)

            var maxXSeen = startX
            var maxYSeen = startY + lineHeight

            var lineStartChunkIndex = 0 // ✅ 현재 라인의 chunk 시작 인덱스

            fun trimLineEnd() {
                x = trimLineEndSpaces(chunks, fm, x, lineStartChunkIndex)
                maxXSeen = max(maxXSeen, x)
            }

            fun newline() {
                // ✅ 줄바꿈 전에 trailing 공백 제거
                trimLineEnd()

                x = startX
                y += lineHeight
                logicalLineYStarts.add(y)
                lineStartChunkIndex = chunks.size // 다음 라인 시작점 업데이트
                maxYSeen = max(maxYSeen, y + lineHeight)
            }

            fun emit(text: String, color: java.awt.Color, font: Font) {
                if (text.isEmpty()) return
                val drawY = y + fm.ascent
                chunks.add(DrawChunk(text, x, drawY, color, font))
                x += fm.stringWidth(text)
                maxXSeen = max(maxXSeen, x)
            }

            fun currentSpan(): RenderSpan? = if (spanIdx in spans.indices) spans[spanIdx] else null

            fun wrapIfNeeded(nextTokenText: String): Boolean {
                if (nextTokenText.isEmpty()) return false
                val tokenW = fm.stringWidth(nextTokenText)
                val curW = x - startX
                if (curW + tokenW <= maxTextWidth) return false
                if (curW > 0) {
                    newline()
                    return true
                }
                return false
            }

            while (true) {
                val span = currentSpan() ?: break
                val remaining = span.text.substring(spanPos)

                if (remaining.isEmpty()) {
                    spanIdx += 1
                    spanPos = 0
                    continue
                }

                val nl = remaining.indexOf('\n')
                val piece = if (nl >= 0) remaining.substring(0, nl) else remaining

                wrapIfNeeded(piece)

                val fg = span.attr.foregroundColor ?: EditorColorsManager.getInstance().globalScheme.defaultForeground
                val color = fg

                var p = piece
                while (p.isNotEmpty()) {
                    val curW = x - startX
                    val available = maxTextWidth - curW
                    if (available <= 0) {
                        newline()
                        continue
                    }

                    val w = fm.stringWidth(p)
                    if (w <= available) {
                        emit(p, color, span.font)
                        p = ""
                    } else {
                        val cut = maxCharsThatFit(fm, p, available)
                        val head = p.substring(0, cut)
                        emit(head, color, span.font)
                        p = p.substring(cut)
                        newline()
                    }
                }

                if (nl >= 0) {
                    spanPos += piece.length + 1
                    newline()
                } else {
                    spanPos += piece.length
                }
            }

            // ✅ 마지막 라인도 trailing 공백 제거
            trimLineEnd()

            return Layout(
                chunks = chunks,
                logicalLineYStarts = logicalLineYStarts,
                maxX = maxXSeen,
                maxY = maxYSeen
            )
        }

        private fun trimLineEndSpaces(
            chunks: MutableList<DrawChunk>,
            fm: FontMetrics,
            currentX: Int,
            lineStartChunkIndex: Int
        ): Int {
            var x = currentX
            var i = chunks.size - 1

            while (i >= lineStartChunkIndex) {
                val c = chunks[i]
                val t = c.text

                val trimmed = t.trimEnd(' ', '\t')
                if (trimmed.length == t.length) break // trailing 공백 없음

                val removed = t.substring(trimmed.length)
                val removedW = fm.stringWidth(removed)
                x -= removedW

                if (trimmed.isEmpty()) chunks.removeAt(i) else chunks[i] = c.copy(text = trimmed)
                break
            }

            return x
        }

        private fun maxCharsThatFit(fm: FontMetrics, s: String, maxWidth: Int): Int {
            var w = 0
            var i = 0
            while (i < s.length) {
                val cw = fm.charWidth(s[i])
                if (w + cw > maxWidth) break
                w += cw
                i++
            }
            return max(1, i)
        }
    }
}
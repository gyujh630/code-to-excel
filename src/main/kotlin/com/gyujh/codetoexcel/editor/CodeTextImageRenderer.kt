package com.gyujh.codetoexcel.editor

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import java.awt.Color
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
    val maxWidthPx: Int = 900,          // wrap 최대 폭(코드 텍스트 영역 기준)
    val fontSize: Int? = null,          // null이면 IDE 폰트 크기 사용
    val tabSize: Int = 4,               // 탭 폭(공백 몇 칸)

    // Header (text only)
    val showHeader: Boolean = true,
    val headerGap: Int = 22,            // 헤더-코드 간격
    val headerTitle: String? = null,    // Service에서 상대경로 넘길 것

    // Layout polish
    val minCodeAreaWidthPx: Int = 520   // 코드가 짧아도 보기 좋은 최소 폭(텍스트 영역 기준)
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

        // Header text (no line range)
        val rawHeaderText = options.headerTitle?.takeIf { it.isNotBlank() } ?: virtualFile.name
        val headerHeight = if (options.showHeader) fm.height else 0

        // line-number digits based on real lines only
        val realLineCount = max(1, code.count { it == '\n' } + 1)
        val endLineNumber = firstLineNumber + realLineCount - 1

        val lineNumberAreaWidth = if (options.showLineNumbers) {
            val digits = endLineNumber.toString().length
            fm.stringWidth("9".repeat(digits)) + options.lineNumberPaddingRight + 8
        } else 0

        val availableTextWidth =
            (options.maxWidthPx - options.paddingX * 2 - lineNumberAreaWidth).coerceAtLeast(160)

        val spans = tokenizeToSpans(code, syntaxHighlighter, scheme, baseFont)

        val startX = options.paddingX + lineNumberAreaWidth
        val startY = options.paddingY + (if (options.showHeader) headerHeight + options.headerGap else 0)

        val layout = LayoutEngine.layout(
            spans = spans,
            fm = fm,
            maxTextWidth = availableTextWidth,
            startX = startX,
            startY = startY,
            lineHeight = fm.height,
            tabSize = options.tabSize.coerceAtLeast(1),
            firstLineNumber = firstLineNumber
        )

        // ✅ 폭 결정 규칙:
        // - 헤더 길이로 폭을 늘리지 않는다
        // - 코드가 짧으면 minCodeAreaWidthPx 만큼 확보
        // - 절대 maxWidthPx(텍스트영역 기준)를 넘겨 커지지 않게 상한 적용
        val minCodeTotalW = options.paddingX * 2 + lineNumberAreaWidth + options.minCodeAreaWidthPx
        val codeTotalW = layout.maxX + options.paddingX

        val maxImageW = options.paddingX * 2 + lineNumberAreaWidth + options.maxWidthPx
        val rawImgW = max(max(codeTotalW, minCodeTotalW), 200)
        val imgW = rawImgW.coerceAtMost(maxImageW)

        val imgH = max(
            layout.maxY + options.paddingY,
            options.paddingY * 2 + headerHeight + fm.height
        )

        // ✅ leading ellipsis: 헤더는 "고정된 imgW"에 맞춰 자른다
        val headerText = if (options.showHeader) {
            val maxHeaderTextWidth = (imgW - options.paddingX * 2).coerceAtLeast(40)
            ellipsizeLeading(rawHeaderText, fm, maxHeaderTextWidth)
        } else rawHeaderText

        val image = UIUtil.createImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            prepareGraphics(g)

            g.color = scheme.defaultBackground
            g.fillRect(0, 0, imgW, imgH)

            // Header text only
            if (options.showHeader) {
                drawHeaderTextOnly(
                    g = g,
                    baseFont = baseFont,
                    text = headerText,
                    x = options.paddingX,
                    y = options.paddingY
                )
            }

            // Line numbers (no numbers for wrapped visual lines)
            if (options.showLineNumbers) {
                drawLineNumbers(
                    g = g,
                    scheme = scheme,
                    fm = fm,
                    lineNumberAreaWidth = lineNumberAreaWidth,
                    baselineOffset = fm.ascent,
                    visualLines = layout.visualLines,
                    paddingX = options.paddingX
                )
            }

            layout.draw(g)
        } finally {
            g.dispose()
        }

        return image
    }

    // ---------- Leading ellipsis helper (…/abc/a.java) ----------

    private fun ellipsizeLeading(text: String, fm: FontMetrics, maxWidth: Int): String {
        if (fm.stringWidth(text) <= maxWidth) return text

        val ell = "…"
        val ellW = fm.stringWidth(ell)
        if (ellW >= maxWidth) return ell

        val budget = maxWidth - ellW

        var lo = 0
        var hi = text.length
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            val tail = text.substring(text.length - mid)
            if (fm.stringWidth(tail) <= budget) lo = mid else hi = mid - 1
        }

        val tail = text.substring(text.length - lo)
        return ell + tail
    }

    // ---------- Tokenization ----------

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
            val text = code.substring(lexer.tokenStart, lexer.tokenEnd)

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

    // ---------- Font / Graphics helpers ----------

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

    // ---------- Header (text only) ----------

    private fun drawHeaderTextOnly(
        g: Graphics2D,
        baseFont: Font,
        text: String,
        x: Int,
        y: Int
    ) {
        val headerFont = baseFont.deriveFont(Font.BOLD, baseFont.size2D)
        val headerFm = g.getFontMetrics(headerFont)

        val headerColor = Color(255, 248, 200)

        g.font = headerFont
        g.color = headerColor

        val textY = y + headerFm.ascent
        g.drawString(text, x, textY)
    }

    // ---------- Line numbers (wrap 줄은 표시하지 않음) ----------

    private fun drawLineNumbers(
        g: Graphics2D,
        scheme: EditorColorsScheme,
        fm: FontMetrics,
        lineNumberAreaWidth: Int,
        baselineOffset: Int,
        visualLines: List<VisualLine>,
        paddingX: Int
    ) {
        val fg = scheme.defaultForeground
        val lnColor = Color(fg.red, fg.green, fg.blue, 140)

        g.font = fm.font
        g.color = lnColor

        for (vl in visualLines) {
            val lineNo = vl.lineNumber ?: continue
            val s = lineNo.toString()
            val textW = fm.stringWidth(s)

            val xx = paddingX + lineNumberAreaWidth - textW - 8
            val yy = vl.y + baselineOffset
            g.drawString(s, xx, yy)
        }
    }

    // ---------- Layout Engine (tab stop + word wrap + trailing trim; lineNo only on real lines) ----------

    private data class RenderSpan(
        val text: String,
        val attr: TextAttributes,
        val font: Font
    )

    private data class DrawChunk(
        val text: String,
        val x: Int,
        val y: Int,
        val color: Color,
        val font: Font
    )

    private data class VisualLine(
        val y: Int,
        val lineNumber: Int?
    )

    private class Layout(
        private val chunks: MutableList<DrawChunk>,
        val visualLines: List<VisualLine>,
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
            lineHeight: Int,
            tabSize: Int,
            firstLineNumber: Int
        ): Layout {

            var spanIdx = 0
            var spanPos = 0

            var x = startX
            var y = startY

            val chunks = ArrayList<DrawChunk>(2048)

            var maxXSeen = startX
            var maxYSeen = startY + lineHeight

            var lineStartChunkIndex = 0

            val spaceW = max(1, fm.charWidth(' '))
            val tabStopPx = tabSize * spaceW

            var currentRealLineNo = firstLineNumber

            val visualLines = ArrayList<VisualLine>()
            visualLines.add(VisualLine(y = y, lineNumber = currentRealLineNo))

            fun trimLineEnd() {
                x = trimLineEndSpaces(chunks, fm, x, lineStartChunkIndex)
                maxXSeen = max(maxXSeen, x)
            }

            fun newVisualLineFromWrap() {
                trimLineEnd()
                x = startX
                y += lineHeight
                visualLines.add(VisualLine(y = y, lineNumber = null))
                lineStartChunkIndex = chunks.size
                maxYSeen = max(maxYSeen, y + lineHeight)
            }

            fun newVisualLineFromRealNewline() {
                trimLineEnd()
                x = startX
                y += lineHeight
                currentRealLineNo += 1
                visualLines.add(VisualLine(y = y, lineNumber = currentRealLineNo))
                lineStartChunkIndex = chunks.size
                maxYSeen = max(maxYSeen, y + lineHeight)
            }

            fun emit(text: String, color: Color, font: Font) {
                if (text.isEmpty()) return
                val drawY = y + fm.ascent
                chunks.add(DrawChunk(text, x, drawY, color, font))
                x += fm.stringWidth(text)
                maxXSeen = max(maxXSeen, x)
            }

            fun advanceTab() {
                val cur = x - startX
                val next = ((cur / tabStopPx) + 1) * tabStopPx
                x = startX + next
                maxXSeen = max(maxXSeen, x)
            }

            fun currentSpan(): RenderSpan? = if (spanIdx in spans.indices) spans[spanIdx] else null

            fun measureWithTabs(s: String): Int {
                var xx = x
                for (ch in s) {
                    if (ch == '\t') {
                        val cur = xx - startX
                        val next = ((cur / tabStopPx) + 1) * tabStopPx
                        xx = startX + next
                    } else {
                        xx += fm.charWidth(ch)
                    }
                }
                return xx - x
            }

            fun emitWithTabs(text: String, color: Color, font: Font) {
                var buf = StringBuilder()
                for (ch in text) {
                    when (ch) {
                        '\t' -> {
                            if (buf.isNotEmpty()) {
                                emit(buf.toString(), color, font)
                                buf = StringBuilder()
                            }
                            advanceTab()
                        }
                        else -> buf.append(ch)
                    }
                }
                if (buf.isNotEmpty()) emit(buf.toString(), color, font)
            }

            fun maxCharsThatFitWithTabs(s: String, maxWidth: Int): Int {
                var xx = x
                var i = 0
                while (i < s.length) {
                    val ch = s[i]
                    val nextX = if (ch == '\t') {
                        val cur = xx - startX
                        val next = ((cur / tabStopPx) + 1) * tabStopPx
                        startX + next
                    } else {
                        xx + fm.charWidth(ch)
                    }
                    if (nextX - x > maxWidth) break
                    xx = nextX
                    i++
                }
                return max(1, i)
            }

            fun wordWrapEmit(text: String, color: Color, font: Font) {
                var s = text
                while (s.isNotEmpty()) {
                    val available = maxTextWidth - (x - startX)
                    if (available <= 0) {
                        newVisualLineFromWrap()
                        continue
                    }

                    val w = measureWithTabs(s)
                    if (w <= available) {
                        emitWithTabs(s, color, font)
                        return
                    }

                    val cut = maxCharsThatFitWithTabs(s, available)
                    val candidate = s.substring(0, cut)

                    val lastSpace = candidate.lastIndexOf(' ')
                    val lastTab = candidate.lastIndexOf('\t')
                    val breakPos = max(lastSpace, lastTab)

                    if (breakPos >= 0) {
                        val head = s.substring(0, breakPos).trimEnd(' ', '\t')
                        val tail = s.substring(breakPos + 1)
                        if (head.isNotEmpty()) emitWithTabs(head, color, font)
                        newVisualLineFromWrap()
                        s = tail
                    } else {
                        val head = s.substring(0, cut)
                        emitWithTabs(head, color, font)
                        newVisualLineFromWrap()
                        s = s.substring(cut)
                    }
                }
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

                val fg = span.attr.foregroundColor ?: EditorColorsManager.getInstance().globalScheme.defaultForeground
                val color = Color(fg.red, fg.green, fg.blue, 255)

                wordWrapEmit(piece, color, span.font)

                if (nl >= 0) {
                    spanPos += piece.length + 1
                    newVisualLineFromRealNewline()
                } else {
                    spanPos += piece.length
                }
            }

            trimLineEnd()

            return Layout(
                chunks = chunks,
                visualLines = visualLines,
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
                if (trimmed.length == t.length) break
                val removed = t.substring(trimmed.length)
                x -= fm.stringWidth(removed)
                if (trimmed.isEmpty()) chunks.removeAt(i) else chunks[i] = c.copy(text = trimmed)
                break
            }
            return x
        }
    }
}
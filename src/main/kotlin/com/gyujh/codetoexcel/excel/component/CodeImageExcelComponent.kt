package com.gyujh.codetoexcel.excel.component

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CodeImageExcelComponent(
    private val title: String,
    private val sourceImage: BufferedImage,
    private val lineCount: Int
) : ExcelInsertComponent {

    override fun render(maxHeightPx: Int): RenderedExcelComponent {
        val normalizedLineCount = lineCount.coerceAtLeast(1)
        val lineBasedBodyHeight =
            (normalizedLineCount * BODY_LINE_HEIGHT_PX + BODY_BASE_PADDING_PX).coerceAtMost(MAX_BODY_HEIGHT_PX)

        val availableBodyHeight = (maxHeightPx - HEADER_HEIGHT_PX - HEADER_TO_IMAGE_GAP_PX).coerceAtLeast(1)
        val targetBodyHeight = min(sourceImage.height, min(lineBasedBodyHeight, availableBodyHeight))

        val scale = targetBodyHeight.toDouble() / sourceImage.height.toDouble()
        val bodyWidth = max(1, (sourceImage.width * scale).roundToInt())
        val bodyHeight = max(1, (sourceImage.height * scale).roundToInt())

        val width = max(bodyWidth, MIN_COMPONENT_WIDTH_PX)
        val height = HEADER_HEIGHT_PX + HEADER_TO_IMAGE_GAP_PX + bodyHeight

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

            g.color = HEADER_BG_COLOR
            g.fillRect(0, 0, width, HEADER_HEIGHT_PX)
            g.color = HEADER_BORDER_COLOR
            g.stroke = BasicStroke(1f)
            g.drawRect(0, 0, width - 1, HEADER_HEIGHT_PX - 1)

            g.font = HEADER_FONT
            g.color = HEADER_TEXT_COLOR
            val fm = g.fontMetrics
            val titleForWidth = ellipsizeLeading(title, fm, (width - HEADER_TEXT_PADDING_X * 2).coerceAtLeast(20))
            val textY = (HEADER_HEIGHT_PX - fm.height) / 2 + fm.ascent
            g.drawString(titleForWidth, HEADER_TEXT_PADDING_X, textY)

            g.drawImage(sourceImage, 0, HEADER_HEIGHT_PX + HEADER_TO_IMAGE_GAP_PX, bodyWidth, bodyHeight, null)
        } finally {
            g.dispose()
        }

        return RenderedExcelComponent(
            image = image,
            bounds = GroupBoundsPx(width, height)
        )
    }

    private fun ellipsizeLeading(text: String, fm: FontMetrics, maxWidth: Int): String {
        if (fm.stringWidth(text) <= maxWidth) return text

        val ellipsis = "..."
        val ellipsisWidth = fm.stringWidth(ellipsis)
        if (ellipsisWidth >= maxWidth) return ellipsis

        val budget = maxWidth - ellipsisWidth
        var lo = 0
        var hi = text.length

        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            val tail = text.takeLast(mid)
            if (fm.stringWidth(tail) <= budget) lo = mid else hi = mid - 1
        }

        return ellipsis + text.takeLast(lo)
    }

    companion object {
        private const val HEADER_HEIGHT_PX = 28
        private const val HEADER_TO_IMAGE_GAP_PX = 6
        private const val HEADER_TEXT_PADDING_X = 10
        private const val MIN_COMPONENT_WIDTH_PX = 220

        private const val BODY_LINE_HEIGHT_PX = 18
        private const val BODY_BASE_PADDING_PX = 16
        private const val MAX_BODY_HEIGHT_PX = 220

        private val HEADER_FONT = Font("Dialog", Font.BOLD, 12)
        private val HEADER_BG_COLOR = Color(255, 242, 166)
        private val HEADER_BORDER_COLOR = Color(220, 188, 90)
        private val HEADER_TEXT_COLOR = Color(68, 56, 20)
    }
}

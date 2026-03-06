package com.gyujh.codetoexcel.excel.component

import java.awt.image.BufferedImage
import kotlin.math.max

class CodeImageExcelComponent(
    private val title: String,
    private val sourceImage: BufferedImage,
    private val lineCount: Int,
    private val renderScale: Int = 1
) : ExcelInsertComponent {

    override fun render(maxHeightPx: Int): RenderedExcelComponent {
        // Do not fit to row height; keep original size (no scaling).
        val bodyWidth = max(1, sourceImage.width)
        val bodyHeight = max(1, sourceImage.height)

        val width = max(bodyWidth, MIN_COMPONENT_WIDTH_PX)
        val image = if (width == bodyWidth) {
            sourceImage
        } else {
            val padded = BufferedImage(width, bodyHeight, BufferedImage.TYPE_INT_ARGB)
            val g = padded.createGraphics()
            try {
                g.drawImage(sourceImage, 0, 0, null)
            } finally {
                g.dispose()
            }
            padded
        }

        return RenderedExcelComponent(
            image = image,
            bounds = GroupBoundsPx(width, bodyHeight),
            dpi = 96 * renderScale.coerceAtLeast(1)
        )
    }

    companion object {
        private const val MIN_COMPONENT_WIDTH_PX = 220
    }
}

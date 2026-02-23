package com.gyujh.codetoexcel.excel.component

import java.awt.image.BufferedImage

interface ExcelInsertComponent {
    fun render(maxHeightPx: Int): RenderedExcelComponent
}

data class RenderedExcelComponent(
    val image: BufferedImage,
    val bounds: GroupBoundsPx
)

data class GroupBoundsPx(
    val width: Int,
    val height: Int
)

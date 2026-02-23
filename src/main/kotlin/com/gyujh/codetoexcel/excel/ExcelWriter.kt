package com.gyujh.codetoexcel.excel

import com.gyujh.codetoexcel.settings.ExcelSettingsState
import org.apache.poi.ss.usermodel.ClientAnchor
import org.apache.poi.util.Units
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFDrawing
import org.apache.poi.xssf.usermodel.XSSFPicture
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ExcelWriter {

    fun insertImage(image: BufferedImage, lineCount: Int): ImageInsertResult {
        val settings = ExcelSettingsState.getInstance()
        val excelPath = settings.excelPath.trim()
        val baseSheet = settings.baseSheet.trim()
        val baseColumn = settings.baseColumn.trim().uppercase()
        val baseRow = settings.baseRow

        if (excelPath.isEmpty()) {
            return ImageInsertResult(false, "엑셀 파일이 선택되지 않았습니다.")
        }
        if (baseSheet.isEmpty()) {
            return ImageInsertResult(false, "Base Sheet가 선택되지 않았습니다.")
        }
        if (baseColumn.isEmpty()) {
            return ImageInsertResult(false, "Base Column이 선택되지 않았습니다.")
        }
        if (!baseColumn.matches(Regex("^[A-Z]+$"))) {
            return ImageInsertResult(false, "Base Column 값이 유효하지 않습니다: $baseColumn")
        }
        if (baseRow < 1) {
            return ImageInsertResult(false, "Base Row는 1 이상이어야 합니다.")
        }

        val file = File(excelPath)
        if (!file.exists()) {
            return ImageInsertResult(false, "선택된 엑셀 파일이 존재하지 않습니다.")
        }

        return runCatching {
            FileInputStream(file).use { fis ->
                XSSFWorkbook(fis).use { workbook ->
                    val sheet = workbook.getSheet(baseSheet)
                        ?: return ImageInsertResult(false, "시트를 찾을 수 없습니다: $baseSheet")

                    val targetColumnIndex = columnToIndex(baseColumn)
                    val targetRowIndex = baseRow - 1

                    val pngBytes = ByteArrayOutputStream().use { bos ->
                        ImageIO.write(image, "png", bos)
                        bos.toByteArray()
                    }

                    val pictureIndex = workbook.addPicture(pngBytes, XSSFWorkbook.PICTURE_TYPE_PNG)
                    val drawing = sheet.createDrawingPatriarch() as XSSFDrawing

                    val cellLeftXEmu = absoluteXOfCellEmu(sheet, targetColumnIndex)
                    val cellTopYEmu = absoluteYOfRowEmu(sheet, targetRowIndex)
                    val cellHeightEmu = rowHeightEmu(sheet, targetRowIndex)
                    val cellBottomYEmu = cellTopYEmu + cellHeightEmu

                    val laneMaxRightXEmu = findLaneMaxRightXEmu(
                        sheet = sheet,
                        drawing = drawing,
                        laneStartXEmu = cellLeftXEmu,
                        laneTopYEmu = cellTopYEmu,
                        laneBottomYEmu = cellBottomYEmu
                    )

                    val normalizedLineCount = lineCount.coerceAtLeast(1)
                    val lineBasedHeightPx = normalizedLineCount * LINE_HEIGHT_PX + LINE_BASE_PADDING_PX
                    val lineBasedHeightEmu = Units.pixelToEMU(
                        lineBasedHeightPx.coerceAtMost(MAX_LINE_BASED_HEIGHT_PX)
                    )
                    val absoluteHeightCapEmu = Units.pixelToEMU(MAX_IMAGE_HEIGHT_PX)
                    val maxAllowedHeightEmu = min(cellHeightEmu, min(lineBasedHeightEmu, absoluteHeightCapEmu))
                    if (maxAllowedHeightEmu <= 0) {
                        return ImageInsertResult(false, "기준 셀 높이가 0이어서 이미지를 삽입할 수 없습니다.")
                    }

                    val sourceWidthEmu = Units.pixelToEMU(image.width)
                    val sourceHeightEmu = Units.pixelToEMU(image.height)
                    if (sourceWidthEmu <= 0 || sourceHeightEmu <= 0) {
                        return ImageInsertResult(false, "생성된 이미지 크기가 유효하지 않습니다.")
                    }

                    val scale = min(1.0, maxAllowedHeightEmu.toDouble() / sourceHeightEmu.toDouble())
                    val displayWidthEmu = max(1, (sourceWidthEmu * scale).roundToInt())
                    val displayHeightEmu = max(1, (sourceHeightEmu * scale).roundToInt())

                    val startXEmu = if (laneMaxRightXEmu > cellLeftXEmu) {
                        laneMaxRightXEmu + Units.pixelToEMU(IMAGE_GAP_PX)
                    } else {
                        // 첫 이미지는 요청 보정값만큼 우측에서 시작
                        cellLeftXEmu + Units.pixelToEMU(FIRST_IMAGE_OFFSET_PX)
                    }
                    val startYEmu = cellTopYEmu
                    val endXEmu = startXEmu + displayWidthEmu
                    val endYEmu = startYEmu + displayHeightEmu

                    val startAnchor = resolveAnchorPointByEmu(sheet, startXEmu, startYEmu)
                    val endAnchor = resolveAnchorPointByEmu(sheet, endXEmu, endYEmu)

                    val anchor = XSSFClientAnchor(
                        startAnchor.dx,
                        startAnchor.dy,
                        endAnchor.dx,
                        endAnchor.dy,
                        startAnchor.col,
                        startAnchor.row,
                        endAnchor.col,
                        endAnchor.row
                    )
                    anchor.anchorType = ClientAnchor.AnchorType.MOVE_DONT_RESIZE
                    drawing.createPicture(anchor, pictureIndex) as XSSFPicture

                    FileOutputStream(file).use { fos ->
                        workbook.write(fos)
                    }
                }
            }

            ImageInsertResult(true, "이미지를 $baseSheet!$baseColumn$baseRow 위치에 추가했습니다.")
        }.getOrElse { error ->
            ImageInsertResult(false, "엑셀 이미지 삽입 실패: ${error.message ?: "알 수 없는 오류"}")
        }
    }

    private fun findLaneMaxRightXEmu(
        sheet: XSSFSheet,
        drawing: XSSFDrawing,
        laneStartXEmu: Int,
        laneTopYEmu: Int,
        laneBottomYEmu: Int
    ): Int {
        var maxRightXEmu = laneStartXEmu

        for (shape in drawing.shapes) {
            val picture = shape as? XSSFPicture ?: continue
            val anchor = picture.anchor as? XSSFClientAnchor ?: continue

            val rightXEmu = absoluteXFromAnchorEmu(sheet, anchor.col2.toInt(), anchor.dx2)
            val topYEmu = absoluteYFromAnchorEmu(sheet, anchor.row1.toInt(), anchor.dy1)

            val inSameLane =
                topYEmu >= laneTopYEmu && topYEmu < laneBottomYEmu && rightXEmu > laneStartXEmu
            if (inSameLane) {
                maxRightXEmu = max(maxRightXEmu, rightXEmu)
            }
        }

        return maxRightXEmu
    }

    private fun resolveAnchorPointByEmu(sheet: XSSFSheet, absoluteXEmu: Int, absoluteYEmu: Int): AnchorPoint {
        val (col, dx) = resolveXByEmu(sheet, absoluteXEmu)
        val (row, dy) = resolveYByEmu(sheet, absoluteYEmu)
        return AnchorPoint(col, dx, row, dy)
    }

    private fun resolveXByEmu(sheet: XSSFSheet, absoluteXEmu: Int): Pair<Int, Int> {
        var remaining = absoluteXEmu.coerceAtLeast(0)
        var col = 0

        while (true) {
            val width = columnWidthEmu(sheet, col)
            if (remaining <= width) break
            remaining -= width
            col += 1
        }

        val dx = remaining
        return col to dx
    }

    private fun resolveYByEmu(sheet: XSSFSheet, absoluteYEmu: Int): Pair<Int, Int> {
        var remaining = absoluteYEmu.coerceAtLeast(0)
        var row = 0

        while (true) {
            val height = rowHeightEmu(sheet, row)
            if (remaining <= height) break
            remaining -= height
            row += 1
        }

        val dy = remaining
        return row to dy
    }

    private fun absoluteXOfCellEmu(sheet: XSSFSheet, columnIndex: Int): Int {
        var x = 0
        for (c in 0 until columnIndex) {
            x += columnWidthEmu(sheet, c)
        }
        return x
    }

    private fun absoluteYOfRowEmu(sheet: XSSFSheet, rowIndex: Int): Int {
        var y = 0
        for (r in 0 until rowIndex) {
            y += rowHeightEmu(sheet, r)
        }
        return y
    }

    private fun absoluteXFromAnchorEmu(sheet: XSSFSheet, col: Int, dx: Int): Int =
        absoluteXOfCellEmu(sheet, col) + dx

    private fun absoluteYFromAnchorEmu(sheet: XSSFSheet, row: Int, dy: Int): Int =
        absoluteYOfRowEmu(sheet, row) + dy

    private fun columnWidthEmu(sheet: XSSFSheet, columnIndex: Int): Int =
        Units.columnWidthToEMU(sheet.getColumnWidth(columnIndex))

    private fun rowHeightEmu(sheet: XSSFSheet, rowIndex: Int): Int {
        val row = sheet.getRow(rowIndex)
        val points = row?.heightInPoints?.toDouble() ?: sheet.defaultRowHeightInPoints.toDouble()
        return Units.toEMU(points)
    }

    private fun columnToIndex(column: String): Int {
        var result = 0
        for (char in column) {
            if (char !in 'A'..'Z') {
                throw IllegalArgumentException("Invalid base column: $column")
            }
            result = result * 26 + (char - 'A' + 1)
        }
        return result - 1
    }

    private data class AnchorPoint(
        val col: Int,
        val dx: Int,
        val row: Int,
        val dy: Int
    )

    companion object {
        private const val IMAGE_GAP_PX = 8
        private const val FIRST_IMAGE_OFFSET_PX = 3
        private const val MAX_IMAGE_HEIGHT_PX = 220
        private const val MAX_LINE_BASED_HEIGHT_PX = 200
        private const val LINE_HEIGHT_PX = 18
        private const val LINE_BASE_PADDING_PX = 16
    }
}

data class ImageInsertResult(
    val success: Boolean,
    val message: String
)

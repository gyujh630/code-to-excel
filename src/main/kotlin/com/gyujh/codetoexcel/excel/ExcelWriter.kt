package com.gyujh.codetoexcel.excel

import com.gyujh.codetoexcel.excel.component.ExcelInsertComponent
import com.gyujh.codetoexcel.excel.component.GroupBoundsPx
import com.gyujh.codetoexcel.settings.ExcelSettingsState
import org.apache.poi.ss.usermodel.ClientAnchor
import org.apache.poi.util.Units
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFDrawing
import org.apache.poi.xssf.usermodel.XSSFPicture
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import java.util.zip.CRC32

class ExcelWriter {

    fun insertComponent(component: ExcelInsertComponent): ImageInsertResult {
        return insertComponent(component, null)
    }

    fun insertComponent(component: ExcelInsertComponent, rowOverride: Int?): ImageInsertResult {
        val settings = ExcelSettingsState.getInstance()
        val excelPath = settings.excelPath.trim()
        val baseSheet = settings.baseSheet.trim()
        val baseColumn = settings.baseColumn.trim().uppercase()
        val baseRow = rowOverride ?: settings.baseRow

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
            var insertedWidth = 0
            var insertedHeight = 0

            FileInputStream(file).use { fis ->
                XSSFWorkbook(fis).use { workbook ->
                    val sheet = workbook.getSheet(baseSheet)
                        ?: return ImageInsertResult(false, "시트를 찾을 수 없습니다: $baseSheet")

                    val info = insertComponent(
                        workbook = workbook,
                        sheet = sheet,
                        baseColumn = baseColumn,
                        baseRow = baseRow,
                        component = component
                    )
                    insertedWidth = info.bounds.width
                    insertedHeight = info.bounds.height

                    FileOutputStream(file).use { fos ->
                        workbook.write(fos)
                    }
                }
            }

            ImageInsertResult(
                true,
                "컴포넌트를 $baseSheet!$baseColumn$baseRow 위치에 추가했습니다. 그룹 크기: ${insertedWidth}x${insertedHeight}px"
            )
        }.getOrElse { error ->
            ImageInsertResult(false, "엑셀 이미지 삽입 실패: ${error.message ?: "알 수 없는 오류"}")
        }
    }

    fun insertComponent(
        workbook: XSSFWorkbook,
        sheet: XSSFSheet,
        baseColumn: String,
        baseRow: Int,
        component: ExcelInsertComponent
    ): InsertedPictureInfo {
        val targetColumnIndex = columnToIndex(baseColumn)
        val targetRowIndex = baseRow - 1

        val rendered = component.render(Int.MAX_VALUE)

        val pngBytes = ByteArrayOutputStream().use { bos ->
            ImageIO.write(rendered.image, "png", bos)
            bos.toByteArray()
        }
        val dpi = rendered.dpi.coerceAtLeast(96)
        val pngBytesWithDpi = addPngDpi(pngBytes, dpi, dpi)

        val pictureIndex = workbook.addPicture(pngBytesWithDpi, XSSFWorkbook.PICTURE_TYPE_PNG)
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

        val sourceWidthEmu = Units.pixelToEMU(rendered.image.width)
        val sourceHeightEmu = Units.pixelToEMU(rendered.image.height)
        val charDeltaScale = CODE_CHAR_WIDTH_DELTA_PX / BASE_CODE_CHAR_WIDTH_PX
        val deltaWidthEmu = max(1, (sourceWidthEmu * charDeltaScale).roundToInt())
        val deltaHeightEmu = max(1, (sourceHeightEmu * charDeltaScale).roundToInt())
        val maxAllowedHeightEmu = Units.pixelToEMU(MAX_IMAGE_HEIGHT_PX)
        val heightScale = min(1.0, maxAllowedHeightEmu.toDouble() / deltaHeightEmu.toDouble())
        val displayWidthEmu = max(1, (deltaWidthEmu * heightScale).roundToInt())
        val startXEmu = if (laneMaxRightXEmu > cellLeftXEmu) {
            laneMaxRightXEmu + Units.pixelToEMU(IMAGE_GAP_PX)
        } else {
            cellLeftXEmu + Units.pixelToEMU(FIRST_IMAGE_OFFSET_PX)
        }
        val startYEmu = cellTopYEmu

        val startAnchor = resolveAnchorPointByEmu(sheet, startXEmu, startYEmu)
        val actualStartXEmu = absoluteXFromAnchorEmu(sheet, startAnchor.col, startAnchor.dx)
        val actualStartYEmu = absoluteYFromAnchorEmu(sheet, startAnchor.row, startAnchor.dy)

        val widthScaleFromDelta = displayWidthEmu.toDouble() / sourceWidthEmu.toDouble()
        val heightLimitScale = maxAllowedHeightEmu.toDouble() / sourceHeightEmu.toDouble()

        // ✅ 수정: 셀 너비를 초과하지 않도록 cellWidthLimitScale 추가
        val cellWidthEmu = columnWidthEmu(sheet, targetColumnIndex)
        val cellWidthLimitScale = cellWidthEmu.toDouble() / sourceWidthEmu.toDouble()

        val finalScale = min(widthScaleFromDelta, min(heightLimitScale, cellWidthLimitScale))

        val targetWidthEmu = max(1, (sourceWidthEmu.toDouble() * finalScale).roundToInt())
        val targetHeightEmu = max(1, (sourceHeightEmu.toDouble() * finalScale).roundToInt())
        val endXEmu = actualStartXEmu + targetWidthEmu
        val endYEmu = actualStartYEmu + targetHeightEmu
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
        anchor.anchorType = ClientAnchor.AnchorType.DONT_MOVE_AND_RESIZE
        val picture = drawing.createPicture(anchor, pictureIndex) as XSSFPicture
        // Keep shape extents in exact EMU size to avoid tiny aspect drift from anchor/grid rounding.
        val ext = picture.ctPicture.spPr.xfrm.ext
        ext.setCx(targetWidthEmu.toLong())
        ext.setCy(targetHeightEmu.toLong())

        return InsertedPictureInfo(
            anchor = anchor,
            bounds = rendered.bounds
        )
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

            val topYEmu = absoluteYFromAnchorEmu(sheet, anchor.row1.toInt(), anchor.dy1)
            val leftXEmu = absoluteXFromAnchorEmu(sheet, anchor.col1.toInt(), anchor.dx1)
            val extCx = picture.ctPicture?.spPr?.xfrm?.ext?.cx?.toInt() ?: 0
            val rightXEmu = leftXEmu + max(0, extCx)

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
        val targetPx = absoluteXEmu.coerceAtLeast(0).toDouble() / Units.EMU_PER_PIXEL.toDouble()
        var col = 0
        var consumedPx = 0.0

        while (true) {
            val widthPx = columnWidthPx(sheet, col)
            if (consumedPx + widthPx >= targetPx) break
            consumedPx += widthPx
            col += 1
        }

        val dxPx = (targetPx - consumedPx).coerceAtLeast(0.0)
        val dx = (dxPx * Units.EMU_PER_PIXEL.toDouble()).roundToInt()
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
        var xPx = 0.0
        for (c in 0 until columnIndex) {
            xPx += columnWidthPx(sheet, c)
        }
        return (xPx * Units.EMU_PER_PIXEL.toDouble()).roundToInt()
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

    private fun columnWidthPx(sheet: XSSFSheet, columnIndex: Int): Double =
        sheet.getColumnWidthInPixels(columnIndex).toDouble()

    private fun columnWidthEmu(sheet: XSSFSheet, columnIndex: Int): Int =
        (columnWidthPx(sheet, columnIndex) * Units.EMU_PER_PIXEL.toDouble()).roundToInt()

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
        private const val MAX_IMAGE_HEIGHT_PX = 4096
        // One-code-character width delta used for Excel insert scaling (independent from cell width).
        private const val CODE_CHAR_WIDTH_DELTA_PX = 5.2
        // Baseline code-character width in the renderer's pixel space.
        private const val BASE_CODE_CHAR_WIDTH_PX = 8.0
    }
}

data class ImageInsertResult(
    val success: Boolean,
    val message: String
)

data class InsertedPictureInfo(
    val anchor: XSSFClientAnchor,
    val bounds: GroupBoundsPx
)

private fun addPngDpi(png: ByteArray, dpiX: Int, dpiY: Int): ByteArray {
    val signature = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47,
        0x0D, 0x0A, 0x1A, 0x0A
    )
    if (png.size < 8 || !png.copyOfRange(0, 8).contentEquals(signature)) return png

    var offset = 8
    if (png.size < offset + 8) return png

    val ihdrLength = readInt(png, offset)
    val ihdrType = String(png, offset + 4, 4, Charsets.ISO_8859_1)
    if (ihdrType != "IHDR") return png

    val ihdrTotal = 4 + 4 + ihdrLength + 4
    val insertAt = offset + ihdrTotal

    val xppm = (dpiX * 39.3701).toInt()
    val yppm = (dpiY * 39.3701).toInt()
    val data = ByteArray(9)
    writeInt(data, 0, xppm)
    writeInt(data, 4, yppm)
    data[8] = 1

    val chunk = buildChunk("pHYs", data)

    return png.copyOfRange(0, insertAt) + chunk + png.copyOfRange(insertAt, png.size)
}

private fun buildChunk(type: String, data: ByteArray): ByteArray {
    val length = data.size
    val out = ByteArray(4 + 4 + length + 4)
    writeInt(out, 0, length)
    val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
    System.arraycopy(typeBytes, 0, out, 4, 4)
    System.arraycopy(data, 0, out, 8, length)

    val crc = CRC32()
    crc.update(typeBytes)
    crc.update(data)
    writeInt(out, 8 + length, crc.value.toInt())
    return out
}

private fun readInt(bytes: ByteArray, offset: Int): Int {
    return (bytes[offset].toInt() and 0xFF shl 24) or
            (bytes[offset + 1].toInt() and 0xFF shl 16) or
            (bytes[offset + 2].toInt() and 0xFF shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}

private fun writeInt(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = (value ushr 24).toByte()
    bytes[offset + 1] = (value ushr 16).toByte()
    bytes[offset + 2] = (value ushr 8).toByte()
    bytes[offset + 3] = value.toByte()
}

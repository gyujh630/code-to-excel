package com.gyujh.codetoexcel.staging

import com.gyujh.codetoexcel.editor.SelectionMetadata
import com.gyujh.codetoexcel.excel.ExcelWriter
import com.gyujh.codetoexcel.excel.InsertedPictureInfo
import com.gyujh.codetoexcel.excel.component.CodeImageExcelComponent
import com.gyujh.codetoexcel.settings.ExcelSettingsState
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.ImageIO
import org.apache.poi.ss.usermodel.SheetVisibility
import org.apache.poi.xssf.usermodel.XSSFDrawing
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class StagingService(
    private val storage: StagingStorage = StagingStorage()
) {

    fun saveSelection(
        project: Project,
        metadata: SelectionMetadata,
        sourceImage: java.awt.image.BufferedImage
    ) {
        val settings = ExcelSettingsState.getInstance()
        val excelPath = settings.excelPath.trim()
        val baseRow = settings.baseRow

        if (excelPath.isEmpty()) {
            notify(project, "엑셀 파일이 선택되지 않았습니다.", NotificationType.ERROR)
            return
        }
        if (baseRow < 1) {
            notify(project, "Base Row는 1 이상이어야 합니다.", NotificationType.ERROR)
            return
        }

        val title = java.nio.file.Path.of(metadata.fileName).fileName.toString()
        val lineCount = (metadata.endLine - metadata.startLine + 1).coerceAtLeast(1)
        saveImageToRow(
            project = project,
            excelPath = excelPath,
            rowNumber = baseRow,
            image = sourceImage,
            title = title,
            lineCount = lineCount,
            startLine = metadata.startLine,
            endLine = metadata.endLine
        )
    }

    fun saveImageToRow(
        project: Project,
        excelPath: String,
        rowNumber: Int,
        image: java.awt.image.BufferedImage,
        title: String,
        lineCount: Int,
        startLine: Int = 0,
        endLine: Int = 0
    ) {
        if (excelPath.isEmpty()) {
            notify(project, "엑셀 파일이 선택되지 않았습니다.", NotificationType.ERROR)
            return
        }
        if (rowNumber < 1) {
            notify(project, "Base Row는 1 이상이어야 합니다.", NotificationType.ERROR)
            return
        }

        val rowFolder = storage.ensureRowFolder(excelPath, rowNumber)
        val index = storage.loadExcelIndex(excelPath)
        val rowKey = rowNumber.toString()
        val rowEntry = index.rows.getOrPut(rowKey) { RowEntry(title = "TC$rowNumber") }

        val nextOrder = (rowEntry.items.maxOfOrNull { it.order } ?: 0) + 1
        val fileName = "$nextOrder.png"
        val itemId = "img-" + UUID.randomUUID().toString()

        val pngBytes = ByteArrayOutputStream().use { bos ->
            ImageIO.write(image, "png", bos)
            bos.toByteArray()
        }

        storage.writeImage(rowFolder, fileName, pngBytes)

        rowEntry.items.add(
            StagedItem(
                id = itemId,
                order = nextOrder,
                file = fileName,
                title = title,
                lineCount = lineCount,
                startLine = startLine,
                endLine = endLine,
                desc = ""
            )
        )
        storage.saveExcelIndex(excelPath, index)

        publishUpdate(excelPath)
        notify(project, "이미지를 TC$rowNumber 폴더에 저장했습니다.", NotificationType.INFORMATION)
    }

    fun deleteItem(project: Project, excelPath: String, rowKey: String, itemId: String) {
        val index = storage.loadExcelIndex(excelPath)
        val rowEntry = index.rows[rowKey] ?: return
        val item = rowEntry.items.firstOrNull { it.id == itemId } ?: return

        val rowNumber = rowKey.toIntOrNull() ?: return
        val rowFolder = storage.ensureRowFolder(excelPath, rowNumber)
        val target = rowFolder.resolve(item.file)
        Files.deleteIfExists(target)
        rowEntry.items.removeIf { it.id == itemId }
        renumberRowFiles(rowFolder, rowEntry)

        storage.saveExcelIndex(excelPath, index)
        publishUpdate(excelPath)
        notify(project, "이미지를 삭제했습니다.", NotificationType.INFORMATION)
    }

    fun reorderRow(
        project: Project,
        excelPath: String,
        rowKey: String,
        orderedIds: List<String>
    ) {
        val index = storage.loadExcelIndex(excelPath)
        val rowEntry = index.rows[rowKey] ?: return
        val rowNumber = rowKey.toIntOrNull() ?: return
        val rowFolder = storage.ensureRowFolder(excelPath, rowNumber)

        val byId = rowEntry.items.associateBy { it.id }
        val newItems = orderedIds.mapNotNull { byId[it] }.toMutableList()
        if (newItems.size != rowEntry.items.size) return

        rowEntry.items.clear()
        rowEntry.items.addAll(newItems)

        renumberRowFiles(rowFolder, rowEntry)
        storage.saveExcelIndex(excelPath, index)

        publishUpdate(excelPath)
        notify(project, "순서를 변경했습니다.", NotificationType.INFORMATION)
    }

    fun applyAll(project: Project) {
        val settings = ExcelSettingsState.getInstance()
        val excelPath = settings.excelPath.trim()
        val baseSheet = settings.baseSheet.trim()
        val baseColumn = settings.baseColumn.trim().uppercase()
        val baseRowStart = settings.baseRowStart
        if (excelPath.isEmpty()) {
            notify(project, "엑셀 파일이 선택되지 않았습니다.", NotificationType.ERROR)
            return
        }
        if (baseSheet.isEmpty()) {
            notify(project, "Base Sheet가 선택되지 않았습니다.", NotificationType.ERROR)
            return
        }
        if (baseColumn.isEmpty()) {
            notify(project, "Base Column이 선택되지 않았습니다.", NotificationType.ERROR)
            return
        }
        if (baseRowStart < 1) {
            notify(project, "첫 테스트케이스 행은 1 이상이어야 합니다.", NotificationType.ERROR)
            return
        }
        if (!baseColumn.matches(Regex("^[A-Z]+$"))) {
            notify(project, "Base Column 값이 유효하지 않습니다: $baseColumn", NotificationType.ERROR)
            return
        }

        val index = storage.loadExcelIndex(excelPath)
        val file = File(excelPath)
        if (!file.exists()) {
            notify(project, "선택된 엑셀 파일이 존재하지 않습니다.", NotificationType.ERROR)
            return
        }

        val writer = ExcelWriter()
        FileInputStream(file).use { fis ->
            XSSFWorkbook(fis).use { workbook ->
                val sheet = workbook.getSheet(baseSheet)
                    ?: run {
                        notify(project, "시트를 찾을 수 없습니다: $baseSheet", NotificationType.ERROR)
                        return
                    }

                val hiddenSheet = getOrCreateHiddenSheet(workbook)
                val existing = loadHiddenEntries(hiddenSheet)

                val desiredItems = index.rows
                    .toSortedMap(compareBy { it.toIntOrNull() ?: 0 })
                    .flatMap { (rowKey, rowEntry) ->
                        val rowNumber = rowKey.toIntOrNull() ?: return@flatMap emptyList()
                        rowEntry.items.sortedBy { it.order }.map { item ->
                            val excelRow = baseRowStart + (rowNumber - 1)
                            DesiredItem(excelRow, item)
                        }
                    }
                val retained = existing.values.filter { it.sheetName != baseSheet }.toMutableList()

                // clear all managed pictures for this sheet (rebuild to reflect deletes/reorders)
                val toRemove = existing.values.filter { it.sheetName == baseSheet }
                toRemove.forEach { entry ->
                    removePictureByAnchor(sheet, entry)
                }

                // insert all desired items in order
                val newEntries = mutableListOf<HiddenEntry>()
                for (desired in desiredItems) {
                    val rowFolder = storage.ensureRowFolder(excelPath, desired.rowNumber)
                    val imageFile = rowFolder.resolve(desired.item.file)
                    if (!Files.exists(imageFile)) {
                        notify(project, "이미지 파일을 찾을 수 없습니다: ${desired.item.file}", NotificationType.ERROR)
                        return
                    }
                    val sourceImage = ImageIO.read(imageFile.toFile())
                    val component = CodeImageExcelComponent(
                        title = desired.item.title,
                        sourceImage = sourceImage,
                        lineCount = desired.item.lineCount
                    )
                    val info = writer.insertComponent(
                        workbook = workbook,
                        sheet = sheet,
                        baseColumn = baseColumn,
                        baseRow = desired.rowNumber,
                        component = component
                    )
                    newEntries.add(HiddenEntry.from(desired.item.id, baseSheet, info))
                }

                rewriteHiddenSheet(hiddenSheet, retained + newEntries)
                workbook.setSheetVisibility(workbook.getSheetIndex(hiddenSheet), SheetVisibility.VERY_HIDDEN)

                FileOutputStream(file).use { fos ->
                    workbook.write(fos)
                }
            }
        }

        notify(project, "엑셀 반영이 완료되었습니다.", NotificationType.INFORMATION)
    }

    private fun renumberRowFiles(rowFolder: Path, rowEntry: RowEntry) {
        val tempFiles = mutableListOf<Pair<Path, Path>>()
        rowEntry.items.forEachIndexed { index, item ->
            val source = rowFolder.resolve(item.file)
            val temp = rowFolder.resolve("tmp-${item.id}.png")
            if (Files.exists(source)) {
                storage.renameFileSafe(source, temp)
                tempFiles.add(temp to source)
            }
            item.order = index + 1
            item.file = "${index + 1}.png"
        }

        rowEntry.items.forEach { item ->
            val temp = rowFolder.resolve("tmp-${item.id}.png")
            val target = rowFolder.resolve(item.file)
            if (Files.exists(temp)) {
                storage.renameFileSafe(temp, target)
            }
        }
    }

    private fun publishUpdate(excelPath: String) {
        ApplicationManager.getApplication()
            .messageBus
            .syncPublisher(STAGING_TOPIC)
            .onStagingUpdated(excelPath)
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("CodeToExcelNotification")
            .createNotification("Code To Excel", message, type)
            .notify(project)
    }
}

private data class DesiredItem(
    val rowNumber: Int,
    val item: StagedItem
)

private data class HiddenEntry(
    val id: String,
    val sheetName: String,
    val row1: Int,
    val col1: Int,
    val dx1: Int,
    val dy1: Int,
    val row2: Int,
    val col2: Int,
    val dx2: Int,
    val dy2: Int
) {
    companion object {
        fun from(id: String, sheetName: String, info: InsertedPictureInfo): HiddenEntry {
            val anchor = info.anchor
            return HiddenEntry(
                id = id,
                sheetName = sheetName,
                row1 = anchor.row1,
                col1 = anchor.col1.toInt(),
                dx1 = anchor.dx1,
                dy1 = anchor.dy1,
                row2 = anchor.row2,
                col2 = anchor.col2.toInt(),
                dx2 = anchor.dx2,
                dy2 = anchor.dy2
            )
        }
    }
}

private const val HIDDEN_SHEET_NAME = "_code_to_excel"

private fun getOrCreateHiddenSheet(workbook: XSSFWorkbook): XSSFSheet {
    val existing = workbook.getSheet(HIDDEN_SHEET_NAME)
    if (existing != null) return existing
    val sheet = workbook.createSheet(HIDDEN_SHEET_NAME)
    workbook.setSheetVisibility(workbook.getSheetIndex(sheet), SheetVisibility.VERY_HIDDEN)
    return sheet
}

private fun loadHiddenEntries(sheet: XSSFSheet): MutableMap<String, HiddenEntry> {
    val map = mutableMapOf<String, HiddenEntry>()
    val last = sheet.lastRowNum
    for (i in 1..last) {
        val row = sheet.getRow(i) ?: continue
        val id = row.getCell(0)?.stringCellValue?.trim().orEmpty()
        if (id.isEmpty()) continue
        val sheetName = row.getCell(1)?.stringCellValue?.trim().orEmpty()
        val row1 = row.getCell(2)?.numericCellValue?.toInt() ?: 0
        val col1 = row.getCell(3)?.numericCellValue?.toInt() ?: 0
        val dx1 = row.getCell(4)?.numericCellValue?.toInt() ?: 0
        val dy1 = row.getCell(5)?.numericCellValue?.toInt() ?: 0
        val row2 = row.getCell(6)?.numericCellValue?.toInt() ?: 0
        val col2 = row.getCell(7)?.numericCellValue?.toInt() ?: 0
        val dx2 = row.getCell(8)?.numericCellValue?.toInt() ?: 0
        val dy2 = row.getCell(9)?.numericCellValue?.toInt() ?: 0
        map[id] = HiddenEntry(id, sheetName, row1, col1, dx1, dy1, row2, col2, dx2, dy2)
    }
    return map
}

private fun rewriteHiddenSheet(sheet: XSSFSheet, entries: List<HiddenEntry>) {
    val last = sheet.lastRowNum
    for (i in last downTo 0) {
        val row = sheet.getRow(i)
        if (row != null) sheet.removeRow(row)
    }
    var rowIndex = 0
    val header = sheet.createRow(rowIndex++)
    header.createCell(0).setCellValue("id")
    header.createCell(1).setCellValue("sheet")
    header.createCell(2).setCellValue("row1")
    header.createCell(3).setCellValue("col1")
    header.createCell(4).setCellValue("dx1")
    header.createCell(5).setCellValue("dy1")
    header.createCell(6).setCellValue("row2")
    header.createCell(7).setCellValue("col2")
    header.createCell(8).setCellValue("dx2")
    header.createCell(9).setCellValue("dy2")

    entries.forEach { entry ->
        val row = sheet.createRow(rowIndex++)
        row.createCell(0).setCellValue(entry.id)
        row.createCell(1).setCellValue(entry.sheetName)
        row.createCell(2).setCellValue(entry.row1.toDouble())
        row.createCell(3).setCellValue(entry.col1.toDouble())
        row.createCell(4).setCellValue(entry.dx1.toDouble())
        row.createCell(5).setCellValue(entry.dy1.toDouble())
        row.createCell(6).setCellValue(entry.row2.toDouble())
        row.createCell(7).setCellValue(entry.col2.toDouble())
        row.createCell(8).setCellValue(entry.dx2.toDouble())
        row.createCell(9).setCellValue(entry.dy2.toDouble())
    }
}

private fun removePictureByAnchor(sheet: XSSFSheet, entry: HiddenEntry): Boolean {
    val drawing = sheet.drawingPatriarch as? XSSFDrawing ?: return false
    val ctDrawing = drawing.ctDrawing
    val anchors = ctDrawing.twoCellAnchorList
    for (i in anchors.size - 1 downTo 0) {
        val anchor = anchors[i]
        val from = anchor.from
        val to = anchor.to
        val match = from.col.toInt() == entry.col1 &&
            from.row.toInt() == entry.row1 &&
            from.colOff.toString().toInt() == entry.dx1 &&
            from.rowOff.toString().toInt() == entry.dy1 &&
            to.col.toInt() == entry.col2 &&
            to.row.toInt() == entry.row2 &&
            to.colOff.toString().toInt() == entry.dx2 &&
            to.rowOff.toString().toInt() == entry.dy2
        if (match) {
            ctDrawing.removeTwoCellAnchor(i)
            return true
        }
    }
    return false
}

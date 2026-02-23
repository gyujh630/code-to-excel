package com.gyujh.codetoexcel.excel

import com.gyujh.codetoexcel.settings.ExcelSettingsState
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ExcelWriter {

    fun writeTestValue(value: String) {
        val settings = ExcelSettingsState.getInstance()
        val file = File(settings.excelPath)

        if (!file.exists()) return

        val fis = FileInputStream(file)
        val workbook = XSSFWorkbook(fis)
        fis.close()

        val sheet = if (settings.baseSheet.isNotBlank()) {
            workbook.getSheet(settings.baseSheet) ?: workbook.getSheetAt(0)
        } else {
            workbook.getSheetAt(0)
        }

        val position = calculatePosition(
            settings.baseColumn,
            settings.baseRow
        )

        val row = sheet.getRow(position.rowIndex)
            ?: sheet.createRow(position.rowIndex)

        val cell = row.getCell(position.columnIndex)
            ?: row.createCell(position.columnIndex)

        cell.setCellValue(value)

        val fos = FileOutputStream(file)
        workbook.write(fos)
        fos.close()
        workbook.close()
    }

    private fun calculatePosition(column: String, row: Int): CellPosition {
        val columnIndex = columnToIndex(column)
        val rowIndex = row - 1  // Excel은 0-based

        return CellPosition(columnIndex, rowIndex)
    }

    private fun columnToIndex(column: String): Int {
        var result = 0
        for (char in column) {
            result = result * 26 + (char - 'A' + 1)
        }
        return result - 1
    }
}

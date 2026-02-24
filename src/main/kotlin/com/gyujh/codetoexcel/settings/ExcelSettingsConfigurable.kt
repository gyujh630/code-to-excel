package com.gyujh.codetoexcel.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.util.CellReference
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import java.io.File

class ExcelSettingsConfigurable : Configurable {

    private val excelPathField = TextFieldWithBrowseButton()
    private val clearFileButton = JButton("해제")
    private val baseSheetCombo = JComboBox<String>()
    private val baseColumnCombo = JComboBox<ColumnOption>()
    private val baseRowStartField = JTextField(5)

    override fun getDisplayName(): String = "Code To Excel"

    override fun createComponent(): JComponent {
        val settings = ExcelSettingsState.Companion.getInstance()

        excelPathField.textField.isEditable = false
        excelPathField.text = settings.excelPath
        baseRowStartField.text = settings.baseRowStart.toString()
        baseSheetCombo.isEnabled = false
        baseColumnCombo.isEnabled = false

        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("xlsx")
        descriptor.title = "Select Excel File"
        descriptor.description = "Choose an Excel (.xlsx) file"

        excelPathField.addActionListener {
            val virtualFile = FileChooser.chooseFile(
                descriptor,
                null,
                null
            )

            if (virtualFile != null) {
                excelPathField.text = virtualFile.path
                refreshSheets(
                    virtualFile.path,
                    baseSheetCombo.selectedItem as? String,
                    selectedColumnRef()
                )
            }
        }

        clearFileButton.addActionListener {
            clearSelection()
        }

        excelPathField.textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = onPathChanged()
            override fun removeUpdate(e: DocumentEvent?) = onPathChanged()
            override fun changedUpdate(e: DocumentEvent?) = onPathChanged()
        })
        baseRowStartField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = onBaseRowStartChanged()
            override fun removeUpdate(e: DocumentEvent?) = onBaseRowStartChanged()
            override fun changedUpdate(e: DocumentEvent?) = onBaseRowStartChanged()
        })

        baseSheetCombo.addActionListener {
            refreshColumns(
                excelPathField.text.trim(),
                baseSheetCombo.selectedItem as? String,
                selectedColumnRef()
            )
        }

        refreshSheets(
            settings.excelPath,
            settings.baseSheet.ifBlank { null },
            settings.baseColumn
        )

        val baseColumnDescription = JLabel("코드 삽입이 될 기준열을 선택합니다.")
        baseColumnDescription.font = baseColumnDescription.font.deriveFont(baseColumnDescription.font.size2D - 1f)

        val filePanel = JPanel(BorderLayout(8, 0))
        filePanel.add(excelPathField, BorderLayout.CENTER)
        filePanel.add(clearFileButton, BorderLayout.EAST)

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Excel File:", filePanel)
            .addLabeledComponent("첫 테스트케이스 행:", baseRowStartField)
            .addLabeledComponent("Base Sheet:", baseSheetCombo)
            .addLabeledComponent("Base Column:", baseColumnCombo)
            .addComponent(baseColumnDescription)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val settings = ExcelSettingsState.Companion.getInstance()
        val selectedSheet = baseSheetCombo.selectedItem as? String ?: ""
        val selectedColumn = selectedColumnRef() ?: ""

        return excelPathField.text != settings.excelPath ||
                baseRowStartField.text.trim() != settings.baseRowStart.toString() ||
                selectedSheet != settings.baseSheet ||
                selectedColumn != settings.baseColumn
    }

    override fun apply() {
        val settings = ExcelSettingsState.Companion.getInstance()

        val path = excelPathField.text.trim()
        val selectedSheet = baseSheetCombo.selectedItem as? String
        val selectedColumn = selectedColumnRef()
        val baseRowStartValue = baseRowStartField.text.trim().toIntOrNull() ?: 0

        // 파일 미선택 상태 저장 허용
        if (path.isEmpty()) {
            settings.excelPath = ""
            settings.baseSheet = ""
            settings.baseColumn = "A"
            settings.baseRowStart = 1
            return
        }

        val file = File(path)
        if (!file.exists()) {
            Messages.showErrorDialog(
                "Selected Excel file does not exist.",
                "Invalid Excel File"
            )
            return
        }

        if (selectedSheet.isNullOrBlank()) {
            Messages.showErrorDialog(
                "Please select a base sheet.",
                "Base Sheet Required"
            )
            return
        }

        if (selectedColumn.isNullOrBlank()) {
            Messages.showErrorDialog(
                "Please select a base column.",
                "Base Column Required"
            )
            return
        }
        if (baseRowStartValue < 1) {
            Messages.showErrorDialog(
                "첫 테스트케이스 행은 1 이상이어야 합니다.",
                "Invalid Row"
            )
            return
        }

        settings.excelPath = path
        settings.baseSheet = selectedSheet
        settings.baseColumn = selectedColumn
        settings.baseRowStart = baseRowStartValue
    }

    private fun onPathChanged() {
        refreshSheets(
            excelPathField.text.trim(),
            baseSheetCombo.selectedItem as? String,
            selectedColumnRef()
        )
    }

    private fun onBaseRowStartChanged() {
        refreshColumns(
            excelPathField.text.trim(),
            baseSheetCombo.selectedItem as? String,
            selectedColumnRef()
        )
    }

    private fun clearSelection() {
        excelPathField.text = ""
        baseSheetCombo.removeAllItems()
        baseColumnCombo.removeAllItems()
        baseSheetCombo.isEnabled = false
        baseColumnCombo.isEnabled = false
    }

    private fun selectedColumnRef(): String? =
        (baseColumnCombo.selectedItem as? ColumnOption)?.columnRef

    private fun refreshSheets(path: String, preferredSheet: String?, preferredColumn: String?) {
        val sheets = if (path.isBlank()) {
            emptyList()
        } else {
            readSheetNames(path)
        }

        baseSheetCombo.removeAllItems()
        sheets.forEach { baseSheetCombo.addItem(it) }
        baseSheetCombo.isEnabled = sheets.isNotEmpty()

        val targetSheet = preferredSheet?.takeIf { sheets.contains(it) } ?: sheets.firstOrNull()
        if (targetSheet != null) {
            baseSheetCombo.selectedItem = targetSheet
        }

        refreshColumns(path, targetSheet, preferredColumn)
    }

    private fun refreshColumns(path: String, sheetName: String?, preferredColumn: String?) {
        val columns = if (path.isBlank() || sheetName.isNullOrBlank()) {
            emptyList()
        } else {
            val rowIndex = (baseRowStartField.text.trim().toIntOrNull() ?: 1) - 1
            readColumns(path, sheetName, rowIndex)
        }

        baseColumnCombo.removeAllItems()
        columns.forEach { baseColumnCombo.addItem(it) }
        baseColumnCombo.isEnabled = columns.isNotEmpty()

        val targetColumn = preferredColumn?.uppercase()?.takeIf { preferred ->
            columns.any { it.columnRef == preferred }
        } ?: columns.firstOrNull()?.columnRef

        if (targetColumn != null) {
            for (i in 0 until baseColumnCombo.itemCount) {
                val option = baseColumnCombo.getItemAt(i)
                if (option.columnRef == targetColumn) {
                    baseColumnCombo.selectedIndex = i
                    break
                }
            }
        }
    }

    private fun readSheetNames(path: String): List<String> {
        return runCatching {
            WorkbookFactory.create(File(path)).use { workbook ->
                (0 until workbook.numberOfSheets).map { workbook.getSheetName(it) }
            }
        }.getOrElse { emptyList() }
    }

    private fun readColumns(path: String, sheetName: String, rowIndex: Int): List<ColumnOption> {
        return runCatching {
            WorkbookFactory.create(File(path)).use { workbook ->
                val sheet = workbook.getSheet(sheetName) ?: return@use emptyList()
                val formatter = DataFormatter()
                val headerRow = sheet.getRow(rowIndex)

                if (headerRow == null || headerRow.lastCellNum <= 0) {
                    return@use emptyList()
                }

                (0 until headerRow.lastCellNum).map { columnIndex ->
                    val columnRef = CellReference.convertNumToColString(columnIndex)
                    val header = formatter.formatCellValue(headerRow.getCell(columnIndex)).trim()
                    val label = if (header.isEmpty()) columnRef else "$columnRef - $header"
                    ColumnOption(label, columnRef)
                }
            }
        }.getOrElse { emptyList() }
    }

    private data class ColumnOption(
        private val label: String,
        val columnRef: String
    ) {
        override fun toString(): String = label
    }
}

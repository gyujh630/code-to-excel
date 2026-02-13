package com.gyujh.codetoexcel.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.*
import java.io.File

class ExcelSettingsConfigurable : Configurable {

    private val excelPathField = TextFieldWithBrowseButton()
    private val baseColumnField = JBTextField()

    override fun getDisplayName(): String = "Code To Excel"

    override fun createComponent(): JComponent {
        val settings = ExcelSettingsState.Companion.getInstance()

        excelPathField.textField.isEditable = false
        excelPathField.text = settings.excelPath
        baseColumnField.text = settings.baseColumn

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
            }
        }

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Excel File:", excelPathField)
            .addLabeledComponent("Base Column (A, B, C...):", baseColumnField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val settings = ExcelSettingsState.Companion.getInstance()

        return excelPathField.text != settings.excelPath ||
                baseColumnField.text != settings.baseColumn
    }

    override fun apply() {
        val settings = ExcelSettingsState.Companion.getInstance()

        val path = excelPathField.text.trim()
        val column = baseColumnField.text.trim()

        // Excel 파일 선택 필수
        if (path.isEmpty()) {
            Messages.showErrorDialog(
                "Please select an Excel file.",
                "Excel File Required"
            )
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

        // Base Column 유효성 검사 (대문자만 허용)
        val columnRegex = Regex("^[A-Z]+$")

        if (!columnRegex.matches(column)) {
            Messages.showErrorDialog(
                "Base Column must contain only uppercase letters (A-Z).\nExamples: A, B, AA, AB",
                "Invalid Base Column"
            )
            return
        }

        settings.excelPath = path
        settings.baseColumn = column
    }
}

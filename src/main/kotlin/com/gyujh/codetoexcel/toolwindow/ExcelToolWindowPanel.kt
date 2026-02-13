package com.gyujh.codetoexcel.toolwindow

import com.gyujh.codetoexcel.settings.ExcelSettingsState
import javax.swing.*
import java.awt.BorderLayout

class ExcelToolWindowPanel : JPanel(BorderLayout()) {

    private val rowField = JTextField(5)
    private val saveButton = JButton("Update Base Row")

    init {
        val settings = ExcelSettingsState.getInstance()

        rowField.text = settings.baseRow.toString()

        val panel = JPanel()
        panel.add(JLabel("Base Row (1부터 시작): "))
        panel.add(rowField)
        panel.add(saveButton)

        add(panel, BorderLayout.NORTH)

        saveButton.addActionListener {
            val value = rowField.text.toIntOrNull()
            if (value != null && value >= 1) {
                settings.baseRow = value
            }
        }
    }
}

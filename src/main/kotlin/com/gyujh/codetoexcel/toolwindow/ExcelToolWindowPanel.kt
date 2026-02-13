package com.gyujh.codetoexcel.toolwindow

import com.gyujh.codetoexcel.settings.ExcelSettingsState
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.*

class ExcelToolWindowPanel : JPanel(BorderLayout()) {

    private val rowField = JBTextField(5)
    private val saveButton = JButton("Update")

    init {
        val settings = ExcelSettingsState.getInstance()
        rowField.text = settings.baseRow.toString()

        saveButton.addActionListener {
            val value = rowField.text.toIntOrNull()
            if (value != null && value >= 1) {
                settings.baseRow = value
            }
        }

        // 🔹 상단 한 줄 패널
        val rowPanel = JBPanel<JBPanel<*>>()
        rowPanel.layout = BoxLayout(rowPanel, BoxLayout.X_AXIS)
        rowPanel.border = JBUI.Borders.empty(8, 12, 0, 12)

        rowPanel.add(JBLabel("Base Row:"))
        rowPanel.add(Box.createHorizontalStrut(8))
        rowPanel.add(rowField)
        rowPanel.add(Box.createHorizontalStrut(8))
        rowPanel.add(saveButton)

        // 🔹 설명 라벨
        val descriptionLabel = JBLabel("현재 위치한 테스트케이스 번호를 의미합니다.")
        descriptionLabel.foreground = UIUtil.getContextHelpForeground()
        descriptionLabel.border = JBUI.Borders.empty(4, 12, 8, 12)

        val container = JBPanel<JBPanel<*>>(BorderLayout())
        container.add(rowPanel, BorderLayout.NORTH)
        container.add(descriptionLabel, BorderLayout.CENTER)

        add(container, BorderLayout.NORTH)
    }

    fun updateRowField(value: Int) {
        rowField.text = value.toString()
    }
}

package com.gyujh.codetoexcel.actions

import com.gyujh.codetoexcel.settings.ExcelSettingsState
import com.gyujh.codetoexcel.toolwindow.ExcelToolWindowPanel
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

class IncreaseRowAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = ExcelSettingsState.getInstance()

        settings.baseRow += 1

        // 🔹 ToolWindow 동기화
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Code To Excel")

        val content = toolWindow?.contentManager?.contents?.firstOrNull()
        val panel = content?.component as? ExcelToolWindowPanel
        panel?.updateRowField(settings.baseRow)

        // 🔹 제목 + 내용 형태 알림
        NotificationGroupManager.getInstance()
            .getNotificationGroup("CodeToExcelNotification")
            .createNotification(
                "Code To Excel",
                "테스트케이스 번호 업데이트  [TC-${settings.baseRow}번]",
                NotificationType.INFORMATION
            )
            .notify(project)
    }
}

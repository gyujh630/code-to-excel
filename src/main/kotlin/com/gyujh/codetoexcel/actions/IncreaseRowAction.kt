package com.gyujh.codetoexcel.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger

class IncreaseRowAction : AnAction() {

    private val log = Logger.getInstance(IncreaseRowAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        println("IncreaseRowAction triggered")
        log.info("CodeToExcel → IncreaseRowAction triggered")
    }
}

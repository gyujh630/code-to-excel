package com.gyujh.codetoexcel.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger

class DecreaseRowAction : AnAction() {

    private val log = Logger.getInstance(DecreaseRowAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        println("DecreaseRowAction triggered")
        log.info("CodeToExcel → DecreaseRowAction triggered")
    }
}

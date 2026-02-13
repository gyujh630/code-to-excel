package com.gyujh.codetoexcel.startup

import com.gyujh.codetoexcel.excel.ExcelWriter
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class StartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val writer = ExcelWriter()
        writer.writeTestValue("Hello from CodeToExcel")
    }
}

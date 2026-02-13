package com.gyujh.codetoexcel.settings

import com.intellij.openapi.components.*

@State(
    name = "ExcelSettingsState",
    storages = [Storage("code-to-excel.xml")]
)
class ExcelSettingsState : PersistentStateComponent<ExcelSettingsState> {

    var excelPath: String = ""
    var baseColumn: String = "A"
    var baseRow: Int = 1

    override fun getState(): ExcelSettingsState = this

    override fun loadState(state: ExcelSettingsState) {
        this.excelPath = state.excelPath
        this.baseColumn = state.baseColumn
        this.baseRow = state.baseRow
    }

    companion object {
        fun getInstance(): ExcelSettingsState =
            service()
    }
}

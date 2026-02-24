package com.gyujh.codetoexcel.settings

import com.intellij.openapi.components.*

@State(
    name = "ExcelSettingsState",
    storages = [Storage("code-to-excel.xml")]
)
class ExcelSettingsState : PersistentStateComponent<ExcelSettingsState> {

    var excelPath: String = ""
    var baseSheet: String = ""
    var baseColumn: String = "A"
    // 테스트케이스 번호 (TC1, TC2 ...)
    var baseRow: Int = 1
    // 첫 테스트케이스가 들어갈 엑셀 행 번호
    var baseRowStart: Int = 1

    override fun getState(): ExcelSettingsState = this

    override fun loadState(state: ExcelSettingsState) {
        this.excelPath = state.excelPath
        this.baseSheet = state.baseSheet
        this.baseColumn = state.baseColumn
        this.baseRow = state.baseRow
        this.baseRowStart = state.baseRowStart
    }

    companion object {
        fun getInstance(): ExcelSettingsState =
            service()
    }
}

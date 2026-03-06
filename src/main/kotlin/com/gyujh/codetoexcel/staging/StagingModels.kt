package com.gyujh.codetoexcel.staging

data class RegistryIndex(
    val entries: MutableList<RegistryEntry> = mutableListOf()
)

data class RegistryEntry(
    val excelPath: String,
    val folderName: String,
    val id: String
)

data class ExcelIndex(
    val excel: ExcelMeta,
    val rows: MutableMap<String, RowEntry> = mutableMapOf()
)

data class ExcelMeta(
    val displayName: String,
    val path: String,
    val id: String
)

data class RowEntry(
    val title: String,
    val items: MutableList<StagedItem> = mutableListOf()
)

data class StagedItem(
    val id: String,
    var order: Int,
    var file: String,
    val title: String,
    val lineCount: Int,
    val startLine: Int = 0,
    val endLine: Int = 0,
    var desc: String = "",
    var renderScale: Int = 1
)

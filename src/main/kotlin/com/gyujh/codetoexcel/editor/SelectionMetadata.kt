package com.gyujh.codetoexcel.editor

data class SelectionMetadata(
    val fileName: String,
    val startLine: Int,
    val endLine: Int,
    val code: String
)

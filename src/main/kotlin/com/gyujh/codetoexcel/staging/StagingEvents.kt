package com.gyujh.codetoexcel.staging

import com.intellij.util.messages.Topic

interface StagingListener {
    fun onStagingUpdated(excelPath: String)
}

val STAGING_TOPIC: Topic<StagingListener> =
    Topic.create("CodeToExcelStaging", StagingListener::class.java)

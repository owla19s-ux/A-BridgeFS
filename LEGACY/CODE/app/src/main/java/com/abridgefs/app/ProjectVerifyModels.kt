package com.abridgefs.app

data class BridgeVerifyRecord(
    val commitSha: String,
    val runId: Long? = null,
    val status: String? = null,
    val conclusion: String? = null,
    val jobCount: Int = 0,
    val failedJobCount: Int = 0,
    val artifactCount: Int = 0,
    val artifactNames: List<String> = emptyList(),
    val state: String = "NOT_QUERIED",
    val time: Long = System.currentTimeMillis()
)

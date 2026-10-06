package com.abridgefs.app

data class BridgeProjectTask(
    val id: String,
    var title: String,
    var completed: Boolean = false,
    var status: String = "PENDING",
    var continuationCount: Int = 0,
    var maxContinuations: Int = 10,
    var lastReceiptId: String? = null,
    var lastResult: String? = null
)

data class BridgeTaskExecutionState(
    var activeTaskId: String? = null,
    var status: String = "IDLE",
    var continuationCount: Int = 0,
    var maxContinuations: Int = 10,
    var lastReceiptId: String? = null,
    var lastResult: String? = null
)

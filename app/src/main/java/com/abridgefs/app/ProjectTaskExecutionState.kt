package com.abridgefs.app

/**
 * Runtime state for Project task execution/continuation.
 *
 * This is intentionally separate from BridgeProjectTask business data.
 */
data class BridgeTaskExecutionState(
    var activeTaskId: String? = null,
    var status: String = "IDLE",
    var continuationCount: Int = 0,
    var maxContinuations: Int = 10,
    var lastReceiptId: String? = null,
    var lastResult: String? = null
)

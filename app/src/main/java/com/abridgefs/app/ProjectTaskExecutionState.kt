package com.abridgefs.app

/**
 * Current Project task selection/lifecycle state.
 *
 * Execution receipts are conversation data; they do not drive task continuation.
 */
data class BridgeTaskExecutionState(
    var activeTaskId: String? = null,
    var status: String = "IDLE"
)

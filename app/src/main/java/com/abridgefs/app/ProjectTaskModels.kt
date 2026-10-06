package com.abridgefs.app

/**
 * Business task belonging to a Project.
 *
 * Execution/continuation runtime data is intentionally not stored here.
 */
data class BridgeProjectTask(
    val id: String,
    var title: String,
    var completed: Boolean = false,
    var status: String = "PENDING"
)

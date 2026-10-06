package com.abridgefs.app

data class BridgeChatMessage(
    val role: String,
    val content: String,
    val time: Long = System.currentTimeMillis(),
    val apiId: String? = null,
    val apiName: String? = null,
    val apiAvatar: String? = null
)

data class BridgeConversation(
    val id: String,
    var name: String,
    var apiId: String? = null,
    var localFileModifyOverride: Boolean? = null,
    val messages: MutableList<BridgeChatMessage> = mutableListOf(),
    val executions: MutableList<BridgeReceiptRecord> = mutableListOf()
)

package com.abridgefs.app.ai

data class AIRequest(
    val contextId: String?,
    val userText: String
) {
    init {
        require(contextId?.isNotBlank() != false) { "Context ID 不能为空" }
        require(userText.isNotBlank()) { "用户消息不能为空" }
    }
}

data class AIResponse(
    val text: String
)

interface AIConnector {
    suspend fun send(request: AIRequest): AIResponse
}

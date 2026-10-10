package com.abridgefs.app.ai

data class AIMessage(
    val role: Role,
    val content: String
) {
    enum class Role(val apiValue: String) {
        USER("user"),
        ASSISTANT("assistant")
    }
}

data class AIRequest(
    val contextId: String?,
    val userText: String,
    /** Prior turns in chronological order. The connector appends userText as the current turn. */
    val history: List<AIMessage> = emptyList()
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

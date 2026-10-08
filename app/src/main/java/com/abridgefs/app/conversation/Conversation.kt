package com.abridgefs.app.conversation

data class Conversation(
    val id: String,
    val contextId: String,
    val aiConnectionId: String,
    val messages: List<Message> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Conversation ID 不能为空" }
        require(contextId.isNotBlank()) { "Context ID 不能为空" }
        require(aiConnectionId.isNotBlank()) { "AI Connection ID 不能为空" }
    }

    fun addMessage(message: Message): Conversation =
        copy(messages = messages + message)
}

data class Message(
    val role: Role,
    val text: String
) {
    init {
        require(text.isNotBlank()) { "消息内容不能为空" }
    }

    enum class Role {
        USER,
        AI
    }
}

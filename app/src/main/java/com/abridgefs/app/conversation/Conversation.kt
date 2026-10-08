package com.abridgefs.app.conversation

data class Conversation(
    val id: String,
    val name: String = "新对话",
    val contextId: String? = null,
    val aiConnectionId: String,
    val groupId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val messages: List<Message> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Conversation ID 不能为空" }
        require(name.isNotBlank()) { "Conversation 名称不能为空" }
        require(contextId?.isNotBlank() != false) { "Context ID 不能为空" }
        require(aiConnectionId.isNotBlank()) { "AI Connection ID 不能为空" }
    }

    fun addMessage(
        message: Message,
        now: Long = System.currentTimeMillis()
    ): Conversation =
        copy(messages = messages + message, updatedAt = now)

    fun rename(newName: String, now: Long = System.currentTimeMillis()): Conversation {
        require(newName.isNotBlank()) { "Conversation 名称不能为空" }
        return copy(name = newName, updatedAt = now)
    }
}

data class Message(
    val role: Role,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(text.isNotBlank()) { "消息内容不能为空" }
    }

    enum class Role {
        USER,
        AI
    }
}

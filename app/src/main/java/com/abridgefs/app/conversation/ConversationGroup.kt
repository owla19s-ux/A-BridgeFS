package com.abridgefs.app.conversation

data class ConversationGroup(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
) {
    init {
        require(id.isNotBlank()) { "Conversation Group ID 不能为空" }
        require(name.isNotBlank()) { "Conversation Group 名称不能为空" }
    }

    fun rename(newName: String, now: Long = System.currentTimeMillis()): ConversationGroup {
        require(newName.isNotBlank()) { "Conversation Group 名称不能为空" }
        return copy(name = newName, updatedAt = now)
    }
}

package com.abridgefs.app.conversation

import java.util.UUID

class ConversationManager(
    private val store: ConversationStoreApi
) {
    fun createConversation(
        aiConnectionId: String,
        contextId: String? = null,
        name: String = "新对话",
        groupId: String? = null
    ): Conversation {
        require(groupId == null || store.getGroup(groupId) != null) {
            "目标对话分组不存在"
        }
        val now = System.currentTimeMillis()
        return Conversation(
            id = UUID.randomUUID().toString(),
            name = name,
            contextId = contextId,
            aiConnectionId = aiConnectionId,
            groupId = groupId,
            createdAt = now,
            updatedAt = now
        ).also(store::saveConversation)
    }

    fun renameConversation(id: String, name: String): Conversation {
        val current = requireConversation(id)
        val updated = current.rename(name)
        store.saveConversation(updated)
        return updated
    }

    fun deleteConversation(id: String) {
        store.deleteConversation(id)
    }

    fun createGroup(name: String): ConversationGroup {
        val now = System.currentTimeMillis()
        return ConversationGroup(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAt = now,
            updatedAt = now
        ).also(store::saveGroup)
    }

    fun renameGroup(id: String, name: String): ConversationGroup {
        val updated = requireGroup(id).rename(name)
        store.saveGroup(updated)
        return updated
    }

    fun deleteGroup(id: String) {
        store.deleteGroup(id)
    }

    fun moveConversation(id: String, groupId: String?) {
        store.moveConversation(id, groupId)
    }

    private fun requireConversation(id: String): Conversation =
        store.getConversation(id) ?: error("对话不存在: $id")

    private fun requireGroup(id: String): ConversationGroup =
        store.getGroup(id) ?: error("对话分组不存在: $id")
}

package com.abridgefs.app.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationManagerTest {
    @Test
    fun create_rename_move_and_delete_conversation() {
        val store = InMemoryConversationStore()
        val manager = ConversationManager(store)
        val group = manager.createGroup("开发")
        val conversation = manager.createConversation(
            aiConnectionId = "ai-1",
            groupId = group.id
        )

        assertEquals(group.id, store.getConversation(conversation.id)?.groupId)

        manager.renameConversation(conversation.id, "新的对话")
        assertEquals("新的对话", store.getConversation(conversation.id)?.name)

        manager.moveConversation(conversation.id, null)
        assertNull(store.getConversation(conversation.id)?.groupId)

        manager.deleteConversation(conversation.id)
        assertNull(store.getConversation(conversation.id))
    }

    @Test
    fun deleting_group_keeps_conversations_but_ungroups_them() {
        val store = InMemoryConversationStore()
        val manager = ConversationManager(store)
        val group = manager.createGroup("测试")
        val conversation = manager.createConversation(
            aiConnectionId = "ai-1",
            groupId = group.id
        )

        manager.deleteGroup(group.id)

        assertNull(store.getGroup(group.id))
        assertNull(store.getConversation(conversation.id)?.groupId)
        assertEquals(1, store.allConversations().size)
    }
}

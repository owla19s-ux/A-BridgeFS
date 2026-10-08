package com.abridgefs.app.conversation

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationTest {
    @Test
    fun conversation_binds_context_and_ai_connection() {
        val conversation = Conversation(
            id = "conversation-1",
            contextId = "context-1",
            aiConnectionId = "ai-1"
        ).addMessage(Message(Message.Role.USER, "读取仓库"))

        assertEquals("context-1", conversation.contextId)
        assertEquals("ai-1", conversation.aiConnectionId)
        assertEquals(Message.Role.USER, conversation.messages.single().role)
    }
}

package com.abridgefs.app.conversation

import com.abridgefs.app.ai.AIConnector
import com.abridgefs.app.ai.AIRequest
import com.abridgefs.app.ai.AIResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationServiceTest {
    @Test
    fun send_passes_context_to_ai_and_appends_user_and_ai_messages() = runBlocking {
        var received: AIRequest? = null
        val connector = object : AIConnector {
            override suspend fun send(request: AIRequest): AIResponse {
                received = request
                return AIResponse("已读取 Context")
            }
        }

        val conversation = Conversation(
            id = "conversation-1",
            contextId = "context-1",
            aiConnectionId = "ai-1"
        )

        val updated = ConversationService(connector).send(conversation, "读取当前 Context")

        assertEquals("context-1", received?.contextId)
        assertEquals("读取当前 Context", received?.userText)
        assertEquals(
            listOf(Message.Role.USER, Message.Role.AI),
            updated.messages.map { it.role }
        )
        assertEquals("读取当前 Context", updated.messages[0].text)
        assertEquals("已读取 Context", updated.messages[1].text)
    }

    @Test
    fun send_allows_conversation_without_context() = runBlocking {
        var received: AIRequest? = null
        val connector = object : AIConnector {
            override suspend fun send(request: AIRequest): AIResponse {
                received = request
                return AIResponse("独立对话响应")
            }
        }

        val conversation = Conversation(
            id = "conversation-independent",
            aiConnectionId = "ai-1"
        )

        val updated = ConversationService(connector).send(conversation, "直接提问")

        assertEquals(null, received?.contextId)
        assertEquals("直接提问", received?.userText)
        assertEquals("独立对话响应", updated.messages.last().text)
    }

    @Test
    fun send_and_save_persists_successful_response() = runBlocking {
        val connector = object : AIConnector {
            override suspend fun send(request: AIRequest): AIResponse =
                AIResponse("已保存")
        }
        val store = InMemoryConversationStore()
        val conversation = Conversation(
            id = "conversation-save",
            aiConnectionId = "ai-1"
        )

        val updated = ConversationService(connector).sendAndSave(
            conversation,
            "保存这次对话",
            store
        )

        assertEquals(updated, store.getConversation(conversation.id))
        assertEquals("已保存", store.getConversation(conversation.id)?.messages?.last()?.text)
    }

    @Test
    fun send_keeps_existing_messages() = runBlocking {
        val connector = object : AIConnector {
            override suspend fun send(request: AIRequest): AIResponse =
                AIResponse("继续处理")
        }

        val conversation = Conversation(
            id = "conversation-2",
            contextId = "context-2",
            aiConnectionId = "ai-2"
        ).addMessage(Message(Message.Role.USER, "第一条"))

        val updated = ConversationService(connector).send(conversation, "第二条")

        assertEquals(3, updated.messages.size)
        assertEquals("第一条", updated.messages[0].text)
        assertEquals("第二条", updated.messages[1].text)
        assertEquals("继续处理", updated.messages[2].text)
        assertTrue(updated !== conversation)
    }

    @Test
    fun send_and_save_preserves_user_message_when_connector_fails() = runBlocking {
        val connector = object : AIConnector {
            override suspend fun send(request: AIRequest): AIResponse {
                throw IllegalStateException("网络不可用")
            }
        }
        val store = InMemoryConversationStore()
        val conversation = Conversation(
            id = "conversation-failure",
            aiConnectionId = "ai-1"
        )

        try {
            ConversationService(connector).sendAndSave(
                conversation,
                "这条消息不能丢",
                store
            )
            throw AssertionError("预期请求失败")
        } catch (error: IllegalStateException) {
            assertEquals("网络不可用", error.message)
        }

        val saved = store.getConversation(conversation.id)
        assertEquals(1, saved?.messages?.size)
        assertEquals(Message.Role.USER, saved?.messages?.single()?.role)
        assertEquals("这条消息不能丢", saved?.messages?.single()?.text)
    }
}

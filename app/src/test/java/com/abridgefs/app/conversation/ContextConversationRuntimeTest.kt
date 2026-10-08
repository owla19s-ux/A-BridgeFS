package com.abridgefs.app.conversation

import com.abridgefs.app.ai.AIConnection
import com.abridgefs.app.ai.AIConnector
import com.abridgefs.app.ai.AIConnectorRegistry
import com.abridgefs.app.ai.AIRequest
import com.abridgefs.app.ai.AIResponse
import com.abridgefs.app.context.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextConversationRuntimeTest {
    @Test
    fun openConversation_creates_context_bound_conversation() {
        val connection = AIConnection("ai-1", "Test AI")
        val context = Context("context-1", "Test Context", aiConnectionId = connection.id)

        val conversation = ContextConversationRuntime(
            connections = mapOf(connection.id to connection),
            connectorRegistry = AIConnectorRegistry(emptyMap())
        ).openConversation(context, "conversation-1")

        assertEquals("conversation-1", conversation.id)
        assertEquals(context.id, conversation.contextId)
        assertEquals(connection.id, conversation.aiConnectionId)
        assertEquals(emptyList<Message>(), conversation.messages)
    }

    @Test
    fun serviceFor_resolves_context_ai_connection_to_connector() = runBlocking {
        val connection = AIConnection("ai-1", "Test AI")
        val connector = object : AIConnector {
            override suspend fun send(request: AIRequest): AIResponse =
                AIResponse("已处理: " + request.userText)
        }
        val context = Context("context-1", "Test Context", aiConnectionId = connection.id)
        val runtime = ContextConversationRuntime(
            connections = mapOf(connection.id to connection),
            connectorRegistry = AIConnectorRegistry(mapOf(connection.id to connector))
        )
        val conversation = runtime.openConversation(context, "conversation-1")

        val updated = runtime.serviceFor(context, conversation)
            .send(conversation, "读取 Context")

        assertEquals("已处理: 读取 Context", updated.messages.last().text)
    }

    @Test(expected = IllegalStateException::class)
    fun openConversation_rejects_context_without_ai_connection() {
        val context = Context("context-1", "Test Context")

        ContextConversationRuntime(emptyMap(), AIConnectorRegistry(emptyMap()))
            .openConversation(context, "conversation-1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun serviceFor_rejects_conversation_from_other_context() {
        val connection = AIConnection("ai-1", "Test AI")
        val context = Context("context-1", "Test Context", aiConnectionId = connection.id)
        val conversation = Conversation("conversation-2", "context-2", connection.id)

        ContextConversationRuntime(
            mapOf(connection.id to connection),
            AIConnectorRegistry(emptyMap())
        ).serviceFor(context, conversation)
    }
}

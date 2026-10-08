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
    fun serviceFor_resolves_context_ai_connection_to_connector() = runBlocking {
        val connection = AIConnection("ai-1", "Test AI")
        val connector = object : AIConnector {
            override suspend fun send(request: AIRequest): AIResponse =
                AIResponse("已处理: " + request.userText)
        }
        val context = Context("context-1", "Test Context", aiConnectionId = connection.id)
        val conversation = Conversation("conversation-1", context.id, connection.id)

        val service = ContextConversationRuntime(
            connections = mapOf(connection.id to connection),
            connectorRegistry = AIConnectorRegistry(mapOf(connection.id to connector))
        ).serviceFor(context, conversation)

        val updated = service.send(conversation, "读取 Context")

        assertEquals("已处理: 读取 Context", updated.messages.last().text)
    }

    @Test(expected = IllegalStateException::class)
    fun serviceFor_rejects_context_without_ai_connection() {
        val context = Context("context-1", "Test Context")
        val conversation = Conversation("conversation-1", context.id, "ai-1")

        ContextConversationRuntime(emptyMap(), AIConnectorRegistry(emptyMap()))
            .serviceFor(context, conversation)
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

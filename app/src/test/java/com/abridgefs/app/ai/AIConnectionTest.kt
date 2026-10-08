package com.abridgefs.app.ai

import com.abridgefs.app.connection.Connection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AIConnectionTest {
    @Test
    fun ai_connection_has_stable_identity_and_type() {
        val connection = AIConnection("ai-test", "Test AI")

        assertEquals("ai-test", connection.id)
        assertEquals(Connection.Type.AI, connection.type)
        assertTrue(connection is Connection)
    }

    @Test
    fun fake_connector_can_return_response() = runBlocking {
        val connector = object : AIConnector {
            override suspend fun send(request: AIRequest): AIResponse =
                AIResponse("已收到：" + request.userText)
        }

        val response = connector.send(AIRequest("ctx-1", "读取当前 Context"))

        assertEquals("已收到：读取当前 Context", response.text)
    }
}

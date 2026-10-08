package com.abridgefs.app.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Test

class AIConnectorRegistryTest {
    @Test
    fun resolve_returns_connector_for_connection_id() = runBlocking {
        val connector = object : AIConnector {
            override suspend fun send(request: AIRequest): AIResponse = AIResponse(request.userText)
        }
        val connection = AIConnection("ai:test", "Test AI")

        val resolved = AIConnectorRegistry(mapOf(connection.id to connector)).resolve(connection)

        assertSame(connector, resolved)
    }

    @Test(expected = IllegalStateException::class)
    fun resolve_rejects_unregistered_connection() {
        AIConnectorRegistry(emptyMap()).resolve(AIConnection("ai:missing", "Missing AI"))
    }
}

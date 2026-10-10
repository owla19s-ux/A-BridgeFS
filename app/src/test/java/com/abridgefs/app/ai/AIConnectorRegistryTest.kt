package com.abridgefs.app.ai

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AIConnectorRegistryTest {
    @Test
    fun resolve_returns_connector_for_connection_id() {
        val connector = object : AIConnector {
            override suspend fun send(request: AIRequest): AIResponse = AIResponse(request.userText)
        }
        val connection = AIConnection("ai:test", "Test AI")

        val resolved = AIConnectorRegistry(mapOf(connection.id to connector)).resolve(connection)

        assertSame(connector, resolved)
    }

    @Test
    fun resolve_builds_connector_from_profile_provider() {
        val profile = AIProfile(
            id = "profile-1",
            name = "Test profile",
            baseUrl = "https://api.example.com/v1",
            model = "model-x",
            apiKey = "secret"
        )
        val registry = AIConnectorRegistry(profileProvider = { id -> profile.takeIf { it.id == id } })
        val connection = AIConnection(profile.id, profile.name)

        val resolved = registry.resolve(connection)

        assertTrue(resolved is OpenAICompatibleConnector)
    }

    @Test(expected = IllegalStateException::class)
    fun resolve_rejects_unregistered_connection() {
        AIConnectorRegistry().resolve(AIConnection("ai:missing", "Missing AI"))
    }
}

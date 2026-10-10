package com.abridgefs.app.ai

/**
 * Builds runtime connectors from persisted API profiles.
 * Provider-specific construction stays out of UI activities.
 * This first version supports OpenAI-compatible endpoints.
 */
class AIConnectorFactory {
    fun create(profile: AIProfile): AIConnector = OpenAICompatibleConnector(profile)

    fun connection(profile: AIProfile): AIConnection =
        AIConnection(id = profile.id, name = profile.name)
}

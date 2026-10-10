package com.abridgefs.app.ai

/**
 * Resolves runtime connectors by stable AI connection ID.
 * Supports injected connectors and lazy construction from persisted profiles.
 */
class AIConnectorRegistry(
    connectors: Map<String, AIConnector> = emptyMap(),
    private val profileProvider: ((String) -> AIProfile?)? = null,
    private val factory: AIConnectorFactory = AIConnectorFactory()
) {
    private val connectorsById = connectors.toMap()

    fun resolve(connection: AIConnection): AIConnector =
        connectorsById[connection.id]
            ?: profileProvider?.invoke(connection.id)?.let(factory::create)
            ?: error("未找到 AI Connection 对应的 Connector: " + connection.id)

    fun connection(profile: AIProfile): AIConnection = factory.connection(profile)
}

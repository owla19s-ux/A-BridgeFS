package com.abridgefs.app.ai

/**
 * Context Conversation 使用的最小 AI Connector 运行时解析边界。
 *
 * Registry 只负责根据 AI Connection ID 找到已经注册的 Connector。
 * 不负责 Provider、API Profile 或具体网络实现。
 */
class AIConnectorRegistry(
    connectors: Map<String, AIConnector>
) {
    private val connectorsById = connectors.toMap()

    fun resolve(connection: AIConnection): AIConnector =
        connectorsById[connection.id]
            ?: error("未找到 AI Connection 对应的 Connector: " + connection.id)
}

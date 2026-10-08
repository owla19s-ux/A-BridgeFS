package com.abridgefs.app.conversation

import com.abridgefs.app.ai.AIConnection
import com.abridgefs.app.ai.AIConnectorRegistry
import com.abridgefs.app.context.Context

/**
 * Context Conversation 的最小运行时组装边界。
 *
 * 负责把 Context 的 AI Connection ID 解析为 AI Connection，
 * 再解析为 AI Connector，最后创建 ConversationService。
 * 不负责 Provider、API Profile 或持久化。
 */
class ContextConversationRuntime(
    private val connections: Map<String, AIConnection>,
    private val connectorRegistry: AIConnectorRegistry
) {
    fun serviceFor(context: Context, conversation: Conversation): ConversationService {
        require(conversation.contextId == context.id) {
            "Conversation 不属于当前 Context"
        }

        val connectionId = context.aiConnectionId
            ?: error("Context 未绑定 AI Connection")

        require(conversation.aiConnectionId == connectionId) {
            "Conversation 的 AI Connection 与 Context 不一致"
        }

        val connection = connections[connectionId]
            ?: error("未找到 Context 对应的 AI Connection: " + connectionId)

        return ConversationService(connectorRegistry.resolve(connection))
    }
}

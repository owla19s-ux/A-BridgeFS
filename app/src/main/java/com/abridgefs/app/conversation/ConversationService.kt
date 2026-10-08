package com.abridgefs.app.conversation

import com.abridgefs.app.ai.AIConnector
import com.abridgefs.app.ai.AIRequest

/**
 * Context Conversation 的最小运行边界。
 *
 * ConversationService 不负责持久化、Dispatcher 或具体 AI Provider。
 * 它只把用户消息交给已绑定的 AI Connector，并将结果追加回 Conversation。
 */
class ConversationService(
    private val connector: AIConnector
) {
    suspend fun send(
        conversation: Conversation,
        userText: String
    ): Conversation {
        val userMessage = Message(Message.Role.USER, userText)
        val response = connector.send(
            AIRequest(
                contextId = conversation.contextId,
                userText = userText
            )
        )

        return conversation
            .addMessage(userMessage)
            .addMessage(Message(Message.Role.AI, response.text))
    }
}

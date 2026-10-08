package com.abridgefs.app.conversation

import com.abridgefs.app.ai.AIConnector
import com.abridgefs.app.ai.AIRequest

/**
 * Conversation 的最小运行边界。
 *
 * ConversationService 不负责持久化、Dispatcher 或具体 AI Provider。
 * Conversation 可以独立存在，也可以关联 Context；关联信息原样传给 AI Connector。
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

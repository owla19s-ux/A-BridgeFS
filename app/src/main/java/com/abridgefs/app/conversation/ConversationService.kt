package com.abridgefs.app.conversation

import com.abridgefs.app.ai.AIConnector
import com.abridgefs.app.ai.AIRequest

/**
 * Conversation 的运行边界。
 *
 * 普通对话页使用 sendAndSave()，每次成功响应后立即保存。
 * Context Conversation 可以继续使用 send()，由其上层决定记录边界。
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

    suspend fun sendAndSave(
        conversation: Conversation,
        userText: String,
        store: ConversationStoreApi
    ): Conversation {
        val updated = send(conversation, userText)
        store.saveConversation(updated)
        return updated
    }
}

package com.abridgefs.app.conversation

import com.abridgefs.app.ai.AIConnector
import com.abridgefs.app.ai.AIRequest
import com.abridgefs.app.ai.AIResponse

/**
 * Conversation 的运行边界。
 *
 * 普通对话页先持久化用户消息，再调用 AI；即使请求失败或被中断，
 * 用户已提交的内容也不会只留在内存中。
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
        val withUserMessage = conversation.addMessage(Message(Message.Role.USER, userText))
        store.saveConversation(withUserMessage)

        val response = connector.send(
            AIRequest(
                contextId = conversation.contextId,
                userText = userText
            )
        )
        val completed = withUserMessage.addMessage(Message(Message.Role.AI, response.text))
        store.saveConversation(completed)
        return completed
    }
}

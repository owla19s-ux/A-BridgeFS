package com.abridgefs.app.conversation

import com.abridgefs.app.RuntimeDiagnostics
import com.abridgefs.app.ai.AIConnector
import com.abridgefs.app.ai.AIMessage
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
                userText = userText,
                modelId = conversation.aiModelId,
                history = conversation.messages.map { message ->
                    AIMessage(
                        role = if (message.role == Message.Role.USER) AIMessage.Role.USER else AIMessage.Role.ASSISTANT,
                        content = message.text
                    )
                }
            )
        )

        return conversation
            .addMessage(userMessage)
            .addMessage(Message(Message.Role.AI, response.text))
    }

    suspend fun sendAndSave(
        conversation: Conversation,
        userText: String,
        store: ConversationStoreApi,
        onDelta: (String) -> Unit = {},
        additionalContext: String? = null
    ): Conversation {
        val startedAt = System.nanoTime()
        RuntimeDiagnostics.record("conversation.send", "started")
        try {
            val withUserMessage = conversation.addMessage(Message(Message.Role.USER, userText))
            store.saveConversation(withUserMessage)
            RuntimeDiagnostics.record("conversation.user_message.save", "success")

            val response = connector.sendStreaming(
                AIRequest(
                    contextId = conversation.contextId,
                    userText = additionalContext
                        ?.takeIf { it.isNotBlank() }
                        ?.let { "$it\n\n用户问题：$userText" }
                        ?: userText,
                    modelId = conversation.aiModelId,
                    history = conversation.messages.map { message ->
                        AIMessage(
                            role = if (message.role == Message.Role.USER) AIMessage.Role.USER else AIMessage.Role.ASSISTANT,
                            content = message.text
                        )
                    }
                ),
                onDelta = onDelta
            )
            val completed = withUserMessage.addMessage(Message(Message.Role.AI, response.text))
            store.saveConversation(completed)
            RuntimeDiagnostics.record(
                "conversation.send", "success",
                durationMs = (System.nanoTime() - startedAt) / 1_000_000
            )
            return completed
        } catch (error: Exception) {
            RuntimeDiagnostics.record(
                "conversation.send", "failed",
                durationMs = (System.nanoTime() - startedAt) / 1_000_000,
                errorType = error.javaClass.simpleName
            )
            throw error
        }
    }
}

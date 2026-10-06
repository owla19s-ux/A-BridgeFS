package com.abridgefs.app

import android.content.Context

/**
 * Persists execution receipts back into their owning conversation.
 *
 * The execution engine only produces a result; this service owns the
 * Project/Conversation persistence side of that result.
 */
class ProjectReceiptService(private val context: Context) {
    private val projectStore = BridgeProjectStore(context)
    private val standaloneStore = BridgeConversationStore(context)

    fun record(
        receiptId: String,
        status: String,
        command: String,
        message: String,
        time: Long,
        projectId: String?,
        conversationId: String?,
        standaloneConversationId: String?
    ) {
        if (!projectId.isNullOrBlank()) {
            val projects = projectStore.load()
            val project = projects.firstOrNull { it.id == projectId } ?: return
            val conversation = conversationId?.let { id ->
                project.conversations.firstOrNull { it.id == id }
            } ?: project.activeConversation()

            if (conversation.executions.none { it.receiptId == receiptId }) {
                conversation.executions += BridgeReceiptRecord(
                    status = status,
                    command = command,
                    message = message,
                    time = time,
                    receiptId = receiptId
                )
                projectStore.save(projects)
            }
            return
        }

        if (!standaloneConversationId.isNullOrBlank()) {
            val conversations = standaloneStore.load()
            val conversation = conversations.firstOrNull { it.id == standaloneConversationId } ?: return
            if (conversation.executions.none { it.receiptId == receiptId }) {
                conversation.executions += BridgeReceiptRecord(
                    status = status,
                    command = command,
                    message = message,
                    time = time,
                    receiptId = receiptId
                )
                standaloneStore.save(conversations)
            }
        }
    }
}

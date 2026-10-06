package com.abridgefs.app

import android.content.Context

/**
 * Owns Project-level active conversation selection.
 */
class ProjectConversationSelectionService(private val context: Context) {
    private val store = BridgeProjectStore(context)

    fun select(projectId: String, conversationId: String): BridgeProject {
        val project = store.load().firstOrNull { it.id == projectId }
            ?: error("Project 不存在：$projectId")
        require(project.conversations.any { it.id == conversationId }) { "对话不存在：$conversationId" }
        project.activeConversationId = conversationId
        store.saveProject(project)
        return project
    }
}

package com.abridgefs.app

import java.util.UUID

data class BridgeChatMessage(
    val role: String,
    val content: String,
    val time: Long = System.currentTimeMillis(),
    val apiId: String? = null,
    val apiName: String? = null,
    val apiAvatar: String? = null
)

data class BridgeReceiptRecord(
    val status: String,
    val command: String,
    val message: String,
    val time: Long = System.currentTimeMillis(),
    val receiptId: String = UUID.randomUUID().toString()
)

data class BridgeAiMember(
    val id: String,
    var name: String,
    var apiProfileId: String? = null
)

/**
 * A conversation belongs to a Project.
 * API selection, chat history and execution receipts are conversation state.
 */
data class BridgeConversation(
    val id: String,
    var name: String,
    var apiId: String? = null,
    var localFileModifyOverride: Boolean? = null,
    val messages: MutableList<BridgeChatMessage> = mutableListOf(),
    val executions: MutableList<BridgeReceiptRecord> = mutableListOf()
)

/**
 * Project root.
 *
 * The BridgeProject type name is retained temporarily for source/storage
 * compatibility while the application moves from the historical Workspace
 * model to the Project model.
 */
data class BridgeProject(
    val id: String,
    var name: String,
    var localFileModifyEnabled: Boolean = false,
    /** Local Project Address. Kept under the historical storage key for migration compatibility. */
    var workspaceDirectory: String? = null,
    /** GitHub Project Address. GitHub is one address type, not the Project itself. */
    var github: GitHubWorkspace = GitHubWorkspace(),
    /** Default Project Member. Null means the project has no default AI yet. */
    var defaultMemberId: String? = null,
    val aiMembers: MutableList<BridgeAiMember> = mutableListOf(),
    val conversations: MutableList<BridgeConversation> = mutableListOf(),
    var activeConversationId: String? = null
) {
    fun activeConversation(): BridgeConversation {
        val current = activeConversationId?.let { id -> conversations.firstOrNull { it.id == id } }
        if (current != null) return current
        val created = BridgeConversation(UUID.randomUUID().toString(), "默认对话")
        conversations += created
        activeConversationId = created.id
        return created
    }

    @Deprecated("Use activeConversation().apiId")
    var apiId: String?
        get() = activeConversation().apiId
        set(value) { activeConversation().apiId = value }

    @Deprecated("Use activeConversation().messages")
    val messages: MutableList<BridgeChatMessage>
        get() = activeConversation().messages

    @Deprecated("Use activeConversation().executions")
    val executions: MutableList<BridgeReceiptRecord>
        get() = activeConversation().executions

    @Deprecated("Use github.accountLogin")
    var githubAccountLogin: String?
        get() = github.accountLogin
        set(value) { github.accountLogin = value }

    @Deprecated("Use github.repository")
    var githubRepository: String?
        get() = github.repository
        set(value) { github.repository = value }

    @Deprecated("Use github.branch")
    var githubBranch: String?
        get() = github.branch
        set(value) { github.branch = value }

    @Deprecated("Use github.readEnabled")
    var githubReadEnabled: Boolean
        get() = github.readEnabled
        set(value) { github.readEnabled = value }

    @Deprecated("Use github.writeEnabled")
    var githubWriteEnabled: Boolean
        get() = github.writeEnabled
        set(value) { github.writeEnabled = value }
}

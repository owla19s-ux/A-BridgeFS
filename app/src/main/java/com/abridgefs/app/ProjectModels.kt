package com.abridgefs.app

import java.util.UUID

data class BridgeProject(
    val id: String,
    var name: String,
    var localFileModifyEnabled: Boolean = false,
    var localAddress: String? = null,
    var githubAddress: ProjectGitHubAddress = ProjectGitHubAddress(),
    var defaultMemberId: String? = null,
    val aiMembers: MutableList<BridgeAiMember> = mutableListOf(),
    val conversations: MutableList<BridgeConversation> = mutableListOf(),
    val verifyRecords: MutableList<BridgeVerifyRecord> = mutableListOf(),
    val tasks: MutableList<BridgeProjectTask> = mutableListOf(),
    var activeConversationId: String? = null,
    var taskState: BridgeTaskExecutionState = BridgeTaskExecutionState()
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

    @Deprecated("Use localAddress")
    var workspaceDirectory: String?
        get() = localAddress
        set(value) { localAddress = value }

    @Deprecated("Use githubAddress")
    var github: ProjectGitHubAddress
        get() = githubAddress
        set(value) { githubAddress = value }

    @Deprecated("Use githubAddress.accountLogin")
    var githubAccountLogin: String?
        get() = githubAddress.accountLogin
        set(value) { githubAddress.accountLogin = value }

    @Deprecated("Use githubAddress.repository")
    var githubRepository: String?
        get() = githubAddress.repository
        set(value) { githubAddress.repository = value }

    @Deprecated("Use githubAddress.branch")
    var githubBranch: String?
        get() = githubAddress.branch
        set(value) { githubAddress.branch = value }

    @Deprecated("Use githubAddress.readEnabled")
    var githubReadEnabled: Boolean
        get() = githubAddress.readEnabled
        set(value) { githubAddress.readEnabled = value }

    @Deprecated("Use githubAddress.writeEnabled")
    var githubWriteEnabled: Boolean
        get() = githubAddress.writeEnabled
        set(value) { githubAddress.writeEnabled = value }
}

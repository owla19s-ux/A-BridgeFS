package com.abridgefs.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class BridgeChatMessage(
    val role: String,
    val content: String,
    val time: Long = System.currentTimeMillis(),
    val apiId: String? = null,
    val apiName: String? = null,
    val apiAvatar: String? = null
)
data class BridgeReceiptRecord(val status: String, val command: String, val message: String, val time: Long = System.currentTimeMillis(), val receiptId: String = UUID.randomUUID().toString())

data class BridgeAiMember(
    val id: String,
    var name: String,
    var apiProfileId: String? = null
)

/**
 * A conversation belongs to a workspace.
 *
 * API selection, chat history and execution receipts are conversation state.
 * Workspace-level resources (GitHub, local permission) stay on BridgeProject.
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
 * Workspace root.
 *
 * Kept under the historical BridgeProject type name for source compatibility with
 * MainActivity/GitHubActivity while the storage model is migrated to the explicit
 * Workspace -> Conversations structure.
 */
data class BridgeProject(
    val id: String,
    var name: String,
    var localFileModifyEnabled: Boolean = false,
    var workspaceDirectory: String? = null,
    var github: GitHubWorkspace = GitHubWorkspace(),
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

    /**
     * Compatibility facade for the current UI. New code should address the
     * workspace and conversation separately.
     */
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

class BridgeProjectStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("bridgefs_projects", Context.MODE_PRIVATE)
    private val key = "data"

    fun load(): MutableList<BridgeProject> {
        val array = JSONArray(prefs.getString(key, "[]") ?: "[]")
        val result = mutableListOf<BridgeProject>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val project = BridgeProject(
                id = obj.getString("id"),
                name = obj.getString("name"),
                localFileModifyEnabled = obj.optBoolean("localFileModifyEnabled", false),
                workspaceDirectory = obj.optString("workspaceDirectory", "").ifBlank { null },
                github = GitHubWorkspace(
                    accountLogin = obj.optString("githubAccount", "").ifBlank {
                        obj.optString("githubAccountLogin", "").ifBlank { null }
                    },
                    repositoryId = obj.optString("githubRepositoryId", "").toLongOrNull(),
                    repository = obj.optString("githubRepository", "").ifBlank { null },
                    branch = obj.optString("githubBranch", "").ifBlank { null },
                    readEnabled = obj.optBoolean("githubReadEnabled", true),
                    writeEnabled = obj.optBoolean("githubWriteEnabled", false)
                ),
                activeConversationId = obj.optString("activeConversationId", "").ifBlank { null }
            )

            val aiMembers = obj.optJSONArray("aiMembers")
            if (aiMembers != null) {
                for (j in 0 until aiMembers.length()) {
                    val member = aiMembers.getJSONObject(j)
                    project.aiMembers += BridgeAiMember(
                        id = member.getString("id"),
                        name = member.optString("name", "AI"),
                        apiProfileId = member.optString("apiProfileId", "").ifBlank { null }
                    )
                }
            }

            // New format: conversations live inside the workspace.
            val conversations = obj.optJSONArray("conversations")
            if (conversations != null) {
                for (j in 0 until conversations.length()) {
                    project.conversations += readConversation(conversations.getJSONObject(j))
                }
            }

            // One-time compatibility migration from the previous flat project format.
            if (project.conversations.isEmpty()) {
                val legacy = BridgeConversation(
                    id = UUID.randomUUID().toString(),
                    name = "默认对话",
                    apiId = obj.optString("apiId", "").ifBlank { null },
                    localFileModifyOverride = null
                )
                readMessages(obj.optJSONArray("messages"), legacy.messages)
                readExecutions(obj.optJSONArray("executions"), legacy.executions)
                project.conversations += legacy
                project.activeConversationId = legacy.id
            }

            if (project.aiMembers.isEmpty()) {
                project.aiMembers += BridgeAiMember(UUID.randomUUID().toString(), "AI A")
                project.aiMembers += BridgeAiMember(UUID.randomUUID().toString(), "AI B")
            }
            project.activeConversation()
            result += project
        }
        return result
    }

    fun save(projects: List<BridgeProject>) {
        val array = JSONArray()
        projects.forEach { project ->
            project.activeConversation()
            val obj = JSONObject()
                .put("id", project.id)
                .put("name", project.name)
                .put("localFileModifyEnabled", project.localFileModifyEnabled)
                .put("workspaceDirectory", project.workspaceDirectory.orEmpty())
                .put("activeConversationId", project.activeConversationId.orEmpty())
                .put("githubAccount", project.github.accountLogin.orEmpty())
                .put("githubRepositoryId", project.github.repositoryId?.toString().orEmpty())
                .put("githubRepository", project.github.repository.orEmpty())
                .put("githubBranch", project.github.branch.orEmpty())
                .put("githubReadEnabled", project.github.readEnabled)
                .put("githubWriteEnabled", project.github.writeEnabled)

            obj.put("aiMembers", JSONArray().apply {
                project.aiMembers.forEach {
                    put(JSONObject()
                        .put("id", it.id)
                        .put("name", it.name)
                        .put("apiProfileId", it.apiProfileId.orEmpty()))
                }
            })

            obj.put("conversations", JSONArray().apply {
                project.conversations.forEach { conversation ->
                    put(JSONObject()
                        .put("id", conversation.id)
                        .put("name", conversation.name)
                        .put("apiId", conversation.apiId.orEmpty())
                        .put("localFileModifyOverride", conversation.localFileModifyOverride)
                        .put("messages", JSONArray().apply {
                            conversation.messages.forEach {
                                put(JSONObject().put("role", it.role).put("content", it.content).put("time", it.time)
                                .put("apiId", it.apiId.orEmpty()).put("apiName", it.apiName.orEmpty()).put("apiAvatar", it.apiAvatar.orEmpty()))
                            }
                        })
                        .put("executions", JSONArray().apply {
                            conversation.executions.forEach {
                                put(JSONObject().put("status", it.status).put("command", it.command).put("message", it.message).put("time", it.time).put("receiptId", it.receiptId))
                            }
                        })
                    )
                }
            })
            array.put(obj)
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    fun newProject(name: String): BridgeProject {
        val workspace = BridgeProject(UUID.randomUUID().toString(), name)
        workspace.aiMembers += BridgeAiMember(UUID.randomUUID().toString(), "AI A")
        workspace.aiMembers += BridgeAiMember(UUID.randomUUID().toString(), "AI B")
        val conversation = BridgeConversation(UUID.randomUUID().toString(), "默认对话")
        workspace.conversations += conversation
        workspace.activeConversationId = conversation.id
        return workspace
    }

    private fun readConversation(obj: JSONObject): BridgeConversation {
        val conversation = BridgeConversation(
            id = obj.getString("id"),
            name = obj.optString("name", "未命名对话"),
            apiId = obj.optString("apiId", "").ifBlank { null },
            localFileModifyOverride = if (obj.has("localFileModifyOverride") && !obj.isNull("localFileModifyOverride")) {
                obj.optBoolean("localFileModifyOverride")
            } else {
                null
            }
        )
        readMessages(obj.optJSONArray("messages"), conversation.messages)
        readExecutions(obj.optJSONArray("executions"), conversation.executions)
        return conversation
    }

    private fun readMessages(array: JSONArray?, target: MutableList<BridgeChatMessage>) {
        if (array == null) return
        for (j in 0 until array.length()) {
            val item = array.getJSONObject(j)
            target += BridgeChatMessage(
                item.getString("role"),
                item.getString("content"),
                item.optLong("time", System.currentTimeMillis()),
                item.optString("apiId", "").ifBlank { null },
                item.optString("apiName", "").ifBlank { null },
                item.optString("apiAvatar", "").ifBlank { null }
            )
        }
    }

    private fun readExecutions(array: JSONArray?, target: MutableList<BridgeReceiptRecord>) {
        if (array == null) return
        for (j in 0 until array.length()) {
            val item = array.getJSONObject(j)
            target += BridgeReceiptRecord(
                item.getString("status"),
                item.getString("command"),
                item.getString("message"),
                item.optLong("time", System.currentTimeMillis()),
                item.optString("receiptId", "").ifBlank { UUID.randomUUID().toString() }
            )
        }
    }
}

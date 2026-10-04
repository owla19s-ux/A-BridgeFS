package com.abridgefs.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persistence boundary for Project state.
 *
 * Models live in ProjectModels.kt. This store only serializes and restores them.
 */
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
                localAddress = obj.optString("localAddress", "").ifBlank {
                    obj.optString("workspaceDirectory", "")
                }.takeIf { it.isNotBlank() },
                githubAddress = ProjectGitHubAddress(
                    accountLogin = obj.optString("githubAccount", "").ifBlank {
                        obj.optString("githubAccountLogin", "").ifBlank { null }
                    },
                    repositoryId = obj.optString("githubRepositoryId", "").toLongOrNull(),
                    repository = obj.optString("githubRepository", "").ifBlank { null },
                    branch = obj.optString("githubBranch", "").ifBlank { null },
                    readEnabled = obj.optBoolean("githubReadEnabled", true),
                    writeEnabled = obj.optBoolean("githubWriteEnabled", false)
                ),
                activeConversationId = obj.optString("activeConversationId", "").ifBlank { null },
                defaultMemberId = obj.optString("defaultMemberId", "").ifBlank { null }
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

            val conversations = obj.optJSONArray("conversations")
            if (conversations != null) {
                for (j in 0 until conversations.length()) {
                    project.conversations += readConversation(conversations.getJSONObject(j))
                }
            }

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

            if (project.defaultMemberId == null) {
                val legacyApiId = project.conversations.firstOrNull()?.apiId
                project.defaultMemberId = legacyApiId?.let { apiId ->
                    project.aiMembers.firstOrNull { it.apiProfileId == apiId }?.id
                } ?: project.aiMembers.firstOrNull()?.id
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
                .put("localAddress", project.localAddress.orEmpty())
                .put("activeConversationId", project.activeConversationId.orEmpty())
                .put("defaultMemberId", project.defaultMemberId.orEmpty())
                .put("githubAccount", project.githubAddress.accountLogin.orEmpty())
                .put("githubRepositoryId", project.githubAddress.repositoryId?.toString().orEmpty())
                .put("githubRepository", project.githubAddress.repository.orEmpty())
                .put("githubBranch", project.githubAddress.branch.orEmpty())
                .put("githubReadEnabled", project.githubAddress.readEnabled)
                .put("githubWriteEnabled", project.githubAddress.writeEnabled)

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
                                put(JSONObject()
                                    .put("role", it.role)
                                    .put("content", it.content)
                                    .put("time", it.time)
                                    .put("apiId", it.apiId.orEmpty())
                                    .put("apiName", it.apiName.orEmpty())
                                    .put("apiAvatar", it.apiAvatar.orEmpty()))
                            }
                        })
                        .put("executions", JSONArray().apply {
                            conversation.executions.forEach {
                                put(JSONObject()
                                    .put("status", it.status)
                                    .put("command", it.command)
                                    .put("message", it.message)
                                    .put("time", it.time)
                                    .put("receiptId", it.receiptId))
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
        val project = BridgeProject(UUID.randomUUID().toString(), name)
        val conversation = BridgeConversation(UUID.randomUUID().toString(), "项目对话")
        project.conversations += conversation
        project.activeConversationId = conversation.id
        return project
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

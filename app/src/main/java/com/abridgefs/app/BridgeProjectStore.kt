package com.abridgefs.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class BridgeProjectStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("bridgefs_projects", Context.MODE_PRIVATE)
    private val key = "data"

    fun load(): MutableList<BridgeProject> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse {
            AppLogger.log(context, "PROJECT_STORE_LOAD_FAILED", "invalid project JSON: " + (it.message ?: "unknown"))
            return mutableListOf()
        }
        val result = mutableListOf<BridgeProject>()

        for (i in 0 until array.length()) {
            val project = runCatching {
                val obj = array.getJSONObject(i)
                val project = BridgeProject(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    localFileModifyEnabled = obj.optBoolean("localFileModifyEnabled", false),
                    localAddress = obj.optString("localAddress", "")
                        .ifBlank { obj.optString("workspaceDirectory", "") }
                        .takeIf { it.isNotBlank() },
                    githubAddress = ProjectGitHubAddress(
                        accountLogin = obj.optString("githubAccount", "")
                            .ifBlank { obj.optString("githubAccountLogin", "").ifBlank { null } },
                        repositoryId = obj.optString("githubRepositoryId", "").toLongOrNull(),
                        repository = obj.optString("githubRepository", "").ifBlank { null },
                        branch = obj.optString("githubBranch", "").ifBlank { null },
                        readEnabled = obj.optBoolean("githubReadEnabled", true),
                        writeEnabled = obj.optBoolean("githubWriteEnabled", false)
                    ),
                    activeConversationId = obj.optString("activeConversationId", "").ifBlank { null },
                    defaultMemberId = obj.optString("defaultMemberId", "").ifBlank { null },
                    taskState = BridgeTaskExecutionState(
                        activeTaskId = obj.optString("activeTaskId", "").ifBlank { null },
                        status = obj.optString("taskStateStatus", "IDLE"),
                        continuationCount = obj.optInt("taskContinuationCount", 0),
                        maxContinuations = obj.optInt("taskMaxContinuations", 10).coerceIn(0, 50),
                        lastReceiptId = obj.optString("taskLastReceiptId", "").ifBlank { null },
                        lastResult = obj.optString("taskLastResult", "").ifBlank { null }
                    )
                )

                obj.optJSONArray("aiMembers")?.let { members ->
                    for (j in 0 until members.length()) {
                        val member = members.getJSONObject(j)
                        if (!member.has("id")) continue
                        project.aiMembers += BridgeAiMember(
                            id = member.getString("id"),
                            name = member.optString("name", "AI"),
                            apiProfileId = member.optString("apiProfileId", "").ifBlank { null }
                        )
                    }
                }

                obj.optJSONArray("tasks")?.let { tasks ->
                    for (j in 0 until tasks.length()) {
                        val task = tasks.getJSONObject(j)
                        if (!task.has("id")) continue
                        project.tasks += BridgeProjectTask(
                            id = task.getString("id"),
                            title = task.optString("title", "未命名任务"),
                            completed = task.optBoolean("completed", false),
                            status = task.optString("status", "PENDING"),
                            continuationCount = task.optInt("continuationCount", 0),
                            maxContinuations = task.optInt("maxContinuations", 10).coerceIn(0, 50),
                            lastReceiptId = task.optString("lastReceiptId", "").ifBlank { null },
                            lastResult = task.optString("lastResult", "").ifBlank { null }
                        )
                    }
                }

                obj.optJSONArray("conversations")?.let { conversations ->
                    for (j in 0 until conversations.length()) {
                        val conversation = conversations.getJSONObject(j)
                        if (!conversation.has("id")) continue
                        project.conversations += readConversation(conversation)
                    }
                }

                if (project.conversations.isEmpty()) {
                    val legacy = BridgeConversation(
                        id = UUID.randomUUID().toString(),
                        name = "默认对话",
                        apiId = obj.optString("apiId", "").ifBlank { null }
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
                project
            }.onFailure {
                AppLogger.log(
                    context,
                    "PROJECT_STORE_LOAD_SKIP",
                    "index=" + i + " error=" + it::class.simpleName + ": " + (it.message ?: "unknown")
                )
            }.getOrNull()

            if (project != null) {
                result += project
            }
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
                .put("activeTaskId", project.taskState.activeTaskId.orEmpty())
                .put("taskStateStatus", project.taskState.status)
                .put("taskContinuationCount", project.taskState.continuationCount)
                .put("taskMaxContinuations", project.taskState.maxContinuations)
                .put("taskLastReceiptId", project.taskState.lastReceiptId.orEmpty())
                .put("taskLastResult", project.taskState.lastResult.orEmpty())
                .put("githubAccount", project.githubAddress.accountLogin.orEmpty())
                .put("githubRepositoryId", project.githubAddress.repositoryId?.toString().orEmpty())
                .put("githubRepository", project.githubAddress.repository.orEmpty())
                .put("githubBranch", project.githubAddress.branch.orEmpty())
                .put("githubReadEnabled", project.githubAddress.readEnabled)
                .put("githubWriteEnabled", project.githubAddress.writeEnabled)

            obj.put("aiMembers", JSONArray().apply {
                project.aiMembers.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("apiProfileId", it.apiProfileId.orEmpty())) }
            })
            obj.put("tasks", JSONArray().apply {
                project.tasks.forEach {
                    put(
                        JSONObject()
                            .put("id", it.id)
                            .put("title", it.title)
                            .put("completed", it.completed)
                            .put("status", it.status)
                            .put("continuationCount", it.continuationCount)
                            .put("maxContinuations", it.maxContinuations)
                            .put("lastReceiptId", it.lastReceiptId.orEmpty())
                            .put("lastResult", it.lastResult.orEmpty())
                    )
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
                                put(JSONObject().put("status", it.status).put("command", it.command).put("message", it.message)
                                    .put("time", it.time).put("receiptId", it.receiptId))
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
            localFileModifyOverride = if (obj.has("localFileModifyOverride") && !obj.isNull("localFileModifyOverride")) obj.optBoolean("localFileModifyOverride") else null
        )
        readMessages(obj.optJSONArray("messages"), conversation.messages)
        readExecutions(obj.optJSONArray("executions"), conversation.executions)
        return conversation
    }

    private fun readMessages(array: JSONArray?, target: MutableList<BridgeChatMessage>) {
        if (array == null) return
        for (j in 0 until array.length()) {
            val item = array.getJSONObject(j)
            target += BridgeChatMessage(item.getString("role"), item.getString("content"), item.optLong("time", System.currentTimeMillis()),
                item.optString("apiId", "").ifBlank { null }, item.optString("apiName", "").ifBlank { null }, item.optString("apiAvatar", "").ifBlank { null })
        }
    }

    private fun readExecutions(array: JSONArray?, target: MutableList<BridgeReceiptRecord>) {
        if (array == null) return
        for (j in 0 until array.length()) {
            val item = array.getJSONObject(j)
            target += BridgeReceiptRecord(item.getString("status"), item.getString("command"), item.getString("message"),
                item.optLong("time", System.currentTimeMillis()), item.optString("receiptId", "").ifBlank { UUID.randomUUID().toString() })
        }
    }
}
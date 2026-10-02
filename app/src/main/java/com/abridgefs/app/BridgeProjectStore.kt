package com.abridgefs.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class BridgeChatMessage(val role: String, val content: String, val time: Long = System.currentTimeMillis())
data class BridgeReceiptRecord(val status: String, val command: String, val message: String, val time: Long = System.currentTimeMillis())

data class BridgeProject(
    val id: String,
    var name: String,
    var apiId: String? = null,
    var github: GitHubWorkspace = GitHubWorkspace(),
    val messages: MutableList<BridgeChatMessage> = mutableListOf(),
    val executions: MutableList<BridgeReceiptRecord> = mutableListOf()
) {
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
                apiId = obj.optString("apiId", "").ifBlank { null },
                github = GitHubWorkspace(
                    accountLogin = obj.optString("githubAccount", "").ifBlank {
                        obj.optString("githubAccountLogin", "").ifBlank { null }
                    },
                    repositoryId = obj.optString("githubRepositoryId", "").toLongOrNull(),
                    repository = obj.optString("githubRepository", "").ifBlank { null },
                    branch = obj.optString("githubBranch", "").ifBlank { null },
                    readEnabled = obj.optBoolean("githubReadEnabled", true),
                    writeEnabled = obj.optBoolean("githubWriteEnabled", false)
                )
            )

            val messages = obj.optJSONArray("messages") ?: JSONArray()
            for (j in 0 until messages.length()) {
                val item = messages.getJSONObject(j)
                project.messages += BridgeChatMessage(
                    item.getString("role"),
                    item.getString("content"),
                    item.optLong("time", System.currentTimeMillis())
                )
            }

            val executions = obj.optJSONArray("executions") ?: JSONArray()
            for (j in 0 until executions.length()) {
                val item = executions.getJSONObject(j)
                project.executions += BridgeReceiptRecord(
                    item.getString("status"),
                    item.getString("command"),
                    item.getString("message"),
                    item.optLong("time", System.currentTimeMillis())
                )
            }
            result += project
        }
        return result
    }

    fun save(projects: List<BridgeProject>) {
        val array = JSONArray()
        projects.forEach { project ->
            val obj = JSONObject()
                .put("id", project.id)
                .put("name", project.name)
                .put("apiId", project.apiId.orEmpty())
                .put("githubAccount", project.github.accountLogin.orEmpty())
                .put("githubRepositoryId", project.github.repositoryId?.toString().orEmpty())
                .put("githubRepository", project.github.repository.orEmpty())
                .put("githubBranch", project.github.branch.orEmpty())
                .put("githubReadEnabled", project.github.readEnabled)
                .put("githubWriteEnabled", project.github.writeEnabled)

            obj.put("messages", JSONArray().apply {
                project.messages.forEach {
                    put(JSONObject().put("role", it.role).put("content", it.content).put("time", it.time))
                }
            })
            obj.put("executions", JSONArray().apply {
                project.executions.forEach {
                    put(JSONObject().put("status", it.status).put("command", it.command).put("message", it.message).put("time", it.time))
                }
            })
            array.put(obj)
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    fun newProject(name: String): BridgeProject = BridgeProject(UUID.randomUUID().toString(), name)
}

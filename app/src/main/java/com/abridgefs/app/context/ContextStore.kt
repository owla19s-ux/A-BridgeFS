package com.abridgefs.app.context

import android.content.Context
import com.abridgefs.app.github.GitHubResource
import org.json.JSONArray
import org.json.JSONObject

/**
 * Context 的最小本地持久化边界。
 *
 * 只负责 Context 本身的保存、读取和删除。
 * Task、Conversation、Dispatcher 等不属于此 Store。
 */
class ContextStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun get(id: String): Context? = load().firstOrNull { it.id == id }

    fun all(): List<Context> = load()

    fun save(context: Context) {
        write(load().filterNot { it.id == context.id } + context)
    }

    fun delete(id: String) {
        write(load().filterNot { it.id == id })
    }

    private fun load(): List<Context> {
        val raw = prefs.getString(KEY_DATA, null) ?: return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) add(fromJson(array.getJSONObject(i)))
        }
    }

    private fun write(contexts: List<Context>) {
        val array = JSONArray()
        contexts.forEach { array.put(toJson(it)) }
        prefs.edit().putString(KEY_DATA, array.toString()).apply()
    }

    private fun toJson(value: Context) = JSONObject().apply {
        put("id", value.id)
        put("name", value.name)
        put("resources", JSONArray().apply {
            value.resources.forEach { put(toResourceJson(it)) }
        })
    }

    private fun toResourceJson(value: ResourceRef) = when (value) {
        is ResourceRef.GitHub -> JSONObject().apply {
            put("type", "github")
            when (val resource = value.resource) {
                is GitHubResource.Repository -> {
                    put("resourceType", "repository")
                    put("id", resource.id)
                    put("fullName", resource.fullName)
                    resource.defaultBranch?.let { put("defaultBranch", it) }
                }
                is GitHubResource.Branch -> {
                    put("resourceType", "branch")
                    put("repositoryId", resource.repositoryId)
                    put("name", resource.name)
                }
                is GitHubResource.File -> {
                    put("resourceType", "file")
                    put("path", resource.path)
                }
                is GitHubResource.Commit -> {
                    put("resourceType", "commit")
                    put("ref", resource.ref)
                }
            }
        }
    }

    private fun fromJson(json: JSONObject): Context {
        val resources = json.optJSONArray("resources") ?: JSONArray()
        return Context(
            id = json.getString("id"),
            name = json.getString("name"),
            resources = buildList {
                for (i in 0 until resources.length()) {
                    add(fromResourceJson(resources.getJSONObject(i)))
                }
            }
        )
    }

    private fun fromResourceJson(json: JSONObject): ResourceRef {
        require(json.getString("type") == "github") { "不支持的 Resource 类型" }
        return ResourceRef.GitHub(when (json.getString("resourceType")) {
            "repository" -> GitHubResource.Repository(
                id = json.getLong("id"),
                fullName = json.getString("fullName"),
                defaultBranch = json.optString("defaultBranch").ifBlank { null }
            )
            "branch" -> GitHubResource.Branch(
                repositoryId = json.getLong("repositoryId"),
                name = json.getString("name")
            )
            "file" -> GitHubResource.File(json.getString("path"))
            "commit" -> GitHubResource.Commit(json.getString("ref"))
            else -> error("不支持的 GitHub Resource 类型")
        })
    }

    private companion object {
        const val PREFERENCES = "aps_contexts"
        const val KEY_DATA = "data"
    }
}

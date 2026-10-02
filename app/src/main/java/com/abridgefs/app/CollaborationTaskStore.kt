package com.abridgefs.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persisted state for one collaboration task.
 *
 * A task is separate from the Conversation and from API Profiles. It records
 * whether this task has explicitly entered the construction stage, while the
 * ConstructionLockStore remains the exclusive authority for Repository/Branch.
 */
data class CollaborationTaskRecord(
    val taskId: String,
    val workspaceId: String,
    val conversationId: String,
    var objective: String,
    var status: String = STATUS_CREATED,
    var constructionRequestedByAiMemberId: String? = null,
    var constructionHolderAiMemberId: String? = null,
    var lastCommitSha: String? = null,
    var lastChangedPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_CREATED = "CREATED"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_WAITING_CONSTRUCTION = "WAITING_CONSTRUCTION"
        const val STATUS_CONSTRUCTING = "CONSTRUCTING"
        const val STATUS_WAITING_VERIFY = "WAITING_VERIFY"
        const val STATUS_COMPLETE = "COMPLETE"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}

/**
 * Local recovery store for collaboration task state.
 *
 * It intentionally does not own GitHub credentials or the construction lock.
 * The lock is recovered independently by ConstructionLockStore.
 */
class CollaborationTaskStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("collaboration_tasks", Context.MODE_PRIVATE)
    private val key = "data"

    @Synchronized
    fun create(
        workspaceId: String,
        conversationId: String,
        objective: String,
        taskId: String = CollaborationProtocol.newTaskId()
    ): CollaborationTaskRecord {
        require(workspaceId.isNotBlank()) { "workspaceId is blank" }
        require(conversationId.isNotBlank()) { "conversationId is blank" }
        require(objective.isNotBlank()) { "objective is blank" }
        val task = CollaborationTaskRecord(
            taskId = taskId,
            workspaceId = workspaceId,
            conversationId = conversationId,
            objective = objective
        )
        save(task)
        return task
    }

    @Synchronized
    fun get(taskId: String): CollaborationTaskRecord? =
        load().firstOrNull { it.taskId == taskId }

    @Synchronized
    fun latest(workspaceId: String, conversationId: String): CollaborationTaskRecord? =
        load()
            .filter { it.workspaceId == workspaceId && it.conversationId == conversationId }
            .maxByOrNull { it.updatedAt }

    @Synchronized
    fun save(task: CollaborationTaskRecord) {
        task.updatedAt = System.currentTimeMillis()
        val items = JSONArray()
        load().filterNot { it.taskId == task.taskId }.forEach { items.put(toJson(it)) }
        items.put(toJson(task))
        prefs.edit().putString(key, items.toString()).apply()
    }

    @Synchronized
    fun update(taskId: String, block: (CollaborationTaskRecord) -> Unit): CollaborationTaskRecord {
        val task = get(taskId) ?: error("协作任务不存在：$taskId")
        block(task)
        save(task)
        return task
    }

    @Synchronized
    fun clearWorkspace(workspaceId: String) {
        val items = JSONArray()
        load().filterNot { it.workspaceId == workspaceId }.forEach { items.put(toJson(it)) }
        prefs.edit().putString(key, items.toString()).apply()
    }

    private fun load(): List<CollaborationTaskRecord> {
        val array = JSONArray(prefs.getString(key, "[]") ?: "[]")
        return buildList {
            for (i in 0 until array.length()) {
                add(fromJson(array.getJSONObject(i)))
            }
        }
    }

    private fun toJson(task: CollaborationTaskRecord): JSONObject =
        JSONObject()
            .put("taskId", task.taskId)
            .put("workspaceId", task.workspaceId)
            .put("conversationId", task.conversationId)
            .put("objective", task.objective)
            .put("status", task.status)
            .put("constructionRequestedByAiMemberId", task.constructionRequestedByAiMemberId)
            .put("constructionHolderAiMemberId", task.constructionHolderAiMemberId)
            .put("lastCommitSha", task.lastCommitSha)
            .put("lastChangedPath", task.lastChangedPath)
            .put("createdAt", task.createdAt)
            .put("updatedAt", task.updatedAt)

    private fun fromJson(obj: JSONObject): CollaborationTaskRecord =
        CollaborationTaskRecord(
            taskId = obj.getString("taskId"),
            workspaceId = obj.getString("workspaceId"),
            conversationId = obj.getString("conversationId"),
            objective = obj.optString("objective", ""),
            status = obj.optString("status", CollaborationTaskRecord.STATUS_CREATED),
            constructionRequestedByAiMemberId = obj.optString("constructionRequestedByAiMemberId", "").ifBlank { null },
            constructionHolderAiMemberId = obj.optString("constructionHolderAiMemberId", "").ifBlank { null },
            lastCommitSha = obj.optString("lastCommitSha", "").ifBlank { null },
            lastChangedPath = obj.optString("lastChangedPath", "").ifBlank { null },
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
        )
}

package com.abridgefs.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal transport boundary for Decision AI ↔ Worker.
 *
 * Persistence is intentionally local for v0.1. The protocol does not depend on
 * GitHub Issues, files, or any specific network transport.
 */
class CollaborationTransport(
    context: Context,
    workspaceId: String,
    conversationId: String
) {
    private val prefs = context.getSharedPreferences("collaboration_transport", Context.MODE_PRIVATE)
    private val keySuffix = workspaceId.ifBlank { "unknown_workspace" } + "_" +
        conversationId.ifBlank { "unknown_conversation" }


    fun append(message: CollaborationProtocol.Message) {
        val items = JSONArray(prefs.getString("${KEY_MESSAGES}_$keySuffix", "[]") ?: "[]")
        items.put(message.toJson())
        prefs.edit().putString("${KEY_MESSAGES}_$keySuffix", items.toString()).apply()
    }

    fun all(): List<CollaborationProtocol.Message> {
        val items = JSONArray(prefs.getString("${KEY_MESSAGES}_$keySuffix", "[]") ?: "[]")
        return buildList {
            for (index in 0 until items.length()) {
                val message = CollaborationProtocol.Message.fromJson(items.getJSONObject(index))
                add(message)
            }
        }
    }

    fun pendingFor(role: CollaborationProtocol.Role): List<CollaborationProtocol.Message> =
        all().filter { it.to == role && !isHandled(it.id) }

    fun markHandled(messageId: String) {
        val handled = JSONArray(prefs.getString("${KEY_HANDLED}_$keySuffix", "[]") ?: "[]")
        if ((0 until handled.length()).none { handled.optString(it) == messageId }) {
            handled.put(messageId)
            prefs.edit().putString("${KEY_HANDLED}_$keySuffix", handled.toString()).apply()
        }
    }

    private fun isHandled(messageId: String): Boolean {
        val handled = JSONArray(prefs.getString("${KEY_HANDLED}_$keySuffix", "[]") ?: "[]")
        return (0 until handled.length()).any { handled.optString(it) == messageId }
    }

    fun clear() {
        prefs.edit()
            .remove("${KEY_MESSAGES}_$keySuffix")
            .remove("${KEY_HANDLED}_$keySuffix")
            .apply()
    }

    companion object {
        private const val KEY_MESSAGES = "messages"
        private const val KEY_HANDLED = "handled"
    }
}

/**
 * Runtime API connection for one AI participant.
 *
 * API profiles are reusable connection resources. The collaboration stage
 * chooses which two profiles participate; there is no persistent Decision/Worker
 * API configuration.
 */
data class CollaborationApiConfig(
    val profileId: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String
) {
    fun isConfigured(): Boolean =
        baseUrl.isNotBlank() && model.isNotBlank()

    companion object {
        fun fromProfile(context: Context, profileId: String): CollaborationApiConfig {
            val profile = ApiProfileStore(context).find(profileId)
                ?: return CollaborationApiConfig(profileId, "", "", "")
            return CollaborationApiConfig(
                profileId = profile.id,
                baseUrl = profile.baseUrl.trim(),
                apiKey = profile.key,
                model = profile.model.trim()
            )
        }
    }
}

/**
 * Small role-aware API boundary. It does not decide how protocol JSON is
 * generated or parsed; callers remain responsible for protocol semantics.
 */
class CollaborationApiClient(private val config: CollaborationApiConfig) {
    fun invoke(
        incoming: CollaborationProtocol.Message,
        systemPrompt: String
    ): String {
        require(config.isConfigured()) { "collaboration API is not configured" }

        val client = BridgeApiClient(
            BridgeApiConfig(
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                model = config.model
            )
        )

        val request = incoming.toJson().toString()
        return client.chat(
            listOf(BridgeChatMessage("user", request)),
            systemPrompt
        )
    }
}

/**
 * Minimal coordinator used by later UI/service integration.
 *
 * It provides the first real boundary between:
 * Decision AI API → protocol transport → Worker API.
 * It deliberately does not execute GitHub work yet.
 */
class CollaborationCoordinator(
    private val context: Context,
    private val workspaceId: String,
    private val conversationId: String,
    private val firstProfileId: String,
    private val secondProfileId: String
) {
    private val transport = CollaborationTransport(context, workspaceId, conversationId)

    fun submitTask(task: CollaborationProtocol.Message) {
        require(task.type == CollaborationProtocol.Type.TASK) { "submitTask requires TASK" }
        require(task.from == CollaborationProtocol.Role.DECISION_AI)
        require(task.to == CollaborationProtocol.Role.WORKER)
        require(CollaborationProtocol.validate(task).valid)
        transport.append(task)
    }

    /**
     * Explicitly enters the construction stage for a persisted task.
     *
     * Analysis-only collaboration never acquires a Repository/Branch lock.
     * The caller must explicitly request construction with an AI Member id.
     */
    fun requestConstruction(taskId: String, aiMemberId: String): CollaborationTaskRecord {
        val taskStore = CollaborationTaskStore(context)
        val task = taskStore.get(taskId) ?: error("协作任务不存在：$taskId")
        require(task.workspaceId == workspaceId && task.conversationId == conversationId) {
            "协作任务不属于当前工作区 / 对话"
        }

        val workspace = BridgeProjectStore(context).load().firstOrNull { it.id == workspaceId }
            ?: error("工作区不存在：$workspaceId")
        require(workspace.aiMembers.any { it.id == aiMemberId }) {
            "AI Member 不属于当前工作区"
        }

        val lock = ConstructionLockStore(context).acquire(workspace, aiMemberId)
        taskStore.update(taskId) {
            it.status = CollaborationTaskRecord.STATUS_CONSTRUCTING
            it.constructionRequestedByAiMemberId = aiMemberId
            it.constructionHolderAiMemberId = lock.holderAiMemberId
        }
        AppLogger.log(context, AppLogger.Category.COLLABORATION, "CONSTRUCTION_ACQUIRED", "taskId=$taskId")
        return taskStore.get(taskId) ?: error("协作任务状态保存失败")
    }

    /**
     * Performs one real GitHub Contents write for a task that already holds
     * construction authority. GitHub's Contents API creates the commit.
     */
    fun updateFile(
        taskId: String,
        aiMemberId: String,
        path: String,
        content: String,
        message: String,
        sha: String
    ): JSONObject {
        val task = CollaborationTaskStore(context).get(taskId)
            ?: error("协作任务不存在：$taskId")
        require(task.workspaceId == workspaceId && task.conversationId == conversationId) {
            "协作任务不属于当前工作区 / 对话"
        }
        require(task.status == CollaborationTaskRecord.STATUS_CONSTRUCTING) {
            "当前协作任务未进入施工阶段"
        }
        require(task.constructionHolderAiMemberId == aiMemberId) {
            "当前 AI 不是该协作任务的施工者"
        }

        val workspace = BridgeProjectStore(context).load().firstOrNull { it.id == workspaceId }
            ?: error("工作区不存在：$workspaceId")
        val auth = GitHubTokenStore(context).state()
        val token = auth.accessToken?.takeIf { it.isNotBlank() }
            ?: error("GitHub 尚未授权")
        val service = GitHubWorkspaceService(context, GitHubApiClient(context, token), workspace.github)
        val result = service.updateFile(path, content, message, sha, aiMemberId)
        val commitSha = result.optJSONObject("commit")?.optString("sha").orEmpty().ifBlank { null }

        CollaborationTaskStore(context).update(taskId) {
            it.status = CollaborationTaskRecord.STATUS_WAITING_VERIFY
            it.lastCommitSha = commitSha
            it.lastChangedPath = path
        }
        AppLogger.log(context, AppLogger.Category.COLLABORATION, "GITHUB_WRITE", "taskId=$taskId path=$path")
        return result
    }

    /**
     * Releases construction authority after the task reaches a safe boundary.
     * It does not silently release another AI's lock.
     */
    fun releaseConstruction(taskId: String, aiMemberId: String) {
        val taskStore = CollaborationTaskStore(context)
        val task = taskStore.get(taskId) ?: error("协作任务不存在：$taskId")
        require(task.workspaceId == workspaceId && task.conversationId == conversationId) {
            "协作任务不属于当前工作区 / 对话"
        }
        val workspace = BridgeProjectStore(context).load().firstOrNull { it.id == workspaceId }
            ?: error("工作区不存在：$workspaceId")
        ConstructionLockStore(context).release(workspace, aiMemberId)
        taskStore.update(taskId) {
            it.status = CollaborationTaskRecord.STATUS_WAITING_VERIFY
            it.constructionHolderAiMemberId = null
        }
        AppLogger.log(context, AppLogger.Category.COLLABORATION, "CONSTRUCTION_RELEASED", "taskId=$taskId")
    }

    /**
     * Checks the exact Commit SHA against GitHub Actions and closes the task
     * only after a completed successful run.
     */
    fun verifyTask(taskId: String): GitHubVerifyResult =
        CollaborationVerifyService(context).verify(taskId)

    /**
     * Returns the current persisted task for this Workspace + Conversation.
     */
    fun currentTask(): CollaborationTaskRecord? =
        CollaborationTaskStore(context).latest(workspaceId, conversationId)

    fun callDecisionAi(message: CollaborationProtocol.Message, systemPrompt: String): String {
        require(message.to == CollaborationProtocol.Role.DECISION_AI)
        return CollaborationApiClient(CollaborationApiConfig.fromProfile(context, firstProfileId)).invoke(message, systemPrompt)
    }

    fun callWorker(message: CollaborationProtocol.Message, systemPrompt: String): String {
        require(message.to == CollaborationProtocol.Role.WORKER)
        return CollaborationApiClient(CollaborationApiConfig.fromProfile(context, secondProfileId)).invoke(message, systemPrompt)
    }

    fun runObjective(
        objective: String,
        decisionSystemPrompt: String,
        workerSystemPrompt: String
    ): List<CollaborationProtocol.Message> {
        require(objective.isNotBlank()) { "objective is blank" }
        val taskRecord = CollaborationTaskStore(context).create(
            workspaceId = workspaceId,
            conversationId = conversationId,
            objective = objective
        )
        val taskId = taskRecord.taskId
        CollaborationTaskStore(context).update(taskId) {
            it.status = CollaborationTaskRecord.STATUS_RUNNING
        }
        val humanMessage = CollaborationProtocol.Message(
            from = CollaborationProtocol.Role.HUMAN,
            to = CollaborationProtocol.Role.DECISION_AI,
            taskId = taskId,
            type = CollaborationProtocol.Type.PROGRESS,
            payload = JSONObject().put("objective", objective)
        )
        AppLogger.log(context, AppLogger.Category.COLLABORATION, "HUMAN_OBJECTIVE", "taskId=" + taskId)
        val rawTask = callDecisionAi(humanMessage, decisionSystemPrompt)
        val task = parseProtocolResponse(rawTask)
        require(task.type == CollaborationProtocol.Type.TASK) { "Decision AI did not return TASK" }
        require(task.taskId == taskId) { "Decision AI changed task_id; collaboration task state cannot be recovered safely" }
        require(task.from == CollaborationProtocol.Role.DECISION_AI && task.to == CollaborationProtocol.Role.WORKER) {
            "Decision AI TASK route is invalid"
        }
        submitTask(task)
        CollaborationTaskStore(context).update(taskId) {
            it.status = CollaborationTaskRecord.STATUS_RUNNING
        }
        AppLogger.log(context, AppLogger.Category.COLLABORATION, "TASK_SUBMITTED", "taskId=" + taskId)
        return dispatchOneWorkerRound(workerSystemPrompt, decisionSystemPrompt)
    }

    /** Execute exactly one Worker -> Decision AI round. */
    fun dispatchOneWorkerRound(workerSystemPrompt: String, decisionSystemPrompt: String): List<CollaborationProtocol.Message> {
        val task = transport.pendingFor(CollaborationProtocol.Role.WORKER).firstOrNull { it.type == CollaborationProtocol.Type.TASK } ?: return emptyList()
        val workerMessage = parseProtocolResponse(callWorker(task, workerSystemPrompt))
        validateResponse(workerMessage, CollaborationProtocol.Role.WORKER)
        transport.append(workerMessage)
        transport.markHandled(task.id)
        if (workerMessage.to != CollaborationProtocol.Role.DECISION_AI) return listOf(workerMessage)
        val decisionMessage = parseProtocolResponse(callDecisionAi(workerMessage, decisionSystemPrompt))
        validateResponse(decisionMessage, CollaborationProtocol.Role.DECISION_AI)
        transport.append(decisionMessage)
        val taskId = task.taskId
        CollaborationTaskStore(context).get(taskId)?.let { record ->
            CollaborationTaskStore(context).update(taskId) {
                it.status = when (decisionMessage.type) {
                    CollaborationProtocol.Type.COMPLETE -> CollaborationTaskRecord.STATUS_COMPLETE
                    CollaborationProtocol.Type.BLOCKED,
                    CollaborationProtocol.Type.ESCALATE -> CollaborationTaskRecord.STATUS_WAITING_CONSTRUCTION
                    else -> record.status
                }
            }
        }
        return listOf(workerMessage, decisionMessage)
    }

    fun pendingFor(role: CollaborationProtocol.Role): List<CollaborationProtocol.Message> = transport.pendingFor(role)

    private fun parseProtocolResponse(raw: String): CollaborationProtocol.Message {
        val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = try { JSONObject(text) } catch (_: Exception) {
            val start = text.indexOf('{'); val end = text.lastIndexOf('}')
            require(start >= 0 && end > start) { "AI response is not a protocol JSON object" }
            JSONObject(text.substring(start, end + 1))
        }
        return try { CollaborationProtocol.Message.fromJson(json) } catch (cause: Exception) {
            throw IllegalArgumentException("AI response is not a valid collaboration message: " + cause.message, cause)
        }
    }

    private fun validateResponse(message: CollaborationProtocol.Message, expectedFrom: CollaborationProtocol.Role) {
        require(message.from == expectedFrom) { "AI response has wrong sender: ${message.from}" }
        val result = CollaborationProtocol.validate(message)
        require(result.valid) { result.error ?: "invalid protocol response" }
    }
}

package com.abridgefs.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

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
    private val secondProfileId: String,
    private val workerAiMemberId: String? = null
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
        // Persisted task ownership is not sufficient: the actual
        // Repository/Branch lock must still be held by this AI immediately
        // before every GitHub write.
        ConstructionLockStore(context).requireHolder(workspace, aiMemberId)

        val auth = GitHubTokenStore(context).state()
        val token = auth.accessToken?.takeIf { it.isNotBlank() }
            ?: error("GitHub 尚未授权")

        CollaborationTaskStore(context).update(taskId) {
            it.status = CollaborationTaskRecord.STATUS_CONSTRUCTION_WRITING
            it.pendingWritePath = path
            it.pendingWriteContent = content
            it.pendingWriteMessage = message
            it.pendingWriteSha = sha
        }

        val service = GitHubWorkspaceService(context, GitHubApiClient(context, token), workspace.github, workspace)
        val result = service.updateFile(path, content, message, sha, aiMemberId)
        val commitSha = result.optJSONObject("commit")?.optString("sha").orEmpty().ifBlank { null }
        require(!commitSha.isNullOrBlank()) { "GitHub 写入成功但未返回 Commit SHA，已停止进入 Verify" }

        CollaborationTaskStore(context).update(taskId) {
            it.status = CollaborationTaskRecord.STATUS_WAITING_VERIFY
            it.lastCommitSha = commitSha
            it.lastChangedPath = path
            it.pendingWritePath = null
            it.pendingWriteContent = null
            it.pendingWriteMessage = null
            it.pendingWriteSha = null
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
        require(task.status == CollaborationTaskRecord.STATUS_CONSTRUCTING) {
            "任务已有 Commit，施工锁必须保留到 Verify 完成"
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
    fun verifyTask(taskId: String): GitHubVerifyResult {
        recoverInterruptedWrite(taskId)
        return CollaborationVerifyService(context).verify(taskId)
    }

    private fun recoverInterruptedWrite(taskId: String) {
        val task = CollaborationTaskStore(context).get(taskId) ?: return
        if (task.status != CollaborationTaskRecord.STATUS_CONSTRUCTION_WRITING) return
        val path = task.pendingWritePath?.takeIf { it.isNotBlank() } ?: error("施工恢复缺少目标路径")
        val content = task.pendingWriteContent ?: error("施工恢复缺少目标内容")
        val workspace = BridgeProjectStore(context).load().firstOrNull { it.id == task.workspaceId }
            ?: error("工作区不存在：" + task.workspaceId)
        val holder = task.constructionHolderAiMemberId?.takeIf { it.isNotBlank() }
            ?: error("施工恢复缺少施工者")
        ConstructionLockStore(context).requireHolder(workspace, holder)

        val token = GitHubTokenStore(context).state().accessToken?.takeIf { it.isNotBlank() }
            ?: error("GitHub 尚未授权")
        val service = GitHubWorkspaceService(context, GitHubApiClient(context, token), workspace.github, workspace)
        val remote = service.file(path)
        val expectedBlobSha = gitBlobSha(content)
        require(remote.optString("sha").trim() == expectedBlobSha) {
            "施工恢复检测到远端文件与预期内容不一致：" + path + "；不会重复写入"
        }

        val branchHead = service.branchHead().trim()
        require(branchHead.isNotBlank()) { "施工恢复无法取得 Branch HEAD" }
        val commit = service.commit(branchHead)
        val files = commit.optJSONArray("files")
        val touched = files != null && (0 until files.length()).any {
            files.optJSONObject(it)?.optString("filename") == path
        }
        require(touched) {
            "施工恢复无法确认 Branch HEAD 包含目标文件变更：" + path + "；不会进入 Verify"
        }

        CollaborationTaskStore(context).update(taskId) {
            it.status = CollaborationTaskRecord.STATUS_WAITING_VERIFY
            it.lastCommitSha = branchHead
            it.lastChangedPath = path
            it.pendingWritePath = null
            it.pendingWriteContent = null
            it.pendingWriteMessage = null
            it.pendingWriteSha = null
        }
        AppLogger.log(context, AppLogger.Category.COLLABORATION, "GITHUB_WRITE_RECOVERED",
            "taskId=" + taskId + " commit=" + branchHead + " path=" + path)
    }

    private fun gitBlobSha(content: String): String {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val header = ("blob " + bytes.size + "\u0000").toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(header + bytes).joinToString("") { "%02x".format(it) }
    }

    private fun discardPendingWorkerInputs(taskId: String) {
        transport.pendingFor(CollaborationProtocol.Role.WORKER)
            .filter {
                it.taskId == taskId &&
                    (it.type == CollaborationProtocol.Type.TASK ||
                        it.type == CollaborationProtocol.Type.DECISION_RESPONSE)
            }
            .forEach { transport.markHandled(it.id) }
    }

    /**
     * Verifies the current Commit and, when it passes, starts one bounded
     * Decision AI -> Worker continuation round. The next Worker file change,
     * if any, must reacquire the ConstructionLock through the normal path.
     */
    fun verifyAndContinue(taskId: String, decisionSystemPrompt: String, workerSystemPrompt: String): GitHubVerifyResult {
        val result = verifyTask(taskId)
        if (result.state != GitHubVerifyState.PASSED) return result

        val task = CollaborationTaskStore(context).get(taskId) ?: return result
        discardPendingWorkerInputs(taskId)
        CollaborationTaskStore(context).update(taskId) {
            it.status = CollaborationTaskRecord.STATUS_RUNNING
            it.constructionHolderAiMemberId = null
        }

        val commitMessage = CollaborationProtocol.commit(
            taskId,
            result.commitSha,
            "Verified Commit",
            listOfNotNull(task.lastChangedPath),
            "GitHub Actions Verify passed"
        )
        transport.append(commitMessage)

        val decisionPrompt = decisionSystemPrompt +
            "\n这是系统确认通过的 Commit。请决定任务是否完成；若未完成，必须返回合法 JSON，from=decision_ai,to=worker,type=DECISION_RESPONSE，并给出下一步施工指令。"
        val decision = parseProtocolResponseWithRetry(callDecisionAi(commitMessage, decisionPrompt)) {
            callDecisionAi(commitMessage, decisionPrompt + compactRetryPrompt(CollaborationProtocol.Role.DECISION_AI, false))
        }
        validateResponse(decision, CollaborationProtocol.Role.DECISION_AI)
        transport.append(decision)

        if (decision.type == CollaborationProtocol.Type.COMPLETE) {
            CollaborationTaskStore(context).update(taskId) {
                it.status = CollaborationTaskRecord.STATUS_COMPLETE
            }
            return result
        }

        require(decision.type == CollaborationProtocol.Type.DECISION_RESPONSE) {
            "Verify 通过后的 Decision AI 必须返回 DECISION_RESPONSE 或 COMPLETE"
        }
        dispatchOneWorkerRound(workerSystemPrompt, decisionSystemPrompt)
        return result
    }

    /**
     * Reopens a Verify-failed task for a bounded repair round. The existing
     * construction holder is intentionally retained so another AI cannot
     * modify the same Repository/Branch while the failure is being repaired.
     */
    fun retryAfterVerifyFailure(taskId: String, decisionSystemPrompt: String, workerSystemPrompt: String): GitHubVerifyResult {
        val task = CollaborationTaskStore(context).get(taskId) ?: error("协作任务不存在：$taskId")
        require(task.workspaceId == workspaceId && task.conversationId == conversationId) {
            "协作任务不属于当前工作区 / 对话"
        }
        require(task.status == CollaborationTaskRecord.STATUS_FAILED) {
            "只有 Verify 失败的任务可以进入修复轮"
        }
        val repairHolder = task.constructionHolderAiMemberId?.takeIf { it.isNotBlank() }
            ?: error("当前任务没有施工者，无法自动进入修复轮")

        val workspace = BridgeProjectStore(context).load().firstOrNull { it.id == workspaceId }
            ?: error("工作区不存在：$workspaceId")

        // A Verify failure deliberately retains construction authority for the
        // repair round. Do not transition FAILED -> RUNNING unless the actual
        // Repository/Branch lock is still held by the recorded repair owner.
        ConstructionLockStore(context).requireHolder(workspace, repairHolder)
        discardPendingWorkerInputs(taskId)

        CollaborationTaskStore(context).update(taskId) {
            it.status = CollaborationTaskRecord.STATUS_RUNNING
        }

        val failureNotice = CollaborationProtocol.Message(
            from = CollaborationProtocol.Role.HUMAN,
            to = CollaborationProtocol.Role.DECISION_AI,
            taskId = taskId,
            type = CollaborationProtocol.Type.PROGRESS,
            payload = JSONObject()
                .put("objective", task.objective)
                .put("instruction", "上一 Commit 的 GitHub Actions Verify 失败，请分析失败结果并决定下一步修复；不得直接宣布任务完成。")
        )
        val decision = parseProtocolResponseWithRetry(callDecisionAi(failureNotice, decisionSystemPrompt)) {
            callDecisionAi(failureNotice, decisionSystemPrompt + compactRetryPrompt(CollaborationProtocol.Role.DECISION_AI, false))
        }
        validateResponse(decision, CollaborationProtocol.Role.DECISION_AI)
        require(decision.type == CollaborationProtocol.Type.DECISION_RESPONSE) {
            "Verify 失败后的 Decision AI 必须返回 DECISION_RESPONSE"
        }
        transport.append(decision)
        dispatchOneWorkerRound(workerSystemPrompt, decisionSystemPrompt)
        return GitHubVerifyResult(
            GitHubVerifyState.FAILED,
            task.lastCommitSha.orEmpty(),
            message = "Verify 失败，已进入修复轮"
        )
    }

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
        val task = parseProtocolResponseWithRetry(rawTask) {
            callDecisionAi(humanMessage, decisionSystemPrompt + compactRetryPrompt(CollaborationProtocol.Role.DECISION_AI))
        }
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
        val messages = mutableListOf<CollaborationProtocol.Message>()
        var rounds = 0
        val maxIterations = task.payload.optJSONObject("autonomy")?.optInt("max_iterations", 20)?.coerceIn(1, 20) ?: 20
        while (rounds < maxIterations) {
            val round = dispatchOneWorkerRound(workerSystemPrompt, decisionSystemPrompt)
            if (round.isEmpty()) break
            messages += round
            rounds++
            val decision = round.lastOrNull { it.from == CollaborationProtocol.Role.DECISION_AI }
            val state = CollaborationTaskStore(context).get(taskId)?.status
            if (decision?.type == CollaborationProtocol.Type.COMPLETE ||
                decision?.type == CollaborationProtocol.Type.BLOCKED ||
                decision?.type == CollaborationProtocol.Type.ESCALATE ||
                state == CollaborationTaskRecord.STATUS_WAITING_VERIFY ||
                state == CollaborationTaskRecord.STATUS_COMPLETE ||
                state == CollaborationTaskRecord.STATUS_FAILED) break
        }
        if (rounds >= maxIterations && CollaborationTaskStore(context).get(taskId)?.status == CollaborationTaskRecord.STATUS_RUNNING) {
            CollaborationTaskStore(context).update(taskId) { it.status = CollaborationTaskRecord.STATUS_FAILED }
            AppLogger.log(context, AppLogger.Category.COLLABORATION, "MAX_ITERATIONS", "taskId=$taskId")
        }
        return messages
    }

    /** Execute exactly one Worker -> Decision AI round. */
    fun dispatchOneWorkerRound(workerSystemPrompt: String, decisionSystemPrompt: String): List<CollaborationProtocol.Message> {
        val current = currentTask() ?: return emptyList()
        // A persisted TASK/DECISION_RESPONSE may remain unhandled after a process
        // restart. Never replay it after a real Commit has already moved the task
        // into Verify/terminal state.
        if (current.status != CollaborationTaskRecord.STATUS_RUNNING &&
            current.status != CollaborationTaskRecord.STATUS_CONSTRUCTING
        ) return emptyList()

        val task = transport.pendingFor(CollaborationProtocol.Role.WORKER)
            .firstOrNull {
                it.taskId == current.taskId &&
                    (it.type == CollaborationProtocol.Type.TASK || it.type == CollaborationProtocol.Type.DECISION_RESPONSE)
            } ?: return emptyList()
        val workerMessage = parseProtocolResponseWithRetry(callWorker(task, workerSystemPrompt)) {
            callWorker(task, workerSystemPrompt + compactRetryPrompt(CollaborationProtocol.Role.WORKER))
        }
        validateResponse(workerMessage, CollaborationProtocol.Role.WORKER)
        require(workerMessage.type in setOf(CollaborationProtocol.Type.DECISION_REQUEST, CollaborationProtocol.Type.PROGRESS, CollaborationProtocol.Type.BLOCKED, CollaborationProtocol.Type.FILE_CHANGE_REQUEST)) {
            "Worker 只能返回 DECISION_REQUEST / PROGRESS / BLOCKED / FILE_CHANGE_REQUEST；Commit / Verify / Complete 必须由系统状态产生"
        }
        transport.append(workerMessage)
        transport.markHandled(task.id)

        val messages = mutableListOf(workerMessage)
        val decisionInput = if (workerMessage.type == CollaborationProtocol.Type.FILE_CHANGE_REQUEST) {
            val commit = executeFileChangeRequest(task, workerMessage)
            transport.append(commit)
            messages += commit
            commit
        } else {
            workerMessage
        }
        if (decisionInput.to != CollaborationProtocol.Role.DECISION_AI) return messages
        val decisionPrompt = if (decisionInput.type == CollaborationProtocol.Type.COMMIT) {
            decisionSystemPrompt + "\n现在进入施工结果审议阶段。你必须返回合法 JSON；from=decision_ai，to=worker，type 必须为 DECISION_RESPONSE 或 COMPLETE。若 Commit 已满足目标，可返回 COMPLETE；否则返回 DECISION_RESPONSE，并在 instruction 中给出下一步。"
        } else {
            decisionSystemPrompt
        }
        val decisionMessage = parseProtocolResponseWithRetry(callDecisionAi(decisionInput, decisionPrompt)) {
            callDecisionAi(decisionInput, decisionPrompt + compactRetryPrompt(CollaborationProtocol.Role.DECISION_AI, false))
        }
        validateResponse(decisionMessage, CollaborationProtocol.Role.DECISION_AI)
        transport.append(decisionMessage)
        val taskId = task.taskId
        CollaborationTaskStore(context).get(taskId)?.let { record ->
            CollaborationTaskStore(context).update(taskId) {
                it.status = when (decisionMessage.type) {
                    CollaborationProtocol.Type.BLOCKED,
                    CollaborationProtocol.Type.ESCALATE -> CollaborationTaskRecord.STATUS_WAITING_CONSTRUCTION
                    CollaborationProtocol.Type.COMPLETE -> when (it.status) {
                        CollaborationTaskRecord.STATUS_FAILED -> CollaborationTaskRecord.STATUS_FAILED
                        CollaborationTaskRecord.STATUS_WAITING_VERIFY -> CollaborationTaskRecord.STATUS_WAITING_VERIFY
                        CollaborationTaskRecord.STATUS_COMPLETE -> CollaborationTaskRecord.STATUS_COMPLETE
                        else -> CollaborationTaskRecord.STATUS_COMPLETE
                    }
                    else -> record.status
                }
            }
        }
        messages += decisionMessage
        return messages
    }

    fun pendingFor(role: CollaborationProtocol.Role): List<CollaborationProtocol.Message> = transport.pendingFor(role)

    private fun parseProtocolResponseWithRetry(
        raw: String,
        retry: () -> String
    ): CollaborationProtocol.Message {
        return runCatching {
            parseProtocolResponse(raw)
        }.getOrElse { firstError ->
            AppLogger.log(context, AppLogger.Category.COLLABORATION, "PROTOCOL_PARSE_RETRY", firstError.message ?: "invalid protocol response")
            parseProtocolResponse(retry())
        }
    }

    private fun compactRetryPrompt(role: CollaborationProtocol.Role, initialTask: Boolean = true): String {
        val route = if (role == CollaborationProtocol.Role.DECISION_AI) {
            if (initialTask) "from=decision_ai,to=worker,type=TASK" else "from=decision_ai,to=worker,type=DECISION_RESPONSE|COMPLETE"
        } else "from=worker,to=decision_ai,type=DECISION_REQUEST|PROGRESS|BLOCKED|FILE_CHANGE_REQUEST"
        return "\n上一轮输出无法被完整解析。请立即重新输出一个完整、紧凑、合法的 JSON 对象；不要 Markdown、不要解释、不要换行长文本；$route。避免冗长 scope、acceptance、autonomy 与 context_refs，只保留完成协议所需内容。确保最后一个字符为 }。"
    }
    private fun executeFileChangeRequest(
        taskMessage: CollaborationProtocol.Message,
        request: CollaborationProtocol.Message
    ): CollaborationProtocol.Message {
        require(request.type == CollaborationProtocol.Type.FILE_CHANGE_REQUEST)
        require(request.taskId == taskMessage.taskId)
        val memberId = workerAiMemberId?.takeIf { it.isNotBlank() }
            ?: error("Worker AI Member 未绑定，不能进入自动施工")
        val workspace = BridgeProjectStore(context).load().firstOrNull { it.id == workspaceId }
            ?: error("工作区不存在：" + workspaceId)
        require(workspace.github.writeEnabled) { "当前工作区未允许 GitHub 修改" }
        val taskDefinition = transport.all().firstOrNull { it.taskId == taskMessage.taskId && it.type == CollaborationProtocol.Type.TASK }
            ?: error("协作 TASK 不存在，无法校验施工范围")
        val scope = taskDefinition.payload.optJSONObject("scope") ?: JSONObject()
        val path = request.payload.optString("path").trim().trimStart('/')
        val operation = request.payload.optString("operation", "write").trim().lowercase()
        val content = request.payload.optString("content", "")
        val commitMessage = request.payload.optString("commit_message", "")
        require(path.isNotBlank()) { "施工文件路径为空" }
        require(content.isNotEmpty()) { "施工文件内容为空" }
        require(commitMessage.isNotBlank()) { "Commit message 为空" }
        val allowPaths = scope.optJSONArray("allow_paths")?.let { a -> (0 until a.length()).map { a.optString(it).trim().trimStart('/') }.filter { it.isNotBlank() } } ?: emptyList()
        val denyPaths = scope.optJSONArray("deny_paths")?.let { a -> (0 until a.length()).map { a.optString(it).trim().trimStart('/') }.filter { it.isNotBlank() } } ?: emptyList()
        val allowOperations = scope.optJSONArray("allow_operations")?.let { a -> (0 until a.length()).map { a.optString(it).trim().lowercase() }.filter { it.isNotBlank() } } ?: emptyList()
        fun matches(root: String, candidate: String): Boolean = candidate == root || candidate.startsWith(root.trimEnd('/') + "/")
        require(denyPaths.none { matches(it, path) }) { "施工路径被 deny_paths 禁止：" + path }
        require(allowPaths.any { matches(it, path) }) { "施工路径不在 allow_paths：" + path }
        require(allowOperations.contains(operation)) { "施工操作不在 allow_operations：" + operation }
        val current = CollaborationTaskStore(context).get(taskMessage.taskId) ?: error("协作任务不存在")
        if (current.status != CollaborationTaskRecord.STATUS_CONSTRUCTING) {
            requestConstruction(taskMessage.taskId, memberId)
        } else {
            require(current.constructionHolderAiMemberId == memberId) { "当前 Worker 未持有施工锁" }
        }
        val token = GitHubTokenStore(context).state().accessToken?.takeIf { it.isNotBlank() } ?: error("GitHub 尚未授权")
        val service = GitHubWorkspaceService(context, GitHubApiClient(context, token), workspace.github, workspace)
        val file = service.file(path)
        val sha = file.optString("sha").takeIf { it.isNotBlank() } ?: error("无法取得文件 SHA：" + path)
        val result = updateFile(taskMessage.taskId, memberId, path, content, commitMessage, sha)
        val commitSha = result.optJSONObject("commit")?.optString("sha").orEmpty()
        runCatching { verifyTask(taskMessage.taskId) }
            .onFailure { AppLogger.log(context, AppLogger.Category.COLLABORATION, "VERIFY_TRIGGER_FAILED", it.message ?: "verify trigger failed") }
        return CollaborationProtocol.commit(taskMessage.taskId, commitSha, commitMessage, listOf(path), "1 file changed")
    }
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

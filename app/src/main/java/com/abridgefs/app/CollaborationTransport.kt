package com.abridgefs.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class CollaborationTurn(
    val id: String = UUID.randomUUID().toString(),
    val speaker: String,
    val profileId: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

class CollaborationTransport(
    context: Context,
    workspaceId: String,
    conversationId: String
) {
    private val prefs = context.getSharedPreferences("collaboration_transport", Context.MODE_PRIVATE)
    private val key = "turns_" + workspaceId.ifBlank { "unknown_workspace" } + "_" + conversationId.ifBlank { "unknown_conversation" }

    @Synchronized
    fun append(turn: CollaborationTurn) {
        val array = JSONArray(prefs.getString(key, "[]") ?: "[]")
        array.put(JSONObject()
            .put("id", turn.id)
            .put("speaker", turn.speaker)
            .put("profileId", turn.profileId)
            .put("content", turn.content)
            .put("createdAt", turn.createdAt))
        prefs.edit().putString(key, array.toString()).apply()
    }

    @Synchronized
    fun all(): List<CollaborationTurn> {
        val array = JSONArray(prefs.getString(key, "[]") ?: "[]")
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(CollaborationTurn(
                    id = item.optString("id", UUID.randomUUID().toString()),
                    speaker = item.optString("speaker", "AI"),
                    profileId = item.optString("profileId", ""),
                    content = item.optString("content", ""),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis())
                ))
            }
        }
    }

    fun clear() {
        prefs.edit().remove(key).apply()
    }
}

data class CollaborationApiConfig(
    val profileId: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String
) {
    fun isConfigured(): Boolean = baseUrl.isNotBlank() && model.isNotBlank()

    companion object {
        fun fromProfile(context: Context, profileId: String): CollaborationApiConfig {
            val profile = ApiProfileStore(context).find(profileId)
                ?: return CollaborationApiConfig(profileId, "", "", "")
            return CollaborationApiConfig(profile.id, profile.baseUrl.trim(), profile.key, profile.model.trim())
        }
    }
}

class CollaborationCoordinator(
    private val context: Context,
    private val workspaceId: String,
    private val conversationId: String,
    private val aiAProfileId: String,
    private val aiBProfileId: String
) {
    private val transport = CollaborationTransport(context, workspaceId, conversationId)

    fun runDiscussion(objective: String, maxTurns: Int = 4): List<CollaborationTurn> {
        require(objective.isNotBlank()) { "objective is blank" }
        require(aiAProfileId.isNotBlank() && aiBProfileId.isNotBlank()) { "请先选择两个协作 AI" }
        require(aiAProfileId != aiBProfileId) { "两个协作 AI 必须使用不同的 API Profile" }

        val taskStore = CollaborationTaskStore(context)
        val task = taskStore.create(workspaceId, conversationId, objective)
        taskStore.update(task.taskId) { it.status = CollaborationTaskRecord.STATUS_RUNNING }
        AppLogger.log(context, AppLogger.Category.COLLABORATION, "DISCUSSION_STARTED", "taskId=" + task.taskId)

        val turns = mutableListOf<CollaborationTurn>()
        var aTurn = true
        repeat(maxTurns.coerceIn(2, 8)) {
            val profileId = if (aTurn) aiAProfileId else aiBProfileId
            val speaker = if (aTurn) "AI A" else "AI B"
            val answer = call(profileId, buildPrompt(objective, turns, speaker))
            val turn = CollaborationTurn(speaker = speaker, profileId = profileId, content = answer)
            transport.append(turn)
            turns += turn
            aTurn = !aTurn
        }

        taskStore.update(task.taskId) { it.status = CollaborationTaskRecord.STATUS_WAITING_CONSTRUCTION }
        AppLogger.log(context, AppLogger.Category.COLLABORATION, "DISCUSSION_READY",
            "taskId=" + task.taskId + " turns=" + turns.size)
        return turns
    }

    private fun call(profileId: String, prompt: String): String {
        val config = CollaborationApiConfig.fromProfile(context, profileId)
        require(config.isConfigured()) { "协作 AI 的 API 未配置完整" }
        val name = if (profileId == aiAProfileId) "AI A" else "AI B"
        return BridgeApiClient(BridgeApiConfig(config.baseUrl, config.apiKey, config.model)).chat(
            listOf(BridgeChatMessage("user", prompt)),
            "你是 $name，是平等的协作参与者。自然讨论问题，不要输出 JSON 协议，不要假装自己是 Decision AI、Worker AI、Planner 或 Reviewer。可以提出方案、质疑另一位 AI、指出风险，也可以说明需要用户决定什么。当前只是讨论阶段，不要自行修改 GitHub 文件。"
        )
    }

    private fun buildPrompt(objective: String, turns: List<CollaborationTurn>, speaker: String): String {
        val transcript = turns.joinToString("\n\n") { it.speaker + "：" + it.content }
            .ifBlank { "（这是第一轮讨论，暂无其他发言。）" }
        return ("用户目标：\n" + objective +
            "\n\n当前讨论记录：\n" + transcript +
            "\n\n现在轮到 " + speaker + " 发言。\n" +
            "请基于上面的真实讨论继续思考，不要重复内部规则。直接给出你的判断、补充、质疑或建议。" +
            "如果已经形成明确方案，可以说明方案已经足够明确；如果必须由用户选择，请明确指出需要用户决定的问题。").trim()
    }

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
     * User-selected construction holder.
     *
     * The user is the authority that chooses which AI Member holds the
     * Repository/Branch ConstructionLock. Selecting the other AI transfers
     * the existing lock instead of creating a second lock.
     */
    fun selectConstructionHolder(taskId: String, aiMemberId: String): CollaborationTaskRecord {
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

        val locks = ConstructionLockStore(context)
        val current = locks.get(workspace)
        val lock = when {
            current == null || current.isFree -> locks.acquire(workspace, aiMemberId)
            current.heldBy(aiMemberId) -> current
            else -> locks.transfer(workspace, current.holderAiMemberId.orEmpty(), aiMemberId)
        }

        taskStore.update(taskId) {
            it.status = CollaborationTaskRecord.STATUS_CONSTRUCTING
            it.constructionRequestedByAiMemberId = aiMemberId
            it.constructionHolderAiMemberId = lock.holderAiMemberId
        }
        AppLogger.log(
            context,
            AppLogger.Category.COLLABORATION,
            "CONSTRUCTION_SELECTED_BY_USER",
            "taskId=$taskId holder=$aiMemberId"
        )
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
            it.pendingWriteBlobSha = gitBlobSha(content)
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
            it.pendingWriteBlobSha = null
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
        val expectedBlobSha = task.pendingWriteBlobSha?.takeIf { it.isNotBlank() }
            ?: error("施工恢复缺少预期 Blob SHA")
        val workspace = BridgeProjectStore(context).load().firstOrNull { it.id == task.workspaceId }
            ?: error("工作区不存在：" + task.workspaceId)
        val holder = task.constructionHolderAiMemberId?.takeIf { it.isNotBlank() }
            ?: error("施工恢复缺少施工者")
        ConstructionLockStore(context).requireHolder(workspace, holder)

        val token = GitHubTokenStore(context).state().accessToken?.takeIf { it.isNotBlank() }
            ?: error("GitHub 尚未授权")
        val service = GitHubWorkspaceService(context, GitHubApiClient(context, token), workspace.github, workspace)
        val remote = service.file(path)
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
            it.pendingWriteBlobSha = null
            it.pendingWriteMessage = null
            it.pendingWriteSha = null
        }
        AppLogger.log(context, AppLogger.Category.COLLABORATION, "GITHUB_WRITE_RECOVERED",
            "taskId=" + taskId + " commit=" + branchHead + " path=" + path)
    }


    private fun gitBlobSha(content: String): String {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val header = ("blob " + bytes.size + "\u0000").toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-1").digest(header + bytes)
            .joinToString("") { "%02x".format(it) }
    }
}

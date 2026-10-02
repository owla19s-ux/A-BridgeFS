package com.abridgefs.app

import android.content.Context
import org.json.JSONObject

enum class GitHubVerifyState { WAITING, PASSED, FAILED }

data class GitHubVerifyResult(
    val state: GitHubVerifyState,
    val commitSha: String,
    val runId: Long? = null,
    val runStatus: String? = null,
    val conclusion: String? = null,
    val message: String,
    val runUrl: String? = null
)

/**
 * Verifies a collaboration task against the real GitHub Actions run for its
 * exact Commit SHA. A missing run is WAITING, never PASS.
 */
class CollaborationVerifyService(private val context: Context) {

    fun verify(taskId: String): GitHubVerifyResult {
        val taskStore = CollaborationTaskStore(context)
        val task = taskStore.get(taskId) ?: error("协作任务不存在：${taskId}")
        val commitSha = task.lastCommitSha?.takeIf { it.isNotBlank() }
            ?: error("协作任务尚未产生 Commit SHA")

        val workspace = BridgeProjectStore(context).load()
            .firstOrNull { it.id == task.workspaceId }
            ?: error("工作区不存在：\${task.workspaceId}")

        val token = GitHubTokenStore(context).state().accessToken
            ?.takeIf { it.isNotBlank() }
            ?: error("GitHub 尚未授权")

        val service = GitHubWorkspaceService(
            context,
            GitHubApiClient(context, token),
            workspace.github,
            workspace
        )
        val array = service.workflowRunsForCommit(commitSha).optJSONArray("workflow_runs")
        val run = findExactRun(array, commitSha)

        if (run == null) {
            taskStore.update(taskId) {
                it.status = CollaborationTaskRecord.STATUS_WAITING_VERIFY
            }
            return GitHubVerifyResult(
                GitHubVerifyState.WAITING,
                commitSha,
                message = "Commit 已存在，但尚未找到对应 GitHub Actions Run"
            )
        }

        val runId = run.optLong("id", 0L).takeIf { it > 0L }
        val status = run.optString("status", "").ifBlank { null }
        val conclusion = run.optString("conclusion", "").ifBlank { null }
        val url = run.optString("html_url", "").ifBlank { null }

        return when {
            status != "completed" -> {
                taskStore.update(taskId) {
                    it.status = CollaborationTaskRecord.STATUS_WAITING_VERIFY
                }
                GitHubVerifyResult(
                    GitHubVerifyState.WAITING, commitSha, runId, status, conclusion,
                    "GitHub Actions 正在运行：${status ?: "unknown"}", url
                )
            }
            conclusion == "success" -> {
                releaseAfterSuccess(workspace, taskStore, task)
                recordReceipt(workspace, task, "VERIFY_PASS", "GitHub Actions", "Verify 通过：$commitSha")
                GitHubVerifyResult(
                    GitHubVerifyState.PASSED, commitSha, runId, status, conclusion,
                    "GitHub Actions Verify 通过", url
                )
            }
            else -> {
                taskStore.update(taskId) {
                    it.status = CollaborationTaskRecord.STATUS_FAILED
                }
                recordReceipt(
                    workspace, task, "VERIFY_FAIL", "GitHub Actions",
                    "Verify 失败：${conclusion ?: "unknown"}；Commit=$commitSha"
                )
                GitHubVerifyResult(
                    GitHubVerifyState.FAILED, commitSha, runId, status, conclusion,
                    "GitHub Actions Verify 未通过：${conclusion ?: "unknown"}", url
                )
            }
        }
    }

    private fun findExactRun(array: org.json.JSONArray?, commitSha: String): JSONObject? {
        if (array == null) return null
        for (i in 0 until array.length()) {
            val run = array.optJSONObject(i) ?: continue
            if (run.optString("head_sha") == commitSha) return run
        }
        return null
    }

    private fun releaseAfterSuccess(
        workspace: BridgeProject,
        taskStore: CollaborationTaskStore,
        task: CollaborationTaskRecord
    ) {
        val holder = task.constructionHolderAiMemberId
        if (!holder.isNullOrBlank()) {
            ConstructionLockStore(context).release(workspace, holder)
        }
        taskStore.update(task.taskId) {
            it.status = CollaborationTaskRecord.STATUS_COMPLETE
            it.constructionHolderAiMemberId = null
        }
    }

    private fun recordReceipt(
        workspace: BridgeProject,
        task: CollaborationTaskRecord,
        status: String,
        command: String,
        message: String
    ) {
        val conversation = workspace.conversations.firstOrNull { it.id == task.conversationId } ?: return
        conversation.executions += BridgeReceiptRecord(status, command, message)
        BridgeProjectStore(context).save(listOf(workspace))
    }
}

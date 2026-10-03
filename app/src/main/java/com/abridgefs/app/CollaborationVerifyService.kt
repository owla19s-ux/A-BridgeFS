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

        // A Commit is only an active Verify target while the task is waiting
        // for that Commit's CI result. Once the task is COMPLETE or FAILED,
        // re-checking the same historical SHA must not mutate the task back
        // into a different state or create duplicate terminal receipts.
        when (task.status) {
            CollaborationTaskRecord.STATUS_VERIFY_PASSED,
            CollaborationTaskRecord.STATUS_COMPLETE -> {
                return GitHubVerifyResult(
                    GitHubVerifyState.PASSED,
                    commitSha,
                    message = "该 Commit 已经 Verify 通过，任务已完成；不重复执行 Verify"
                )
            }
            CollaborationTaskRecord.STATUS_FAILED -> {
                return GitHubVerifyResult(
                    GitHubVerifyState.FAILED,
                    commitSha,
                    message = "该 Commit 已经 Verify 失败，请进入修复轮后再产生新的 Commit"
                )
            }
            CollaborationTaskRecord.STATUS_WAITING_VERIFY -> Unit
            CollaborationTaskRecord.STATUS_CONSTRUCTION_WRITING -> {
                return GitHubVerifyResult(
                    GitHubVerifyState.WAITING,
                    commitSha,
                    message = "GitHub 写入处于恢复阶段，先核对远端文件与 Commit；不重复写入"
                )
            }
            else -> {
                return GitHubVerifyResult(
                    GitHubVerifyState.WAITING,
                    commitSha,
                    message = "当前任务尚未进入该 Commit 的 Verify 阶段"
                )
            }
        }

        val workspace = BridgeProjectStore(context).load()
            .firstOrNull { it.id == task.workspaceId }
            ?: error("工作区不存在：${task.workspaceId}")

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

        // Verify must come from the formal Android Release workflow, not merely
        // any Actions run attached to the same Commit. Multiple workflows or
        // manual dispatches may exist for one SHA, so select deterministically:
        // exact SHA -> formal workflow -> newest run.
        val candidates = buildList {
            for (i in 0 until array.length()) {
                val run = array.optJSONObject(i) ?: continue
                if (run.optString("head_sha") != commitSha) continue

                val workflowPath = run.optString("path").trim()
                val workflowName = run.optString("name").trim()
                if (workflowPath != FORMAL_VERIFY_WORKFLOW_PATH &&
                    workflowName != FORMAL_VERIFY_WORKFLOW_NAME
                ) continue

                add(run)
            }
        }

        return candidates.maxByOrNull {
            runTimestamp(it, "created_at")
        }
    }

    private fun runTimestamp(run: JSONObject, field: String): Long {
        return run.optString(field).trim().let {
            runCatching {
                java.time.Instant.parse(it).toEpochMilli()
            }.getOrDefault(0L)
        }
    }

    companion object {
        private const val FORMAL_VERIFY_WORKFLOW_NAME = "Android Build and Release"
        private const val FORMAL_VERIFY_WORKFLOW_PATH = ".github/workflows/android-build.yml"
    }

    private fun releaseAfterSuccess(
        workspace: BridgeProject,
        taskStore: CollaborationTaskStore,
        task: CollaborationTaskRecord
    ) {
        val holder = task.constructionHolderAiMemberId
        if (!holder.isNullOrBlank()) {
            runCatching {
                val lockStore = ConstructionLockStore(context)
                val lock = lockStore.get(workspace)
                if (lock?.heldBy(holder) == true) {
                    lockStore.release(workspace, holder)
                } else {
                    AppLogger.log(context, AppLogger.Category.COLLABORATION, "VERIFY_LOCK_ALREADY_RELEASED", "taskId=" + task.taskId + " holder=" + holder)
                }
            }.onFailure {
                AppLogger.log(context, AppLogger.Category.COLLABORATION, "VERIFY_LOCK_RELEASE_FAILED", "taskId=" + task.taskId + " holder=" + holder + " error=" + (it.message ?: "unknown"))
            }
        }
        taskStore.update(task.taskId) {
            it.status = CollaborationTaskRecord.STATUS_VERIFY_PASSED
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

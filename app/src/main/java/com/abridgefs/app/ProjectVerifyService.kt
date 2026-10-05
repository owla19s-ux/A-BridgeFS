package com.abridgefs.app

/**
 * Verify boundary for Project GitHub state.
 * Keeps workflow/run interpretation outside CommandExecutor.
 */
class ProjectVerifyService(
    private val github: ProjectGitHubService
) {
    data class Result(
        val commitSha: String,
        val runId: Long? = null,
        val status: String? = null,
        val conclusion: String? = null,
        val state: State
    ) {
        enum class State { NOT_QUERIED, NOT_TRIGGERED, RUNNING, PASSED, FAILED }
    }

    fun forCommit(commitSha: String): Result {
        if (commitSha.isBlank()) return Result(commitSha, state = Result.State.NOT_QUERIED)
        val payload = github.workflowRunsForCommit(commitSha, 10)
        val runs = payload.optJSONArray("workflow_runs")
        if (runs == null || runs.length() == 0) {
            return Result(commitSha, state = Result.State.NOT_TRIGGERED)
        }
        val run = runs.optJSONObject(0)
        val runId = run?.optLong("id")?.takeIf { it > 0L }
        val status = run?.optString("status")?.takeIf { it.isNotBlank() && it != "null" }
        val conclusion = run?.optString("conclusion")?.takeIf { it.isNotBlank() && it != "null" }
        val state = when {
            conclusion == "success" -> Result.State.PASSED
            conclusion != null -> Result.State.FAILED
            status != null -> Result.State.RUNNING
            else -> Result.State.NOT_QUERIED
        }
        return Result(commitSha, runId, status, conclusion, state)
    }

    fun receiptLine(result: Result): String = when (result.state) {
        Result.State.NOT_QUERIED -> "  — Verify：未查询"
        Result.State.NOT_TRIGGERED -> "  — Verify：未触发（当前 Commit 没有对应 Actions Run）"
        Result.State.RUNNING -> "  — Verify：Run #${result.runId ?: 0} status=${result.status ?: "unknown"}，仍在运行"
        Result.State.PASSED -> "  ✓ Verify：Run #${result.runId ?: 0} status=${result.status ?: "completed"} conclusion=success"
        Result.State.FAILED -> "  ✗ Verify：Run #${result.runId ?: 0} status=${result.status ?: "completed"} conclusion=${result.conclusion ?: "failed"}"
    }
}

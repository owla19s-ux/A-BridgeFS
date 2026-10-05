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
        val jobCount: Int = 0,
        val failedJobCount: Int = 0,
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
        val base = Result(commitSha, runId, status, conclusion, state = state)
        return withJobSummary(base)
    }

    private fun withJobSummary(result: Result): Result {
        val jobs = result.runId?.let { github.workflowRun(it).optJSONArray("jobs") }
        if (jobs == null) return result
        var failed = 0
        for (index in 0 until jobs.length()) {
            val job = jobs.optJSONObject(index)
            val jobConclusion = job?.optString("conclusion")?.takeIf { it.isNotBlank() && it != "null" }
            if (jobConclusion != null && jobConclusion != "success" && jobConclusion != "skipped") {
                failed++
            }
        }
        return result.copy(jobCount = jobs.length(), failedJobCount = failed)
    }

    fun jobs(result: Result): org.json.JSONArray? = result.runId?.let { github.workflowRun(it).optJSONArray("jobs") }

    fun receiptLine(result: Result): String = when (result.state) {
        Result.State.NOT_QUERIED -> "  — Verify：未查询"
        Result.State.NOT_TRIGGERED -> "  — Verify：未触发（当前 Commit 没有对应 Actions Run）"
        Result.State.RUNNING -> "  — Verify：Run #${result.runId ?: 0} status=${result.status ?: "unknown"}，仍在运行"
        Result.State.PASSED -> "  ✓ Verify：Run #${result.runId ?: 0} status=${result.status ?: "completed"} conclusion=success\n  — Jobs：${result.jobCount}，失败 ${result.failedJobCount}"
        Result.State.FAILED -> "  ✗ Verify：Run #${result.runId ?: 0} status=${result.status ?: "completed"} conclusion=${result.conclusion ?: "failed"}\n  — Jobs：${result.jobCount}，失败 ${result.failedJobCount}"
    }
}

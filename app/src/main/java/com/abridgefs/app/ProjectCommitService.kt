package com.abridgefs.app

import java.io.File

/**
 * Project Commit business boundary.
 * Owns local-file -> Project GitHub commit and immediate Verify lookup.
 */
class ProjectCommitService(
    private val context: android.content.Context,
    private val project: BridgeProject,
    private val aiMemberId: String
) {
    data class Result(
        val commitSha: String,
        val receipt: String
    )

    fun commit(localFile: File, relativePath: String, message: String): Result {
        val activeTaskId = project.taskState.activeTaskId
        val activeTask = activeTaskId?.let { id ->
            project.tasks.firstOrNull { it.id == id }
        }
        check(
            activeTask != null &&
                !activeTask.completed &&
                activeTask.status == "RUNNING" &&
                project.taskState.status == "RUNNING"
        ) {
            "当前 Project 没有处于 RUNNING 状态的施工 Task，禁止 Commit"
        }

        check(project.aiMembers.any { it.id == aiMemberId }) {
            "当前 AI Member 不属于该 Project，禁止 Commit"
        }

        val token = GitHubTokenStore(context).state().accessToken.orEmpty()
        val github = ProjectGitHubService(
            context,
            GitHubApiClient(context, token),
            project.githubAddress,
            project
        )
        val response = github.commitLocalFile(localFile, relativePath, message, aiMemberId)
        val sha = response.optJSONObject("commit")?.optString("sha").orEmpty()
        val verifyService = ProjectVerifyService(github)
        val verify = runCatching { verifyService.forCommit(sha) }.getOrNull()
        if (verify != null) {
            project.verifyRecords.removeAll { it.commitSha == verify.commitSha }
            project.verifyRecords += BridgeVerifyRecord(
                commitSha = verify.commitSha,
                runId = verify.runId,
                status = verify.status,
                conclusion = verify.conclusion,
                jobCount = verify.jobCount,
                failedJobCount = verify.failedJobCount,
                artifactCount = verify.artifactCount,
                artifactNames = verify.artifactNames,
                state = verify.state.name,
                time = System.currentTimeMillis()
            )
            BridgeProjectStore(context).saveProject(project)
        }
        val verifyLine = verify?.let { verifyService.receiptLine(it) }
            ?: "  — Verify：未查询"
        val commitLine = if (sha.isBlank()) "已创建" else sha
        return Result(
            commitSha = sha,
            receipt = "[Tool: Commit] $relativePath
  ✓ 已提交到 GitHub
  ✓ Commit: $commitLine
$verifyLine"
        )
    }
}

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
        val token = GitHubTokenStore(context).state().accessToken.orEmpty()
        val github = ProjectGitHubService(
            context,
            GitHubApiClient(context, token),
            project.githubAddress,
            project
        )
        val response = github.commitLocalFile(localFile, relativePath, message, aiMemberId)
        val sha = response.optJSONObject("commit")?.optString("sha").orEmpty()
        val verify = runCatching { ProjectVerifyService(github).forCommit(sha) }.getOrNull()
        val verifyLine = verify?.let { ProjectVerifyService(github).receiptLine(it) }
            ?: "  — Verify：未查询"
        val commitLine = if (sha.isBlank()) "已创建" else sha
        return Result(
            commitSha = sha,
            receipt = "[Tool: Commit] $relativePath\n  ✓ 已提交到 GitHub\n  ✓ Commit: $commitLine\n$verifyLine"
        )
    }
}

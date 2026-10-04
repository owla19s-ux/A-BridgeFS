package com.abridgefs.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Workspace boundary for GitHub collaboration.
 *
 * UI/business code uses this boundary for workspace-scoped GitHub reads and
 * permissions instead of constructing repository/branch access rules itself.
 */
class GitHubWorkspaceService(
    private val context: android.content.Context,
    private val client: GitHubApiClient,
    private val workspace: GitHubWorkspace,
    private val project: BridgeProject? = null
) {
    fun currentAccount(): JSONObject {
        requireRead()
        return client.getCurrentUser()
    }

    fun repositories(perPage: Int = 100): JSONArray {
        requireRead()
        return client.listRepositories(perPage)
    }

    fun repository(): JSONObject {
        requireRead()
        val (owner, name) = repositoryParts()
        return client.getRepository(owner, name)
    }

    fun branches(perPage: Int = 100): JSONArray {
        requireRead()
        val (owner, name) = repositoryParts()
        return client.listBranches(owner, name, perPage)
    }

    fun branchHead(): String {
        requireRead()
        val (owner, name) = repositoryParts()
        val branch = workspace.branch?.takeIf { it.isNotBlank() } ?: error("GitHub Branch 未配置")
        return client.getBranch(owner, name, branch).optJSONObject("commit")?.optString("sha").orEmpty()
    }

    fun commit(commitSha: String): JSONObject {
        requireRead()
        val (owner, name) = repositoryParts()
        return client.getCommit(owner, name, commitSha)
    }

    fun file(path: String): JSONObject {
        requireRead()
        val (owner, name) = repositoryParts()
        return client.getFile(owner, name, path, workspace.branch)
    }

    fun workflowRuns(perPage: Int = 20): JSONObject {
        requireRead()
        val (owner, name) = repositoryParts()
        return client.listWorkflowRuns(owner, name, perPage)
    }

    fun workflowRunsForCommit(commitSha: String, perPage: Int = 20): JSONObject {
        requireRead()
        require(commitSha.isNotBlank()) { "Commit SHA 不能为空" }
        val (owner, name) = repositoryParts()
        return client.listWorkflowRunsForCommit(owner, name, commitSha, perPage)
    }

    fun workflowRun(runId: Long): JSONObject {
        requireRead()
        val (owner, name) = repositoryParts()
        return client.getWorkflowRun(owner, name, runId)
    }

    /**
     * Permission boundary for future write APIs.
     *
     * This method only checks permission; it does not claim that a write
     * operation is currently implemented.
     */
    /**
     * Workspace-scoped GitHub file update boundary.
     *
     * Callers use this service so the workspace read/write boundary is checked
     * before a repository write reaches the low-level GitHub client.
     */
    fun updateFile(
        path: String,
        content: String,
        message: String,
        sha: String,
        aiMemberId: String
    ): JSONObject {
        requireWritePermission(aiMemberId)
        val (owner, name) = repositoryParts()
        return client.updateFile(owner, name, path, content, message, workspace.branch, sha)
    }

    /**
     * Backward-compatible guard for callers that only need to inspect whether
     * the workspace permits GitHub writes. Actual file writes require an
     * AI Member construction lock through the overload above.
     */
    fun requireWritePermission() {
        requireRead()
        check(workspace.writeEnabled) { "当前工作区未允许 GitHub 修改" }
    }

    fun requireWritePermission(aiMemberId: String) {
        requireWritePermission()
        ConstructionLockStore(context).requireHolder(project ?: error("GitHub 写入必须绑定工作区"), aiMemberId)
    }

    private fun requireRead() {
        check(workspace.readEnabled) { "当前工作区未允许 GitHub 读取" }
    }

    private fun repositoryParts(): Pair<String, String> {
        check(workspace.isConfigured()) { "GitHub Repository 未配置" }
        val fullName = workspace.repository!!.trim()
        val parts = fullName.split('/', limit = 2)
        check(parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            "GitHub Repository 格式应为 owner/name"
        }
        return parts[0] to parts[1]
    }
}

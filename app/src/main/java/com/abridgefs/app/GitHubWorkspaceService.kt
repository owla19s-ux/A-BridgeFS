package com.abridgefs.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Workspace boundary for GitHub collaboration.
 *
 * The UI should bind to this layer instead of knowing GitHub REST paths.
 * Authorization/account remains owned by GitHubApiClient; repository/branch and
 * read/write permissions belong to the workspace.
 */
class GitHubWorkspaceService(
    private val client: GitHubApiClient,
    private val workspace: GitHubWorkspace
) {
    fun requireConfigured() {
        check(workspace.isConfigured()) { "GitHub Repository 未配置" }
        check(workspace.readEnabled) { "当前工作区未允许 GitHub 读取" }
    }

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
        val fullName = requireRepository()
        val parts = splitRepository(fullName)
        return client.getRepository(parts.first, parts.second)
    }

    fun branches(perPage: Int = 100): JSONArray {
        requireRead()
        val fullName = requireRepository()
        val parts = splitRepository(fullName)
        return client.listBranches(parts.first, parts.second, perPage)
    }

    fun file(path: String): JSONObject {
        requireRead()
        val fullName = requireRepository()
        val parts = splitRepository(fullName)
        return client.getFile(parts.first, parts.second, path, workspace.branch)
    }

    fun workflowRuns(perPage: Int = 20): JSONObject {
        requireRead()
        val fullName = requireRepository()
        val parts = splitRepository(fullName)
        return client.listWorkflowRuns(parts.first, parts.second, perPage)
    }

    fun workflowRun(runId: Long): JSONObject {
        requireRead()
        val fullName = requireRepository()
        val parts = splitRepository(fullName)
        return client.getWorkflowRun(parts.first, parts.second, runId)
    }

    /**
     * Write operations are intentionally blocked until a concrete write API is
     * added. This prevents the workspace's "允许修改" switch from implying
     * capabilities that the client does not yet implement.
     */
    fun requireWritePermission() {
        requireRead()
        check(workspace.writeEnabled) { "当前工作区未允许 GitHub 修改" }
    }

    private fun requireRead() {
        requireConfigured()
    }

    private fun requireRepository(): String =
        workspace.repository?.trim().orEmpty().also {
            check(it.count { c -> c == '/' } == 1) {
                "GitHub Repository 格式应为 owner/name"
            }
        }

    private fun splitRepository(fullName: String): Pair<String, String> {
        val parts = fullName.split('/', limit = 2)
        check(parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            "GitHub Repository 格式应为 owner/name"
        }
        return parts[0] to parts[1]
    }
}

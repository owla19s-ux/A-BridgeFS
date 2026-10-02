package com.abridgefs.app

/**
 * Workspace-level GitHub collaboration resource.
 *
 * Repository binding and permissions belong to the workspace.
 * Authentication/token storage remains owned by the GitHub authorization layer.
 */
data class GitHubWorkspace(
    var accountLogin: String? = null,
    var repositoryId: Long? = null,
    var repository: String? = null,
    var branch: String? = null,
    var readEnabled: Boolean = true,
    var writeEnabled: Boolean = false
) {
    fun isConfigured(): Boolean = !repository.isNullOrBlank()

    fun displayRepository(): String = repository?.takeIf { it.isNotBlank() } ?: "未连接 Repository"
    fun displayBranch(): String = branch?.takeIf { it.isNotBlank() } ?: "未设置 Branch"
    fun displayAccount(): String = accountLogin?.takeIf { it.isNotBlank() } ?: "未授权 GitHub 账号"
}

package com.abridgefs.app

/**
 * Workspace-level GitHub collaboration resource.
 *
 * Account/authorization is intentionally kept separate from repository binding.
 * The token is not persisted here; it belongs to the GitHub authorization layer.
 */
data class GitHubWorkspace(
    var accountLogin: String? = null,
    var repository: String? = null,
    var branch: String? = null,
    var readEnabled: Boolean = true,
    var writeEnabled: Boolean = false
) {
    fun isConfigured(): Boolean =
        !repository.isNullOrBlank()

    fun displayRepository(): String =
        repository?.takeIf { it.isNotBlank() } ?: "未连接 Repository"

    fun displayBranch(): String =
        branch?.takeIf { it.isNotBlank() } ?: "未设置 Branch"

    fun displayAccount(): String =
        accountLogin?.takeIf { it.isNotBlank() } ?: "未授权 GitHub 账号"
}

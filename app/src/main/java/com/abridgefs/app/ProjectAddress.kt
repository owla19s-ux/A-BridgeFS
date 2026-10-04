package com.abridgefs.app

/**
 * GitHub-backed Project Address.
 *
 * GitHub is an address/resource type of Project, not a separate Workspace.
 */
data class ProjectGitHubAddress(
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

/**
 * Source-compatibility alias only. New code uses ProjectGitHubAddress.
 */
@Deprecated("Use ProjectGitHubAddress")
typealias GitHubWorkspace = ProjectGitHubAddress

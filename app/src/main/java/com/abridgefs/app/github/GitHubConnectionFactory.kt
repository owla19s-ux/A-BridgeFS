package com.abridgefs.app.github

import com.abridgefs.app.context.Context
import com.abridgefs.app.context.ResourceRef

/**
 * 从工作 Context 装配运行时 GitHub Connection。
 *
 * 只负责 Context Resource -> Connection 的边界转换；
 * 不处理凭证、权限、Task、ConstructionLock 或 API 调用。
 */
object GitHubConnectionFactory {
    fun create(context: Context): GitHubConnection {
        val repositories = context.resources
            .mapNotNull { (it as? ResourceRef.GitHub)?.resource as? GitHubResource.Repository }

        require(repositories.size == 1) {
            when {
                repositories.isEmpty() -> "Context 未关联 GitHub Repository"
                else -> "Context 关联了多个 GitHub Repository，无法确定当前 Connection"
            }
        }

        val repository = repositories.single()
        val branches = context.resources
            .mapNotNull { (it as? ResourceRef.GitHub)?.resource as? GitHubResource.Branch }
            .filter { it.repositoryId == repository.id }

        require(branches.size <= 1) {
            "Context 为当前 GitHub Repository 关联了多个 Branch，无法确定当前 Connection"
        }

        val branch = branches.singleOrNull()?.name ?: repository.defaultBranch
        return GitHubConnection(
            id = "github:repository:" + repository.id,
            address = GitHubAddress(repository.fullName, branch)
        )
    }
}

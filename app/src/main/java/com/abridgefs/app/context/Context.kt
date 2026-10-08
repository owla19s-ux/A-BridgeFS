package com.abridgefs.app.context

import com.abridgefs.app.github.GitHubResource

/**
 * APS 的具体工作上下文。
 *
 * Context 只保存工作上下文自身的稳定身份、已关联资源以及可选的当前 AI Connection。
 * Task、Conversation、Dispatcher 等能力按场景组合，不作为固定字段。
 */
data class Context(
    val id: String,
    val name: String,
    val resources: List<ResourceRef> = emptyList(),
    val aiConnectionId: String? = null
) {
    init {
        require(id.isNotBlank()) { "Context ID 不能为空" }
        require(name.isNotBlank()) { "Context 名称不能为空" }
        require(aiConnectionId?.isNotBlank() != false) { "AI Connection ID 不能为空" }
    }

    fun withResource(resource: ResourceRef): Context =
        copy(resources = resources.filterNot { it.key == resource.key } + resource)

    fun withAIConnection(connectionId: String): Context =
        copy(aiConnectionId = connectionId)
}

sealed interface ResourceRef {
    val key: String

    data class GitHub(
        val resource: GitHubResource
    ) : ResourceRef {
        override val key: String = when (resource) {
            is GitHubResource.Repository -> "github:repository:" + resource.id
            is GitHubResource.Branch -> "github:branch:" + resource.repositoryId + ":" + resource.name
            is GitHubResource.File -> "github:file:" + resource.path
            is GitHubResource.Commit -> "github:commit:" + resource.ref
        }
    }
}

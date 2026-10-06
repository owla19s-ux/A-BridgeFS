package com.abridgefs.app

import android.content.Context

/**
 * Owns mutable Project configuration.
 *
 * UI may request changes through this service; it does not own Project
 * persistence or configuration invariants.
 */
class ProjectConfigurationService(private val context: Context) {
    private val store = BridgeProjectStore(context)

    fun rename(projectId: String, name: String): BridgeProject {
        val project = requireProject(projectId)
        project.name = name.trim().ifBlank { "默认项目" }
        store.saveProject(project)
        return project
    }

    fun updateAddress(
        projectId: String,
        localAddress: String?,
        repository: String?,
        branch: String?
    ): BridgeProject {
        val project = requireProject(projectId)
        project.localAddress = localAddress?.trim()?.ifBlank { null }
        project.githubAddress.repository = repository?.trim()?.ifBlank { null }
        project.githubAddress.branch = branch?.trim()?.ifBlank { null }
        store.saveProject(project)
        return project
    }

    fun bindDefaultApi(projectId: String, profile: ApiProfile): BridgeProject {
        val project = requireProject(projectId)
        val member = project.defaultMemberId
            ?.let { id -> project.aiMembers.firstOrNull { it.id == id } }
            ?: BridgeAiMember(
                id = java.util.UUID.randomUUID().toString(),
                name = profile.name,
                apiProfileId = profile.id
            ).also {
                project.aiMembers += it
                project.defaultMemberId = it.id
            }

        member.name = profile.name
        member.apiProfileId = profile.id
        project.activeConversation().apiId = profile.id
        store.saveProject(project)
        return project
    }

    private fun requireProject(projectId: String): BridgeProject =
        store.load().firstOrNull { it.id == projectId }
            ?: error("Project 不存在：$projectId")
}

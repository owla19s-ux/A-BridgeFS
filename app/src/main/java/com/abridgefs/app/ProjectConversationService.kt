package com.abridgefs.app

import android.content.Context

class ProjectConversationService(private val context: Context) {
    data class Result(
        val answer: String? = null,
        val error: String? = null,
        val isGithubReadError: Boolean = false,
        val executionRequested: Boolean = false
    )

    private val apiProfiles by lazy { ApiProfileStore(context) }

    fun send(project: BridgeProject, conversation: BridgeConversation, userText: String): Result {
        if (!AccessPolicy.isApiEnabled(context)) return Result(error = "API access disabled")
        val member = project.defaultMemberId?.let { id -> project.aiMembers.firstOrNull { it.id == id } } ?: project.aiMembers.firstOrNull()
        val profile = conversation.apiId?.let { apiProfiles.find(it) }
            ?: member?.apiProfileId?.let { apiProfiles.find(it) }
            ?: return Result(error = "No default AI configured")

        val read = GitHubConversationReader(context).readForProject(project, userText)
        if (read.error != null) return Result(error = "[GitHub error]\n" + read.error, isGithubReadError = true)

        val info = "Project: " + project.name +
            "\nRepository: " + project.githubAddress.repository +
            "\nBranch: " + project.githubAddress.branch

        val prompt = "You are the default AI for this Project. " +
            "The conversation layer cannot perform Android local file operations. " +
            "Project changes must use the GitHub project workflow. " +
            "Use GitHub Actions and Verify results as evidence.\n\n" +
            info +
            "\n\n" + read.content

        return runCatching {
            val answer = BridgeApiClient(
                BridgeApiConfig(profile.baseUrl.trimEnd('/'), profile.key, profile.model)
            ).chat(conversation.messages, prompt)
            Result(answer = answer)
        }.getOrElse {
            Result(error = "[API error]\n" + (it.message ?: "unknown"))
        }
    }
}


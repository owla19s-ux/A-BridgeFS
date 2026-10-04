package com.abridgefs.app

import android.content.Context

/**
 * Project Conversation business boundary.
 *
 * Resolves the Project Default AI, gathers authorized Project Address context,
 * and performs the API conversation. UI rendering and command execution remain
 * outside this service.
 */
class ProjectConversationService(private val context: Context) {
    data class Result(
        val answer: String? = null,
        val error: String? = null,
        val isGithubReadError: Boolean = false
    )

    private val apiProfiles by lazy { ApiProfileStore(context) }

    fun send(project: BridgeProject, conversation: BridgeConversation, userText: String): Result {
        if (!AccessPolicy.isApiEnabled(context)) {
            return Result(error = "API 全局访问已关闭")
        }

        val member = project.defaultMemberId?.let { id ->
            project.aiMembers.firstOrNull { it.id == id }
        } ?: project.aiMembers.firstOrNull()

        val profile = member?.apiProfileId?.let { id -> apiProfiles.find(id) }
            ?: return Result(error = "当前 Project 尚未配置默认 AI")

        val address = project.localAddress?.trim().orEmpty()
        val githubRepository = project.githubAddress.repository?.trim().orEmpty()
        val branch = project.githubAddress.branch?.trim().orEmpty()
        val projectInfo = buildString {
            append("当前 Project：").append(project.name)
            if (address.isNotBlank()) append("\nLocal Project Address：").append(address)
            if (githubRepository.isNotBlank()) {
                append("\nGitHub Repository：").append(githubRepository)
                if (branch.isNotBlank()) append("\nGitHub Branch：").append(branch)
            }
        }

        val githubRead = GitHubConversationReader(context).readForProject(project, userText)
        if (githubRead.error != null) {
            return Result(
                error = "[GitHub 错误]\n" + githubRead.error,
                isGithubReadError = true
            )
        }

        val githubPrompt = if (githubRead.content.isNotBlank()) {
            "\n\n[Project GitHub 只读资料]\nRepository: " + githubRead.repository +
                "\nBranch: " + (githubRead.branch ?: "默认分支") +
                "\n以下内容来自当前 Project Address，仅用于本轮回答；不要执行任何修改操作。\n\n" +
                githubRead.content
        } else ""

        return runCatching {
            val answer = BridgeApiClient(
                BridgeApiConfig(profile.baseUrl.trimEnd('/'), profile.key, profile.model)
            ).chat(
                conversation.messages,
                BridgeCommandSpec.aiSystemPrompt(
                    context.getSharedPreferences("bridgefs", Context.MODE_PRIVATE)
                        .getInt("command_limit", 3).coerceIn(1, 20)
                ) +
                    "\n\n[Project 信息]\n" + projectInfo +
                    githubPrompt +
                    "\n你是当前 Project 的默认 AI。先直接回答用户问题；只有用户明确要求执行工作时，才进入后续工作流程。不要自动启动其他 AI 协作，也不要恢复已经废弃的固定阶段角色模型。"
            )
            Result(
                answer = answer
            )
        }.getOrElse {
            Result(error = "[API 错误]\n" + (it.message ?: "未知错误"))
        }
    }
}

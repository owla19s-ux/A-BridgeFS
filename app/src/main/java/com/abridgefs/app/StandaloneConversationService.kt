package com.abridgefs.app

/**
 * Standalone API conversation boundary.
 *
 * This conversation is independent from Project construction. It may read
 * Project GitHub context, but it does not execute Android-local commands.
 */
class StandaloneConversationService(private val context: android.content.Context) {
    private val apiProfiles by lazy { ApiProfileStore(context) }

    data class Result(
        val answer: String? = null,
        val error: String? = null,
        val executionRequested: Boolean = false
    )

    fun send(
        conversation: BridgeConversation,
        userText: String,
        project: BridgeProject? = null
    ): Result {
        if (!AccessPolicy.isApiEnabled(context)) return Result(error = "API 全局访问已关闭")

        val profile = conversation.apiId?.let { apiProfiles.find(it) }
            ?: return Result(error = "当前对话尚未选择 API Profile")

        val githubRead = project?.let {
            GitHubConversationReader(context).readForProject(it, userText)
        }
        if (githubRead?.error != null) {
            return Result(error = "[GitHub 错误]\n" + githubRead.error)
        }

        val projectInfo = project?.let {
            buildString {
                append("\n\n[当前 Project 信息]")
                append("\nProject：").append(it.name)
                it.githubAddress.repository?.takeIf(String::isNotBlank)?.let { repo ->
                    append("\nGitHub：").append(repo)
                    it.githubAddress.branch?.takeIf(String::isNotBlank)?.let { b -> append("\nBranch：").append(b) }
                }
            }
        } ?: ""

        val githubPrompt = githubRead?.content?.takeIf(String::isNotBlank)?.let {
            "\n\n[Project GitHub 资料]\n" + it
        } ?: ""

        val systemPrompt =
            "你是 APS 的普通对话 AI。当前页面是独立对话。" +
            "\n可以参考附带的 Project/GitHub 资料回答问题。" +
            "\n当前对话不具备手机本地文件读写、编辑、目录操作能力。" +
            "\n不要生成或执行 [bridgefs] 指令。" +
            "\n如需修改 Project，只能通过 APS 当前提供的 GitHub 工作流，并以 Actions/Verify 结果为事实。"

        return runCatching {
            val answer = BridgeApiClient(
                BridgeApiConfig(profile.baseUrl.trimEnd('/'), profile.key, profile.model)
            ).chat(
                conversation.messages,
                systemPrompt + projectInfo + githubPrompt
            )
            Result(answer = answer)
        }.getOrElse {
            Result(error = "[API 错误]\n" + (it.message ?: "未知错误"))
        }
    }
}

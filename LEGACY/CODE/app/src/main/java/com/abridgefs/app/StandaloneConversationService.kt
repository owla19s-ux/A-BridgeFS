package com.abridgefs.app

/**
 * Standalone conversation boundary.
 *
 * It may read the current Project Address when GitHub access is enabled,
 * but it never inherits Project write/Construction authority.
 */
class StandaloneConversationService(private val context: android.content.Context) {
    private val apiProfiles by lazy { ApiProfileStore(context) }

    data class Result(val answer: String? = null, val error: String? = null)

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
                append("\n\n[当前 Project Address 只读信息]")
                append("\nProject：").append(it.name)
                it.localAddress?.takeIf(String::isNotBlank)?.let { path -> append("\nLocal：").append(path) }
                it.githubAddress.repository?.takeIf(String::isNotBlank)?.let { repo ->
                    append("\nGitHub：").append(repo)
                    it.githubAddress.branch?.takeIf(String::isNotBlank)?.let { b -> append("\nBranch：").append(b) }
                }
                append("\n此页面只能读取这些资料，不能因为读取 Project 而获得施工或写入权限。")
            }
        } ?: ""

        val githubPrompt = githubRead?.content?.takeIf(String::isNotBlank)?.let {
            "\n\n[Project GitHub 只读资料]\n" + it
        } ?: ""

        return runCatching {
            val answer = BridgeApiClient(
                BridgeApiConfig(profile.baseUrl.trimEnd('/'), profile.key, profile.model)
            ).chat(
                conversation.messages,
                "你是 APS 的普通对话 AI。当前页面是独立对话，不属于 Project 施工会话。" +
                    "\n可以参考附带的 Project Address/GitHub 只读资料回答问题。" +
                    "\n不要执行 [bridgefs] 指令，不要声称拥有 Project 写入权限。" +
                    projectInfo + githubPrompt
            )
            Result(answer = answer)
        }.getOrElse {
            Result(error = "[API 错误]\n" + (it.message ?: "未知错误"))
        }
    }
}

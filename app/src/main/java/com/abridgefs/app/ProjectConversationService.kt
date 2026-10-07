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
        if (!AccessPolicy.isApiEnabled(context)) return Result(error = "API 全局访问已关闭")

        val member = project.defaultMemberId?.let { id ->
            project.aiMembers.firstOrNull { it.id == id }
        } ?: project.aiMembers.firstOrNull()

        val profile = conversation.apiId?.let { id -> apiProfiles.find(id) }
            ?: member?.apiProfileId?.let { id -> apiProfiles.find(id) }
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
            project.taskState.activeTaskId?.let { taskId ->
                project.tasks.firstOrNull { it.id == taskId }?.let { task ->
                    append("\n当前施工任务：").append(task.title)
                    append("\n任务状态：").append(task.status)
                }
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

            val systemPrompt = "你是当前 Project 的默认 AI。\n\n" +
                "对话层不具备手机本地文件读写、编辑、目录操作或 [bridgefs] 指令执行能力。不要输出 [bridgefs] 指令，也不要声称已经修改手机本地文件。\n" +
                "如果用户要求修改项目，只能进入当前 Project 的 GitHub 工作流；如果当前没有提供对应的 GitHub 写入工具，就明确说明不能执行修改。\n" +
                "需要验证时，应使用 GitHub Actions / Verify 结果作为事实依据。\n\n" +
                "[Project 信息]\n" + projectInfo + githubPrompt +
                "\n先直接回答用户问题；只有用户明确要求执行工作时，才进入后续工作流程。"

            val answer = BridgeApiClient(
                BridgeApiConfig(profile.baseUrl.trimEnd('/'), profile.key, profile.model)
            ).chat(conversation.messages, systemPrompt)

            Result(answer = answer)
        }.getOrElse {
            Result(error = "[API 错误]\n" + (it.message ?: "未知错误"))
        }
    }
}

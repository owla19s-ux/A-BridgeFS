package com.abridgefs.app

/**
 * Standalone conversation boundary.
 *
 * By default it is read-only. When the explicit standalone construction
 * switch is enabled, it may enter the same Project construction pipeline
 * used by Project conversations. Existing Task, permission, ConstructionLock,
 * GitHub write and Verify gates remain in force.
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

        val constructionEnabled = AccessPolicy.isStandaloneConstructionEnabled(context)

        if (constructionEnabled && project == null) {
            return Result(error = "普通对话施工已开启，但当前没有可用 Project")
        }

        val githubRead = project?.let {
            GitHubConversationReader(context).readForProject(it, userText)
        }
        if (githubRead?.error != null) {
            return Result(error = "[GitHub 错误]\n" + githubRead.error)
        }

        val projectInfo = project?.let {
            buildString {
                append("\n\n[当前 Project Address 信息]")
                append("\nProject：").append(it.name)
                it.localAddress?.takeIf(String::isNotBlank)?.let { path -> append("\nLocal：").append(path) }
                it.githubAddress.repository?.takeIf(String::isNotBlank)?.let { repo ->
                    append("\nGitHub：").append(repo)
                    it.githubAddress.branch?.takeIf(String::isNotBlank)?.let { b -> append("\nBranch：").append(b) }
                }
                it.taskState.activeTaskId?.let { taskId ->
                    it.tasks.firstOrNull { task -> task.id == taskId }?.let { task ->
                        append("\n当前施工任务：").append(task.title)
                        append("\n任务状态：").append(task.status)
                    }
                }
                if (constructionEnabled) {
                    append("\n普通对话施工：已开启")
                    append("\n当前对话可以在用户明确要求执行工作时生成 [bridgefs] 指令。")
                    append("\n施工仍受 Task、权限、ConstructionLock、Project GitHub 写权限等限制。")
                } else {
                    append("\n普通对话施工：已关闭")
                    append("\n当前对话只能读取 Project 资料，不能执行 [bridgefs] 指令。")
                }
            }
        } ?: ""

        val githubPrompt = githubRead?.content?.takeIf(String::isNotBlank)?.let {
            if (constructionEnabled) {
                "\n\n[Project GitHub 资料]\n" + it
            } else {
                "\n\n[Project GitHub 只读资料]\n" + it
            }
        } ?: ""

        val systemPrompt = if (constructionEnabled) {
            "你是 APS 的普通对话 AI。当前页面是独立对话，但用户已明确开启普通对话施工。" +
                "\n可以读取当前 Project 的资料。" +
                "\n只有用户明确要求执行工作时，才生成 [bridgefs]...[/bridgefs] 指令。" +
                "\n不要自动执行没有用户意图支持的修改。" +
                "\n施工指令必须针对当前 Project，不要声称拥有超出系统实际权限的能力。"
        } else {
            "你是 APS 的普通对话 AI。当前页面是独立对话。" +
                "\n可以参考附带的 Project Address/GitHub 只读资料回答问题。" +
                "\n不要执行 [bridgefs] 指令，不要声称拥有 Project 写入权限。"
        }

        return runCatching {
            val answer = BridgeApiClient(
                BridgeApiConfig(profile.baseUrl.trimEnd('/'), profile.key, profile.model)
            ).chat(
                conversation.messages,
                systemPrompt + projectInfo + githubPrompt
            )

            var executionError: String? = null
            var executionRequested = false

            val commandBlocks = Regex("""(?s)\[bridgefs\](.*?)\[/bridgefs\]""")
                .findAll(answer)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotBlank() }
                .toList()

            if (commandBlocks.isNotEmpty()) {
                if (!constructionEnabled) {
                    executionError = "普通对话施工开关已关闭，本轮 BridgeFS 指令未执行。"
                } else {
                    val parsed = CommandParser.parse(commandBlocks.joinToString("\n"))
                    executionError = parsed.error
                    if (executionError == null && parsed.commands.isNotEmpty()) {
                        val activeTaskId = project?.taskState?.activeTaskId
                        val activeTask = activeTaskId?.let { id ->
                            project?.tasks?.firstOrNull { it.id == id }
                        }
                        if (project == null) {
                            executionError = "当前没有可用 Project，不能施工"
                        } else if (activeTask == null) {
                            executionError = "当前 Project 没有正在施工的 Task，请先选择并开始施工任务"
                        } else if (activeTask.completed) {
                            executionError = "当前施工任务已完成，不能继续施工"
                        } else if (activeTask.status != "RUNNING" || project.taskState.status != "RUNNING") {
                            executionError = "当前 Task 不处于 RUNNING 状态，请先开始施工"
                        } else {
                            val intent = android.content.Intent(context, FileBridgeService::class.java).apply {
                                putExtra("bridgefs_external_command", commandBlocks.joinToString("\n"))
                                putExtra("bridgefs_root", project.localAddress.orEmpty())
                                putExtra("projectId", project.id)
                                putExtra("standaloneConversationId", conversation.id)
                            }
                            context.startService(intent)
                            executionRequested = true
                        }
                    } else if (executionError == null) {
                        executionError = "未识别到可执行的 BridgeFS 指令"
                    }
                }
            }

            Result(
                answer = answer,
                error = executionError,
                executionRequested = executionRequested
            )
        }.getOrElse {
            Result(error = "[API 错误]\n" + (it.message ?: "未知错误"))
        }
    }
}

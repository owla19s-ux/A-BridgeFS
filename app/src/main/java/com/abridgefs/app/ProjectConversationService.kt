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
        val isGithubReadError: Boolean = false,
        val executionRequested: Boolean = false
    )

    private val apiProfiles by lazy { ApiProfileStore(context) }

    fun sendReceipt(
        project: BridgeProject,
        conversation: BridgeConversation,
        receipt: BridgeReceiptRecord
    ): Result {
        val feedback = buildString {
            append("上一轮本地 Project 操作已经执行完成。以下是 A-BridgeFS Receipt，请根据结果继续当前工作。")
            append("\n\n[Receipt]")
            append("\nStatus：").append(receipt.status)
            append("\nCommand：").append(receipt.command)
            append("\nMessage：").append(receipt.message)
            append("\nReceipt ID：").append(receipt.receiptId)
            append("\n\n如果操作失败、被拒绝或需要确认，请先判断原因；如果仍需要继续施工，请输出下一步需要执行的 [bridgefs] 指令。不要声称尚未收到的操作已经成功。")
        }
        conversation.messages += BridgeChatMessage("user", feedback)
        val result = send(project, conversation, feedback)
        if (result.answer != null) {
            conversation.messages += BridgeChatMessage("assistant", result.answer)
        } else {
            conversation.messages += BridgeChatMessage("system", result.error ?: "Receipt 处理失败")
        }
        BridgeProjectStore(context).save(mutableListOf(project))
        return result
    }

    fun send(project: BridgeProject, conversation: BridgeConversation, userText: String): Result {
        if (!AccessPolicy.isApiEnabled(context)) {
            return Result(error = "API 全局访问已关闭")
        }

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
            val commandBlocks = Regex("""(?s)\[bridgefs\](.*?)\[/bridgefs\]""")
                .findAll(answer)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotBlank() }
                .toList()
            if (commandBlocks.isNotEmpty()) {
                val parsedCommands = CommandParser.parse(commandBlocks.joinToString("\n"))
                val needsConstruction = parsedCommands.any {
                    it is Command.Write || it is Command.Edit || it is Command.Mkdir || it is Command.Commit
                }
                // ConstructionLock is acquired at the FileBridgeService execution boundary,
                // after PermissionPolicy has confirmed the requested commands are executable.
                val intent = android.content.Intent(context, FileBridgeService::class.java).apply {
                    putExtra("bridgefs_external_command", commandBlocks.joinToString("\n"))
                    putExtra("bridgefs_root", project.localAddress.orEmpty())
                    putExtra("projectId", project.id)
                    putExtra("conversationId", conversation.id)
                }
                context.startService(intent)
            }
            Result(
                answer = answer,
                executionRequested = commandBlocks.isNotEmpty()
            )
        }.getOrElse {
            Result(error = "[API 错误]\n" + (it.message ?: "未知错误"))
        }
    }
}

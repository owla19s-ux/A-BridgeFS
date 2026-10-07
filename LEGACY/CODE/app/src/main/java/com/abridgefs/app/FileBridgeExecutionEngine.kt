package com.abridgefs.app

import android.content.Context
import java.io.File

/**
 * Execution boundary extracted from the legacy overlay UI.
 * Authorization and local command execution live here.
 */
class FileBridgeExecutionEngine(private val context: Context) {
    fun executeExternalCommand(
        requestedRoot: String,
        text: String,
        projectId: String?,
        conversationId: String?,
        standaloneConversationId: String?
    ): Triple<String, String, String> {
        val autoExecuteEnabled = context.getSharedPreferences("bridgefs", 0)
            .getBoolean("ai_auto_bridgefs_enabled", true)
        if (!autoExecuteEnabled) {
            AppLogger.log(context, "EXECUTION_DENIED", "reason=ai_auto_bridgefs_disabled")
            return Triple("DENIED", text, "AI 自动执行已关闭，本轮 BridgeFS 指令未执行。")
        }

        val parsed = CommandParser.parse(text)
        val commands = parsed.commands
        if (parsed.error != null) {
            AppLogger.log(context, "EXECUTION_DENIED", "reason=command_parse_error message=" + parsed.error)
            return Triple("FAILED", text, parsed.error)
        }
        if (commands.isEmpty()) {
            val error = "未识别到可执行指令"
            AppLogger.log(context, "EXECUTION_DENIED", "reason=command_parse_empty")
            return Triple("FAILED", text, error)
        }

        val limit = context.getSharedPreferences("bridgefs", 0)
            .getInt("command_limit", 3).coerceIn(1, 20)
        if (commands.size > limit) {
            AppLogger.log(context, "EXECUTION_DENIED", "reason=command_limit count=" + commands.size + " limit=" + limit)
            return Triple("DENIED", text, "本轮指令数量 " + commands.size + " 超过限制 " + limit + "，未执行。")
        }

        val project = projectId?.let { id ->
            BridgeProjectStore(context).load().firstOrNull { it.id == id }
        }
        val projectConversation = project?.let { current ->
            conversationId?.let { id -> current.conversations.firstOrNull { it.id == id } }
                ?: current.activeConversation()
        }
        val standaloneConversation = standaloneConversationId?.let { id ->
            BridgeConversationStore(context).load().firstOrNull { it.id == id }
        }

        val auth = PermissionPolicy.authorization(
            context,
            project,
            projectConversation ?: standaloneConversation
        )
        val rootPath = auth.root.ifBlank { requestedRoot }
        val rootFile = File(rootPath)

        if (rootPath.isBlank() || !rootFile.isDirectory || isProtectedWorkspace(rootPath)) {
            return Triple("FAILED", text, "Project Local Address 无效或属于受保护区域：" + rootPath)
        }
        if (project != null && requestedRoot.isNotBlank() &&
            File(requestedRoot).canonicalPath != rootFile.canonicalPath
        ) {
            return Triple("DENIED", text, "请求的 Local Address 与当前 Project Address 不一致")
        }

        commands.firstOrNull { PermissionPolicy.check(it, auth) == Decision.DENY }?.let { denied ->
            AppLogger.log(context, "EXECUTION_DENIED", "reason=permission command=" + denied)
            return Triple("DENIED", denied.toString(), "当前权限设置禁止该操作")
        }
        commands.firstOrNull { PermissionPolicy.check(it, auth) == Decision.CONFIRM }?.let { confirm ->
            AppLogger.log(context, "EXECUTION_DENIED", "reason=confirmation_required command=" + confirm)
            return Triple("DENIED", confirm.toString(), "该操作需要用户确认，Service 不允许绕过确认直接执行")
        }

        val constructionRequired = commands.any {
            it is Command.Write || it is Command.Edit || it is Command.Mkdir || it is Command.Commit
        }
        val memberId = project?.defaultMemberId ?: project?.aiMembers?.firstOrNull()?.id
        if (constructionRequired && project != null) {
            val activeTaskId = project.taskState.activeTaskId
            val activeTask = activeTaskId?.let { id -> project.tasks.firstOrNull { it.id == id } }
            if (activeTask == null || activeTask.completed || activeTask.status != "RUNNING" || project.taskState.status != "RUNNING") {
                AppLogger.log(context, "EXECUTION_DENIED", "reason=task_not_running project=${project.id} task=${activeTaskId ?: "none"}")
                return Triple("DENIED", text, "当前 Project 没有处于 RUNNING 状态的施工 Task，禁止执行写入/编辑/创建/Commit。")
            }
            if (memberId.isNullOrBlank()) {
                return Triple("DENIED", text, "需要施工权，但当前 Project 没有可用 AI Member")
            }
            runCatching {
                ConstructionLockStore(context).acquire(project, memberId)
            }.getOrElse {
                AppLogger.log(context, "EXECUTION_DENIED", "reason=construction_lock")
                return Triple("DENIED", text, "[ConstructionLock]\n" + (it.message ?: "无法取得施工权"))
            }
        }

        return try {
            val results = commands.map {
                CommandExecutor(rootFile, context, project, memberId).execute(it)
            }
            val message = results.joinToString("\n\n")
            val status = if (results.any { it.contains("✗") }) "FAILED" else "SUCCEEDED"
            Triple(status, commands.joinToString(" | ") { it.toString() }, message)
        } catch (e: Exception) {
            Triple("FAILED", text, "执行异常：" + (e.message ?: "未知错误"))
        }
    }

    private fun isProtectedWorkspace(path: String): Boolean {
        val candidate = runCatching { File(path).canonicalPath.trimEnd('/') }
            .getOrElse { File(path).absolutePath.trimEnd('/') }
            .replace('\\', '/').lowercase(java.util.Locale.ROOT)
        val storageRoot = runCatching {
            android.os.Environment.getExternalStorageDirectory().canonicalPath.trimEnd('/')
        }.getOrElse {
            android.os.Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
        }.replace('\\', '/').lowercase(java.util.Locale.ROOT)
        if (candidate == storageRoot) return true
        val segments = candidate.split('/').filter { it.isNotEmpty() }
        return segments.any { it == "android" } ||
            listOf("/android/data", "/android/obb", "/android/media").any { candidate.contains(it) }
    }
}

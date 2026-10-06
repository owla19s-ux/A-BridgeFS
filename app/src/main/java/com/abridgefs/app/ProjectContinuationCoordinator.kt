package com.abridgefs.app

import android.content.Context

/**
 * Project-level receipt handoff and continuation boundary.
 *
 * It owns the third-round loop:
 * execution -> receipt -> current task state -> AI -> next execution.
 * It never bypasses ProjectConversationService or FileBridgeExecutionEngine.
 */
object ProjectContinuationCoordinator {
    private val lock = Any()

    fun onReceipt(
        context: Context,
        projectId: String?,
        conversationId: String?,
        receiptId: String?,
        status: String,
        command: String,
        message: String,
        time: Long = System.currentTimeMillis()
    ) {
        if (projectId.isNullOrBlank()) return

        synchronized(lock) {
            val store = BridgeProjectStore(context)
            val project = store.load().firstOrNull { it.id == projectId } ?: return
            val conversation = conversationId?.let { id ->
                project.conversations.firstOrNull { it.id == id }
            } ?: project.activeConversation()

            val actualReceiptId = receiptId?.takeIf { it.isNotBlank() }
                ?: ("generated-" + time + "-" + status + "-" + command.hashCode())

            if (conversation.executions.none { it.receiptId == actualReceiptId }) {
                conversation.executions += BridgeReceiptRecord(
                    status = status,
                    command = command,
                    message = message,
                    time = time,
                    receiptId = actualReceiptId
                )
            }

            val taskId = project.taskState.activeTaskId
            val task = taskId?.let { id -> project.tasks.firstOrNull { it.id == id } }

            if (task == null) {
                store.saveProject(project)
                return
            }

            if (project.taskState.lastReceiptId == actualReceiptId) {
                store.saveProject(project)
                return
            }

            project.taskState.lastReceiptId = actualReceiptId
            project.taskState.lastResult = message

            if (status != "SUCCEEDED") {
                task.status = "FAILED"
                task.lastReceiptId = actualReceiptId
                task.lastResult = message
                project.taskState.status = "FAILED"
                releaseLock(context, project)
                store.saveProject(project)
                return
            }

            if (task.completed) {
                task.status = "COMPLETED"
                project.taskState.status = "COMPLETED"
                releaseLock(context, project)
                store.saveProject(project)
                return
            }

            if (project.taskState.continuationCount >= project.taskState.maxContinuations) {
                task.status = "WAITING"
                project.taskState.status = "LIMIT_REACHED"
                releaseLock(context, project)
                store.saveProject(project)
                return
            }

            project.taskState.status = "RUNNING"
            project.taskState.continuationCount += 1
            task.status = "RUNNING"
            task.lastReceiptId = actualReceiptId
            task.lastResult = message

            val receiptPrompt = buildString {
                append("[APS Execution Receipt]\n")
                append("status: ").append(status).append('\n')
                append("command: ").append(command).append('\n')
                append("result:\n").append(message).append('\n')
                append("当前任务：").append(task.title).append('\n')
                append("这是上一轮真实执行结果。请基于结果判断下一步；如果任务尚未完成，继续输出下一轮 [bridgefs] 指令；不要声称尚未执行的操作已经完成。")
            }

            conversation.messages += BridgeChatMessage("user", receiptPrompt)

            val result = runCatching {
                ProjectConversationService(context).send(
                    project,
                    conversation,
                    receiptPrompt
                )
            }.getOrElse {
                ProjectConversationService.Result(
                    error = "Continuation 异常：" + (it.message ?: "未知错误")
                )
            }

            if (result.answer != null) {
                conversation.messages += BridgeChatMessage("assistant", result.answer)
            }

            when {
                result.error != null -> {
                    task.status = "FAILED"
                    task.lastResult = result.error
                    project.taskState.status = "FAILED"
                    releaseLock(context, project)
                }
                result.executionRequested -> {
                    task.status = "RUNNING"
                    project.taskState.status = "RUNNING"
                }
                else -> {
                    task.status = "WAITING"
                    project.taskState.status = "WAITING"
                    releaseLock(context, project)
                }
            }

            store.saveProject(project)
        }
    }

    private fun releaseLock(context: Context, project: BridgeProject) {
        val memberId = project.defaultMemberId ?: return
        runCatching { ConstructionLockStore(context).release(project, memberId) }
    }
}
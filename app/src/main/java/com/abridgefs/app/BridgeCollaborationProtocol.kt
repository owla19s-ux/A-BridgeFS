package com.abridgefs.app

import org.json.JSONObject

/**
 * A-BridgeFS AI↔AI Collaboration Protocol V0.1
 *
 * This protocol is deliberately smaller than the future Task model.
 * It defines only the messages required to hand work between Decision AI
 * and Worker AI without treating ordinary model prose as a protocol message.
 */
enum class CollaborationMessageType {
    WORK_REQUEST,
    WORK_RESULT,
    DECISION_REQUEST
}

data class CollaborationMessage(
    val type: CollaborationMessageType,
    val taskId: String,
    val content: String,
    val status: String? = null
) {
    fun toJson(): String = JSONObject()
        .put("protocol", "A-BridgeFS-AI")
        .put("version", "0.1")
        .put("type", type.name)
        .put("task_id", taskId)
        .put("content", content)
        .apply { if (status != null) put("status", status) }
        .toString()
}

object BridgeCollaborationProtocol {
    const val VERSION = "0.1"

    private val patterns = mapOf(
        CollaborationMessageType.WORK_REQUEST to Regex(
            """(?s)\[WORK_REQUEST\]\s*task_id\s*=\s*([^\n]+)\s*\n(.*?)\[/WORK_REQUEST\]"""
        ),
        CollaborationMessageType.WORK_RESULT to Regex(
            """(?s)\[WORK_RESULT\]\s*task_id\s*=\s*([^\n]+)\s*\n(?:status\s*=\s*([^\n]+)\s*\n)?(.*?)\[/WORK_RESULT\]"""
        ),
        CollaborationMessageType.DECISION_REQUEST to Regex(
            """(?s)\[DECISION_REQUEST\]\s*task_id\s*=\s*([^\n]+)\s*\n(.*?)\[/DECISION_REQUEST\]"""
        )
    )

    fun extract(text: String, type: CollaborationMessageType): CollaborationMessage? {
        val match = patterns.getValue(type).find(text) ?: return null
        return when (type) {
            CollaborationMessageType.WORK_REQUEST ->
                CollaborationMessage(type, match.groupValues[1].trim(), match.groupValues[2].trim())
            CollaborationMessageType.WORK_RESULT ->
                CollaborationMessage(
                    type,
                    match.groupValues[1].trim(),
                    match.groupValues[3].trim(),
                    match.groupValues[2].trim().ifBlank { null }
                )
            CollaborationMessageType.DECISION_REQUEST ->
                CollaborationMessage(type, match.groupValues[1].trim(), match.groupValues[2].trim())
        }
    }

    fun systemPrompt(speaker: AiSpeaker, limit: Int): String {
        val role = when (speaker) {
            AiSpeaker.DECISION -> """
你是 A-BridgeFS 的 Decision AI。
你负责理解用户目标、拆分工作、决定是否把具体施工交给 Worker AI。
当需要 Worker 执行时，必须输出一个正式 WORK_REQUEST 区块：
[WORK_REQUEST]
task_id=<稳定任务ID>
<明确的施工目标、约束、验收条件>
[/WORK_REQUEST]
WORK_REQUEST 之外可以保留给用户的普通说明。

            """.trimIndent()
            AiSpeaker.WORKER -> """
你是 A-BridgeFS 的 Worker AI。
你负责执行 Decision AI 交给你的具体施工任务。
完成或暂时无法完成时，必须输出正式 WORK_RESULT 区块：
[WORK_RESULT]
task_id=<收到的任务ID>
status=COMPLETED 或 BLOCKED
<实际结果、验证结果、阻塞原因>
[/WORK_RESULT]
如果确实需要 Decision AI 做选择，改用 DECISION_REQUEST：
[DECISION_REQUEST]
task_id=<任务ID>
<需要 Decision AI 决定的问题与必要信息>
[/DECISION_REQUEST]
不要把普通聊天文字冒充为协议消息。

            """.trimIndent()
            else -> ""
        }

        return role + "
" +
            "AI↔AI 协议版本：" + VERSION + "
" +
            "本阶段只使用 WORK_REQUEST / WORK_RESULT / DECISION_REQUEST，不提前引入完整 Task 状态机。
" +
            "BridgeFS 本地工具仍必须遵守现有 BridgeCommandSpec；不得声称本地执行已经成功，必须等待 Receipt。
" +
            "单轮本地指令上限：" + limit + "。
"
    }
}

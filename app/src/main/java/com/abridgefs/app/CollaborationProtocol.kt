package com.abridgefs.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A-BridgeFS 双 AI 协作运行协议 v0.2.
 * AI A / AI B 是参与者，不是固定的决策/施工身份。阶段角色由任务状态决定。
 *
 * This layer defines message shape and local state transitions only.
 * AI A / AI B are participant identities; StageRole expresses the current task stage.
 * It intentionally does not bind the protocol to a transport implementation.
 */
object CollaborationProtocol {
    const val VERSION = "0.2"

    enum class Role { AI_A, AI_B, HUMAN }

    enum class StageRole { PLANNER, BUILDER, REVIEWER, OBSERVER }

    enum class Type {
        TASK,
        DECISION_REQUEST,
        DECISION_RESPONSE,
        PROGRESS,
        BLOCKED,
        COMMIT,
        VERIFY,
        ESCALATE,
        COMPLETE,
        FILE_CHANGE_REQUEST
    }

    enum class State {
        IDLE,
        WORKING,
        WAITING_DECISION,
        BLOCKED,
        ESCALATED,
        COMPLETE
    }

    data class Message(
        val id: String = "msg_" + UUID.randomUUID().toString(),
        val ts: String = java.time.Instant.now().toString(),
        val from: Role,
        val to: Role,
        val taskId: String,
        val type: Type,
        val replyTo: String? = null,
        val payload: JSONObject = JSONObject()
    ) {
        fun validate(): String? {
            if (id.isBlank()) return "id is blank"
            if (ts.isBlank()) return "ts is blank"
            if (taskId.isBlank()) return "task_id is blank"
            if (payload.length() == 0 && type != Type.PROGRESS) {
                // Empty payload is legal for PROGRESS only when an implementation
                // deliberately emits a heartbeat-like progress event.
                return "payload is empty"
            }
            return null
        }

        fun toJson(): JSONObject = JSONObject()
            .put("v", VERSION)
            .put("id", id)
            .put("ts", ts)
            .put("from", from.wireName())
            .put("to", to.wireName())
            .put("task_id", taskId)
            .put("type", type.name)
            .put("reply_to", replyTo)
            .put("payload", payload)

        companion object {
            fun fromJson(json: JSONObject): Message {
                val version = json.optString("v").ifBlank { VERSION }
                require(version == VERSION || version == "0.1") { "unsupported protocol version: $version" }
                return Message(
                    id = json.optString("id").ifBlank { "msg_" + UUID.randomUUID().toString() },
                    ts = json.optString("ts").ifBlank { java.time.Instant.now().toString() },
                    from = roleFromWireName(json.getString("from")),
                    to = roleFromWireName(json.getString("to")),
                    taskId = json.getString("task_id"),
                    type = Type.valueOf(json.getString("type")),
                    replyTo = if (json.isNull("reply_to")) null else json.optString("reply_to"),
                    payload = json.optJSONObject("payload") ?: JSONObject()
                )
            }
        }
    }

    data class ValidationResult(
        val valid: Boolean,
        val error: String? = null
    )

    fun validate(message: Message): ValidationResult {
        val error = message.validate()
        if (error != null) return ValidationResult(false, error)

        val routeError = when (message.type) {
            Type.TASK,
            Type.DECISION_REQUEST,
            Type.DECISION_RESPONSE,
            Type.FILE_CHANGE_REQUEST ->
                if (message.from == Role.HUMAN || message.to == Role.HUMAN || message.from == message.to) {
                    message.type.name + " must be an AI-to-AI message"
                } else null
            Type.ESCALATE -> if (message.to != Role.HUMAN) "ESCALATE must target human" else null
            Type.PROGRESS, Type.BLOCKED, Type.COMMIT, Type.VERIFY, Type.COMPLETE -> null
        }
        return if (routeError == null) ValidationResult(true) else ValidationResult(false, routeError)
    }

    fun nextState(current: State, message: Message): State {
        validate(message).let { result ->
            require(result.valid) { result.error ?: "invalid message" }
        }

        return when (message.type) {
            Type.TASK -> State.WORKING
            Type.DECISION_REQUEST -> State.WAITING_DECISION
            Type.DECISION_RESPONSE -> State.WORKING
            Type.PROGRESS, Type.COMMIT, Type.VERIFY, Type.FILE_CHANGE_REQUEST -> {
                if (message.type == Type.VERIFY &&
                    message.payload.optString("verdict") == "pass") State.WORKING
                else current
            }
            Type.BLOCKED -> State.BLOCKED
            Type.ESCALATE -> State.ESCALATED
            Type.COMPLETE -> State.COMPLETE
        }
    }

    fun newTaskId(): String = "task_" + UUID.randomUUID().toString()

    fun task(
        taskId: String,
        objective: String,
        allowPaths: List<String>,
        denyPaths: List<String> = emptyList(),
        allowOperations: List<String>,
        acceptance: List<String>,
        selfResolve: List<String> = emptyList(),
        mustAsk: List<String> = emptyList(),
        maxIterations: Int = 20,
        contextRefs: List<String> = emptyList()
    ): Message {
        require(objective.isNotBlank()) { "objective is blank" }
        require(maxIterations > 0) { "maxIterations must be positive" }

        val scope = JSONObject()
            .put("allow_paths", JSONArray(allowPaths))
            .put("deny_paths", JSONArray(denyPaths))
            .put("allow_operations", JSONArray(allowOperations))

        val autonomy = JSONObject()
            .put("self_resolve", JSONArray(selfResolve))
            .put("must_ask", JSONArray(mustAsk))
            .put("max_iterations", maxIterations)
            .put("no_human_in_loop", true)

        return Message(
            from = Role.AI_A,
            to = Role.AI_B,
            taskId = taskId,
            type = Type.TASK,
            payload = JSONObject()
                .put("objective", objective)
                .put("stage_role", StageRole.PLANNER.name)
                .put("scope", scope)
                .put("acceptance", JSONArray(acceptance))
                .put("autonomy", autonomy)
                .put("context_refs", JSONArray(contextRefs))
        )
    }

    fun decisionRequest(
        taskId: String,
        kind: String,
        question: String,
        options: List<Pair<String, String>>,
        recommendation: String? = null,
        reason: String = "",
        evidence: List<String> = emptyList(),
        blockedOn: String = ""
    ): Message {
        require(question.isNotBlank()) { "question is blank" }
        require(options.size >= 2) { "DECISION_REQUEST requires at least two options" }

        val optionArray = JSONArray()
        options.forEach { (id, summary) ->
            optionArray.put(JSONObject().put("id", id).put("summary", summary))
        }

        return Message(
            from = Role.AI_B,
            to = Role.AI_A,
            taskId = taskId,
            type = Type.DECISION_REQUEST,
            payload = JSONObject()
                .put("kind", kind)
                .put("question", question)
                .put("options", optionArray)
                .put("recommendation", recommendation ?: JSONObject.NULL)
                .put("reason", reason)
                .put("evidence", JSONArray(evidence))
                .put("blocked_on", blockedOn)
        )
    }

    fun decisionResponse(
        taskId: String,
        replyTo: String,
        decision: String,
        instruction: String = "",
        scopeChange: JSONObject? = null,
        extendScope: List<String> = emptyList(),
        extraIterations: Int = 0
    ): Message {
        require(decision.isNotBlank()) { "decision is blank" }
        require(replyTo.isNotBlank()) { "replyTo is blank" }

        val grants = JSONObject()
            .put("extend_scope", JSONArray(extendScope))
            .put("extra_iterations", extraIterations)

        return Message(
            from = Role.AI_A,
            to = Role.AI_B,
            taskId = taskId,
            type = Type.DECISION_RESPONSE,
            replyTo = replyTo,
            payload = JSONObject()
                .put("decision", decision)
                .put("instruction", instruction)
                .put("scope_change", scopeChange ?: JSONObject.NULL)
                .put("grants", grants)
        )
    }

    fun fileChangeRequest(
        taskId: String,
        path: String,
        content: String,
        commitMessage: String,
        operation: String = "write"
    ): Message {
        require(path.isNotBlank()) { "path is blank" }
        require(content.isNotEmpty()) { "content is empty" }
        require(commitMessage.isNotBlank()) { "commitMessage is blank" }
        require(operation == "write" || operation == "edit") { "unsupported file operation: $operation" }
        return Message(
            from = Role.AI_B,
            to = Role.AI_A,
            taskId = taskId,
            type = Type.FILE_CHANGE_REQUEST,
            payload = JSONObject()
                .put("path", path.trimStart('/'))
                .put("operation", operation)
                .put("content", content)
                .put("commit_message", commitMessage)
        )
    }

    fun commit(
        taskId: String,
        sha: String,
        message: String,
        files: List<String>,
        diffStat: String
    ): Message = Message(
        from = Role.AI_B,
        to = Role.AI_A,
        taskId = taskId,
        type = Type.COMMIT,
        payload = JSONObject()
            .put("sha", sha)
            .put("message", message)
            .put("files", JSONArray(files))
            .put("diff_stat", diffStat)
    )

    fun verify(
        taskId: String,
        targetCommit: String,
        checks: List<Triple<String, String, String>>,
        verdict: String,
        realLinkTested: Boolean
    ): Message {
        require(verdict == "pass" || verdict == "fail") { "verdict must be pass or fail" }
        val checkArray = JSONArray()
        checks.forEach { (name, result, detail) ->
            checkArray.put(JSONObject()
                .put("name", name)
                .put("result", result)
                .put("detail", detail))
        }
        return Message(
            from = Role.AI_B,
            to = Role.AI_A,
            taskId = taskId,
            type = Type.VERIFY,
            payload = JSONObject()
                .put("target_commit", targetCommit)
                .put("checks", checkArray)
                .put("verdict", verdict)
                .put("real_link_tested", realLinkTested)
        )
    }

    private fun Role.wireName(): String = when (this) {
        Role.AI_A -> "ai_a"
        Role.AI_B -> "ai_b"
        Role.HUMAN -> "human"
    }

    private fun roleFromWireName(value: String): Role = when (value.lowercase()) {
        "ai_a", "decision_ai" -> Role.AI_A
        "ai_b", "worker" -> Role.AI_B
        "human" -> Role.HUMAN
        else -> error("unknown role: $value")
    }
}

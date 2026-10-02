package com.abridgefs.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A-BridgeFS Decision AI ↔ Worker v0.1 runtime protocol.
 *
 * This layer defines message shape and local state transitions only.
 * It intentionally does not bind the protocol to a transport implementation.
 */
object CollaborationProtocol {
    const val VERSION = "0.1"

    enum class Role { DECISION_AI, WORKER, HUMAN }

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
                val version = json.optString("v")
                require(version == VERSION) { "unsupported protocol version: $version" }
                return Message(
                    id = json.getString("id"),
                    ts = json.getString("ts"),
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
            Type.TASK -> if (message.from != Role.DECISION_AI || message.to != Role.WORKER) "TASK route must be Decision AI → Worker" else null
            Type.DECISION_REQUEST -> if (message.from != Role.WORKER || message.to != Role.DECISION_AI) "DECISION_REQUEST route must be Worker → Decision AI" else null
            Type.DECISION_RESPONSE -> if (message.from != Role.DECISION_AI || message.to != Role.WORKER) "DECISION_RESPONSE route must be Decision AI → Worker" else null
            Type.ESCALATE -> if (message.to != Role.HUMAN) "ESCALATE must target human" else null
            Type.FILE_CHANGE_REQUEST -> if (message.from != Role.WORKER || message.to != Role.DECISION_AI) "FILE_CHANGE_REQUEST route must be Worker → Decision AI" else null
            Type.PROGRESS, Type.BLOCKED, Type.COMMIT, Type.VERIFY, Type.COMPLETE, Type.FILE_CHANGE_REQUEST -> null
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
            from = Role.DECISION_AI,
            to = Role.WORKER,
            taskId = taskId,
            type = Type.TASK,
            payload = JSONObject()
                .put("objective", objective)
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
            from = Role.WORKER,
            to = Role.DECISION_AI,
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
            from = Role.DECISION_AI,
            to = Role.WORKER,
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
            from = Role.WORKER,
            to = Role.DECISION_AI,
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
        from = Role.WORKER,
        to = Role.DECISION_AI,
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
            from = Role.WORKER,
            to = Role.DECISION_AI,
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
        Role.DECISION_AI -> "decision_ai"
        Role.WORKER -> "worker"
        Role.HUMAN -> "human"
    }

    private fun roleFromWireName(value: String): Role = when (value.lowercase()) {
        "decision_ai" -> Role.DECISION_AI
        "worker" -> Role.WORKER
        "human" -> Role.HUMAN
        else -> error("unknown role: $value")
    }
}

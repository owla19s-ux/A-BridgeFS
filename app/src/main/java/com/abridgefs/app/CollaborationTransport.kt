package com.abridgefs.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal transport boundary for Decision AI ↔ Worker.
 *
 * Persistence is intentionally local for v0.1. The protocol does not depend on
 * GitHub Issues, files, or any specific network transport.
 */
class CollaborationTransport(context: Context) {
    private val prefs = context.getSharedPreferences("collaboration_transport", Context.MODE_PRIVATE)

    fun append(message: CollaborationProtocol.Message) {
        val items = JSONArray(prefs.getString(KEY_MESSAGES, "[]") ?: "[]")
        items.put(message.toJson())
        prefs.edit().putString(KEY_MESSAGES, items.toString()).apply()
    }

    fun all(): List<CollaborationProtocol.Message> {
        val items = JSONArray(prefs.getString(KEY_MESSAGES, "[]") ?: "[]")
        return buildList {
            for (index in 0 until items.length()) {
                val message = CollaborationProtocol.Message.fromJson(items.getJSONObject(index))
                add(message)
            }
        }
    }

    fun pendingFor(role: CollaborationProtocol.Role): List<CollaborationProtocol.Message> =
        all().filter { it.to == role && !isHandled(it.id) }

    fun markHandled(messageId: String) {
        val handled = JSONArray(prefs.getString(KEY_HANDLED, "[]") ?: "[]")
        if ((0 until handled.length()).none { handled.optString(it) == messageId }) {
            handled.put(messageId)
            prefs.edit().putString(KEY_HANDLED, handled.toString()).apply()
        }
    }

    private fun isHandled(messageId: String): Boolean {
        val handled = JSONArray(prefs.getString(KEY_HANDLED, "[]") ?: "[]")
        return (0 until handled.length()).any { handled.optString(it) == messageId }
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_MESSAGES)
            .remove(KEY_HANDLED)
            .apply()
    }

    companion object {
        private const val KEY_MESSAGES = "messages"
        private const val KEY_HANDLED = "handled"
    }
}

/**
 * Role-specific API configuration.
 *
 * The existing single-API settings remain untouched. These keys are reserved
 * for the collaboration layer so the UI can add role configuration later.
 */
data class CollaborationApiConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String
) {
    fun isConfigured(): Boolean =
        baseUrl.isNotBlank() && model.isNotBlank()

    companion object {
        fun fromPreferences(
            context: Context,
            role: CollaborationProtocol.Role
        ): CollaborationApiConfig {
            val prefs = context.getSharedPreferences("bridgefs", 0)
            val prefix = when (role) {
                CollaborationProtocol.Role.DECISION_AI -> "collab_decision_"
                CollaborationProtocol.Role.WORKER -> "collab_worker_"
                CollaborationProtocol.Role.HUMAN -> return CollaborationApiConfig("", "", "")
            }
            return CollaborationApiConfig(
                baseUrl = prefs.getString(prefix + "base_url", "").orEmpty().trim(),
                apiKey = prefs.getString(prefix + "api_key", "").orEmpty(),
                model = prefs.getString(prefix + "model", "").orEmpty().trim()
            )
        }
    }
}

/**
 * Small role-aware API boundary. It does not decide how protocol JSON is
 * generated or parsed; callers remain responsible for protocol semantics.
 */
class CollaborationApiClient(private val config: CollaborationApiConfig) {
    fun invoke(
        incoming: CollaborationProtocol.Message,
        systemPrompt: String
    ): String {
        require(config.isConfigured()) { "collaboration API is not configured" }

        val client = BridgeApiClient(
            BridgeApiConfig(
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                model = config.model
            )
        )

        val request = incoming.toJson().toString()
        return client.chat(
            listOf(BridgeChatMessage("user", request)),
            systemPrompt
        )
    }
}

/**
 * Minimal coordinator used by later UI/service integration.
 *
 * It provides the first real boundary between:
 * Decision AI API → protocol transport → Worker API.
 * It deliberately does not execute GitHub work yet.
 */
class CollaborationCoordinator(private val context: Context) {
    private val transport = CollaborationTransport(context)

    fun submitTask(task: CollaborationProtocol.Message) {
        require(task.type == CollaborationProtocol.Type.TASK) { "submitTask requires TASK" }
        require(task.from == CollaborationProtocol.Role.DECISION_AI)
        require(task.to == CollaborationProtocol.Role.WORKER)
        require(CollaborationProtocol.validate(task).valid)
        transport.append(task)
    }

    fun callDecisionAi(message: CollaborationProtocol.Message, systemPrompt: String): String {
        require(message.to == CollaborationProtocol.Role.DECISION_AI)
        return CollaborationApiClient(CollaborationApiConfig.fromPreferences(context, CollaborationProtocol.Role.DECISION_AI)).invoke(message, systemPrompt)
    }

    fun callWorker(message: CollaborationProtocol.Message, systemPrompt: String): String {
        require(message.to == CollaborationProtocol.Role.WORKER)
        return CollaborationApiClient(CollaborationApiConfig.fromPreferences(context, CollaborationProtocol.Role.WORKER)).invoke(message, systemPrompt)
    }

    /** Execute exactly one Worker -> Decision AI round. */
    fun dispatchOneWorkerRound(workerSystemPrompt: String, decisionSystemPrompt: String): List<CollaborationProtocol.Message> {
        val task = transport.pendingFor(CollaborationProtocol.Role.WORKER).firstOrNull { it.type == CollaborationProtocol.Type.TASK } ?: return emptyList()
        val workerMessage = parseProtocolResponse(callWorker(task, workerSystemPrompt))
        validateResponse(workerMessage, CollaborationProtocol.Role.WORKER)
        transport.append(workerMessage)
        transport.markHandled(task.id)
        if (workerMessage.to != CollaborationProtocol.Role.DECISION_AI) return listOf(workerMessage)
        val decisionMessage = parseProtocolResponse(callDecisionAi(workerMessage, decisionSystemPrompt))
        validateResponse(decisionMessage, CollaborationProtocol.Role.DECISION_AI)
        transport.append(decisionMessage)
        return listOf(workerMessage, decisionMessage)
    }

    fun pendingFor(role: CollaborationProtocol.Role): List<CollaborationProtocol.Message> = transport.pendingFor(role)

    private fun parseProtocolResponse(raw: String): CollaborationProtocol.Message {
        val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = try { JSONObject(text) } catch (_: Exception) {
            val start = text.indexOf('{'); val end = text.lastIndexOf('}')
            require(start >= 0 && end > start) { "AI response is not a protocol JSON object" }
            JSONObject(text.substring(start, end + 1))
        }
        return try { CollaborationProtocol.Message.fromJson(json) } catch (cause: Exception) {
            throw IllegalArgumentException("AI response is not a valid collaboration message: " + cause.message, cause)
        }
    }

    private fun validateResponse(message: CollaborationProtocol.Message, expectedFrom: CollaborationProtocol.Role) {
        require(message.from == expectedFrom) { "AI response has wrong sender: ${message.from}" }
        val result = CollaborationProtocol.validate(message)
        require(result.valid) { result.error ?: "invalid protocol response" }
    }
}

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
        all().filter { it.to == role }

    fun clear() {
        prefs.edit().remove(KEY_MESSAGES).apply()
    }

    companion object {
        private const val KEY_MESSAGES = "messages"
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
        require(task.type == CollaborationProtocol.Type.TASK) {
            "submitTask requires TASK"
        }
        require(task.from == CollaborationProtocol.Role.DECISION_AI)
        require(task.to == CollaborationProtocol.Role.WORKER)
        require(CollaborationProtocol.validate(task).valid)
        transport.append(task)
    }

    fun callDecisionAi(
        message: CollaborationProtocol.Message,
        systemPrompt: String
    ): String {
        require(message.to == CollaborationProtocol.Role.DECISION_AI)
        return CollaborationApiClient(
            CollaborationApiConfig.fromPreferences(
                context,
                CollaborationProtocol.Role.DECISION_AI
            )
        ).invoke(message, systemPrompt)
    }

    fun callWorker(
        message: CollaborationProtocol.Message,
        systemPrompt: String
    ): String {
        require(message.to == CollaborationProtocol.Role.WORKER)
        return CollaborationApiClient(
            CollaborationApiConfig.fromPreferences(
                context,
                CollaborationProtocol.Role.WORKER
            )
        ).invoke(message, systemPrompt)
    }

    fun pendingFor(role: CollaborationProtocol.Role): List<CollaborationProtocol.Message> =
        transport.pendingFor(role)
}

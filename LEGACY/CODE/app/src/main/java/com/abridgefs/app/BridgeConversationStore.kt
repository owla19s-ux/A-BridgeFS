package com.abridgefs.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Standalone human ↔ single-AI conversations.
 *
 * These conversations are deliberately outside Project state. Project
 * conversations belong to a Project; standalone conversations remain independent
 * for ordinary chat, reading and external resource queries.
 */
class BridgeConversationStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("bridgefs_standalone_conversations", Context.MODE_PRIVATE)
    private val key = "data"

    fun load(): MutableList<BridgeConversation> {
        val array = JSONArray(prefs.getString(key, "[]") ?: "[]")
        val result = mutableListOf<BridgeConversation>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result += readConversation(obj)
        }
        return result
    }

    fun save(conversations: List<BridgeConversation>) {
        val array = JSONArray()
        conversations.forEach { conversation ->
            array.put(JSONObject()
                .put("id", conversation.id)
                .put("name", conversation.name)
                .put("apiId", conversation.apiId.orEmpty())
                .put("localFileModifyOverride", conversation.localFileModifyOverride)
                .put("messages", JSONArray().apply {
                    conversation.messages.forEach {
                        put(JSONObject()
                            .put("role", it.role)
                            .put("content", it.content)
                            .put("time", it.time)
                            .put("apiId", it.apiId.orEmpty())
                            .put("apiName", it.apiName.orEmpty())
                            .put("apiAvatar", it.apiAvatar.orEmpty()))
                    }
                })
                .put("executions", JSONArray().apply {
                    conversation.executions.forEach {
                        put(JSONObject()
                            .put("status", it.status)
                            .put("command", it.command)
                            .put("message", it.message)
                            .put("time", it.time).put("receiptId", it.receiptId))
                    }
                })
            )
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    fun newConversation(name: String, apiId: String?): BridgeConversation =
        BridgeConversation(
            id = UUID.randomUUID().toString(),
            name = name,
            apiId = apiId
        )

    private fun readConversation(obj: JSONObject): BridgeConversation {
        val conversation = BridgeConversation(
            id = obj.getString("id"),
            name = obj.optString("name", "未命名对话"),
            apiId = obj.optString("apiId", "").ifBlank { null },
            localFileModifyOverride = if (obj.has("localFileModifyOverride") && !obj.isNull("localFileModifyOverride")) {
                obj.optBoolean("localFileModifyOverride")
            } else {
                null
            }
        )
        readMessages(obj.optJSONArray("messages"), conversation.messages)
        readExecutions(obj.optJSONArray("executions"), conversation.executions)
        return conversation
    }

    private fun readMessages(array: JSONArray?, target: MutableList<BridgeChatMessage>) {
        if (array == null) return
        for (j in 0 until array.length()) {
            val item = array.getJSONObject(j)
            target += BridgeChatMessage(
                item.getString("role"),
                item.getString("content"),
                item.optLong("time", System.currentTimeMillis()),
                item.optString("apiId", "").ifBlank { null },
                item.optString("apiName", "").ifBlank { null },
                item.optString("apiAvatar", "").ifBlank { null }
            )
        }
    }

    private fun readExecutions(array: JSONArray?, target: MutableList<BridgeReceiptRecord>) {
        if (array == null) return
        for (j in 0 until array.length()) {
            val item = array.getJSONObject(j)
            target += BridgeReceiptRecord(
                item.getString("status"),
                item.getString("command"),
                item.getString("message"),
                item.optLong("time", System.currentTimeMillis()),
                item.optString("receiptId", "").ifBlank { UUID.randomUUID().toString() }
            )
        }
    }
}

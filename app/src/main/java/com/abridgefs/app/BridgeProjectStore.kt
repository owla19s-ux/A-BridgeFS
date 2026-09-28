package com.abridgefs.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class BridgeChatMessage(
    val role: String,
    val content: String,
    val time: Long = System.currentTimeMillis()
)

data class BridgeReceiptRecord(
    val status: String,
    val command: String,
    val message: String,
    val time: Long = System.currentTimeMillis()
)

data class BridgeProject(
    val id: String,
    var name: String,
    val messages: MutableList<BridgeChatMessage> = mutableListOf(),
    val executions: MutableList<BridgeReceiptRecord> = mutableListOf()
)

class BridgeProjectStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("bridgefs_projects", Context.MODE_PRIVATE)
    private val key = "data"

    fun load(): MutableList<BridgeProject> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        val array = JSONArray(raw)
        val result = mutableListOf<BridgeProject>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val project = BridgeProject(
                obj.getString("id"),
                obj.getString("name")
            )

            val messages = obj.optJSONArray("messages") ?: JSONArray()
            for (j in 0 until messages.length()) {
                val item = messages.getJSONObject(j)
                project.messages += BridgeChatMessage(
                    item.getString("role"),
                    item.getString("content"),
                    item.optLong("time", System.currentTimeMillis())
                )
            }

            val executions = obj.optJSONArray("executions") ?: JSONArray()
            for (j in 0 until executions.length()) {
                val item = executions.getJSONObject(j)
                project.executions += BridgeReceiptRecord(
                    item.getString("status"),
                    item.getString("command"),
                    item.getString("message"),
                    item.optLong("time", System.currentTimeMillis())
                )
            }

            result += project
        }

        return result
    }

    fun save(projects: List<BridgeProject>) {
        val array = JSONArray()

        projects.forEach { project ->
            val obj = JSONObject()
                .put("id", project.id)
                .put("name", project.name)

            obj.put(
                "messages",
                JSONArray().apply {
                    project.messages.forEach {
                        put(
                            JSONObject()
                                .put("role", it.role)
                                .put("content", it.content)
                                .put("time", it.time)
                        )
                    }
                }
            )

            obj.put(
                "executions",
                JSONArray().apply {
                    project.executions.forEach {
                        put(
                            JSONObject()
                                .put("status", it.status)
                                .put("command", it.command)
                                .put("message", it.message)
                                .put("time", it.time)
                        )
                    }
                }
            )

            array.put(obj)
        }

        prefs.edit().putString(key, array.toString()).apply()
    }

    fun newProject(name: String): BridgeProject =
        BridgeProject(UUID.randomUUID().toString(), name)
}

package com.abridgefs.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class BridgeApiConfig(val baseUrl: String, val apiKey: String, val model: String)

class BridgeApiClient(private val config: BridgeApiConfig) {
    var lastToolTrace: List<String> = emptyList()
        private set
    fun testConnection(): String {
        val url = URL(config.baseUrl.trimEnd('/') + "/models")
        val c = url.openConnection() as HttpURLConnection
        c.requestMethod = "GET"; c.connectTimeout = 10000; c.readTimeout = 15000
        if (config.apiKey.isNotBlank()) c.setRequestProperty("Authorization", "Bearer " + config.apiKey)
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("API $code: " + body.take(300))
        return "HTTP $code"
    }

    fun chat(messages: List<BridgeChatMessage>, system: String): String {
        val apiMessages = buildMessages(messages, system)
        val body = JSONObject().put("model", config.model).put("messages", apiMessages)
        val response = post(body)
        return response.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content")
    }

    /**
     * OpenAI-compatible Tool Calling loop.
     * The runtime executes requested local tools and feeds their results back
     * to the model until the model returns a normal assistant message.
     */
    fun chatWithTools(
        messages: List<BridgeChatMessage>,
        system: String,
        maxToolRounds: Int = BridgeToolRuntime.MAX_TOOL_ROUNDS
    ): String {
        val apiMessages = buildMessages(messages, system)
        val trace = mutableListOf<String>()
        lastToolTrace = emptyList()

        repeat(maxToolRounds.coerceIn(1, 8)) {
            val body = JSONObject()
                .put("model", config.model)
                .put("messages", apiMessages)
                .put("tools", BridgeToolRuntime.definitions())
                .put("tool_choice", "auto")

            val response = post(body)
            val message = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val toolCalls = message.optJSONArray("tool_calls")

            if (toolCalls == null || toolCalls.length() == 0) {
                lastToolTrace = trace.toList()
                return message.optString("content")
            }

            val assistantMessage = JSONObject()
                .put("role", "assistant")
                .put("content", if (message.isNull("content")) JSONObject.NULL else message.optString("content"))
                .put("tool_calls", toolCalls)
            apiMessages.put(assistantMessage)

            for (i in 0 until toolCalls.length()) {
                val call = toolCalls.getJSONObject(i)
                val function = call.getJSONObject("function")
                val name = function.getString("name")
                val arguments = function.optString("arguments", "{}")
                trace += "Tool Call: $name $arguments"
                val result = BridgeToolRuntime.execute(name, arguments)
                trace += "Tool Result: $result"

                apiMessages.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", call.getString("id"))
                        .put("content", result)
                )
            }
        }

        error("Tool Calling 超过最大轮数，未得到最终 assistant 回复")
    }

    private fun buildMessages(messages: List<BridgeChatMessage>, system: String): JSONArray {
        val result = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        messages.forEach {
            if (it.role == "user" || it.role == "assistant") {
                result.put(JSONObject().put("role", it.role).put("content", it.content))
            }
        }
        return result
    }

    private fun post(body: JSONObject): JSONObject {
        val url = URL(config.baseUrl.trimEnd('/') + "/chat/completions")
        val c = url.openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 120000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        if (config.apiKey.isNotBlank()) c.setRequestProperty("Authorization", "Bearer " + config.apiKey)
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val responseCode = c.responseCode
        val stream = if (responseCode in 200..299) c.inputStream else c.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (responseCode !in 200..299) error("API $responseCode: $response")
        return JSONObject(response)
    }
}

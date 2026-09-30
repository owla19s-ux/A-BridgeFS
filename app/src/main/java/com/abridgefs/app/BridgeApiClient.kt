package com.abridgefs.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class BridgeApiConfig(val baseUrl: String, val apiKey: String, val model: String)

class BridgeApiClient(private val config: BridgeApiConfig) {
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
        val url = URL(config.baseUrl.trimEnd('/') + "/chat/completions")
        val c = url.openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 60000; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        if (config.apiKey.isNotBlank()) c.setRequestProperty("Authorization", "Bearer " + config.apiKey)
        val a = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        messages.forEach {
            if (it.role == "user" || it.role == "assistant")
                a.put(JSONObject().put("role", it.role).put("content", it.content))
        }
        val body = JSONObject().put("model", config.model).put("messages", a)
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val responseCode = c.responseCode
        val stream = if (responseCode in 200..299) c.inputStream else c.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (responseCode !in 200..299) error("API $responseCode: $response")
        return JSONObject(response).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content")
    }
}

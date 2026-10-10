package com.abridgefs.app.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Connector for APIs that implement the OpenAI-compatible /chat/completions contract. */
class OpenAICompatibleConnector(
    private val profile: AIProfile,
    private val client: OkHttpClient = defaultClient()
) : AIConnector {
    private val activeCall = AtomicReference<Call?>(null)

    /** Cancels the active chat request, if one is currently executing. */
    override fun cancelCurrentRequest(): Boolean = activeCall.get()?.let { call ->
        if (call.isCanceled()) return@let false
        call.cancel()
        true
    } ?: false

    override suspend fun send(request: AIRequest): AIResponse = withContext(Dispatchers.IO) {
        val messages = JsonArray().apply {
            request.history.forEach { message ->
                add(JsonObject().apply {
                    addProperty("role", message.role.apiValue)
                    addProperty("content", message.content)
                })
            }
            add(JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", request.userText)
            })
        }
        val payload = JsonObject().apply {
            addProperty("model", request.modelId?.takeIf { it.isNotBlank() } ?: profile.model)
            add("messages", messages)
        }
        val httpRequest = Request.Builder()
            .url(endpoint("chat/completions"))
            .header("Authorization", "Bearer ${profile.apiKey}")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        val call = client.newCall(httpRequest)
        check(activeCall.compareAndSet(null, call)) { "已有 AI 请求正在执行" }
        try {
            call.execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("AI API 请求失败（HTTP ${response.code}）：${safeError(body)}")
            }
            val root = runCatching { com.google.gson.JsonParser.parseString(body).asJsonObject }
                .getOrElse { throw IOException("AI API 返回了无法解析的 JSON") }
            val content = runCatching {
                root.getAsJsonArray("choices")
                    .get(0).asJsonObject
                    .getAsJsonObject("message")
                    .get("content").asString
            }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: throw IOException("AI API 响应中没有可用的 choices[0].message.content")
                AIResponse(content)
            }
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }

    /** Streams text from an OpenAI-compatible Server-Sent Events chat completion. */
    override suspend fun sendStreaming(request: AIRequest, onDelta: (String) -> Unit): AIResponse = withContext(Dispatchers.IO) {
        val messages = JsonArray().apply {
            request.history.forEach { message ->
                add(JsonObject().apply {
                    addProperty("role", message.role.apiValue)
                    addProperty("content", message.content)
                })
            }
            add(JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", request.userText)
            })
        }
        val payload = JsonObject().apply {
            addProperty("model", request.modelId?.takeIf { it.isNotBlank() } ?: profile.model)
            add("messages", messages)
            addProperty("stream", true)
        }
        val httpRequest = Request.Builder()
            .url(endpoint("chat/completions"))
            .header("Authorization", "Bearer ${profile.apiKey}")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        val call = client.newCall(httpRequest)
        check(activeCall.compareAndSet(null, call)) { "已有 AI 请求正在执行" }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    throw IOException("AI API 流式请求失败（HTTP ${response.code}）：${safeError(body)}")
                }
                val source = response.body?.source() ?: throw IOException("AI API 流式响应没有响应体")
                val result = StringBuilder()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break
                    val delta = runCatching {
                        val root = com.google.gson.JsonParser.parseString(data).asJsonObject
                        root.getAsJsonArray("choices").get(0).asJsonObject
                            .getAsJsonObject("delta").get("content")
                            ?.takeIf { !it.isJsonNull }?.asString
                    }.getOrNull().orEmpty()
                    if (delta.isNotEmpty()) {
                        result.append(delta)
                        onDelta(delta)
                    }
                }
                if (result.isEmpty()) throw IOException("AI API 流式响应中没有可用文本")
                AIResponse(result.toString())
            }
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }
    /** Performs a real non-streaming chat completion to verify model access and chat permissions. */
    suspend fun testChatCompletion(): String {
        return send(AIRequest(
            contextId = null,
            userText = "Reply with OK only."
        )).text
    }

    /** Fetches the provider's model catalog. The user still chooses and saves a model explicitly. */
    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint("models"))
            .header("Authorization", "Bearer ${profile.apiKey}")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("获取模型列表失败（HTTP ${response.code}）：${safeError(body)}")
            }
            val root = runCatching { com.google.gson.JsonParser.parseString(body).asJsonObject }
                .getOrElse { throw IOException("模型列表返回了无法解析的 JSON") }
            val data = root.getAsJsonArray("data") ?: return@withContext emptyList()
            data.mapNotNull { item ->
                runCatching { item.asJsonObject.get("id")?.asString }.getOrNull()
            }.filter { it.isNotBlank() }.distinct().sorted()
        }
    }

    private fun endpoint(path: String): String {
        val base = profile.normalizedBaseUrl
        require(base.startsWith("https://") || base.startsWith("http://")) {
            "API 地址必须以 http:// 或 https:// 开头"
        }
        return "$base/$path"
    }

    private fun safeError(body: String): String {
        if (body.isBlank()) return "服务未提供错误详情"
        // Keep server messages short and avoid reflecting request headers or API keys.
        return Regex("""(?i)(bearer\s+)[^\s"}]+""").replace(body) { match ->
            match.groupValues[1] + "[redacted]"
        }.take(240)
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

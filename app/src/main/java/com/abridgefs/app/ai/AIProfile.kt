package com.abridgefs.app.ai

/**
 * User-configured OpenAI-compatible API profile.
 * The API key is never included in toString() or UI summaries.
 */
data class AIProfile(
    val id: String = DEFAULT_ID,
    val name: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String
) {
    init {
        require(id.isNotBlank()) { "API Profile ID 不能为空" }
        require(name.isNotBlank()) { "API 名称不能为空" }
        require(baseUrl.isNotBlank()) { "API 地址不能为空" }
        require(model.isNotBlank()) { "模型名称不能为空" }
        require(apiKey.isNotBlank()) { "API Key 不能为空" }
    }

    val normalizedBaseUrl: String
        get() = baseUrl.trim().trimEnd('/')

    companion object {
        const val DEFAULT_ID = "default-ai"
    }
}

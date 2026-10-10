package com.abridgefs.app.ai

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAICompatibleConnectorTest {
    @Test
    fun send_uses_configured_model_and_returns_assistant_content() = runBlocking {
        var captured: Request? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            captured = chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    """{"choices":[{"message":{"content":"Hello from API"}}]}"""
                        .toResponseBody("application/json".toMediaType())
                )
                .build()
        }.build()
        val profile = AIProfile(
            name = "Test API",
            baseUrl = "https://api.example.com/v1/",
            model = "model-test",
            apiKey = "secret-test"
        )

        val response = OpenAICompatibleConnector(profile, client).send(\n            AIRequest(\n                null,\n                "Hello",\n                history = listOf(\n                    AIMessage(AIMessage.Role.USER, "Earlier question"),\n                    AIMessage(AIMessage.Role.ASSISTANT, "Earlier answer")\n                )\n            )\n        )

        assertEquals("Hello from API", response.text)
        assertEquals("https://api.example.com/v1/chat/completions", captured?.url.toString())
        assertEquals("Bearer secret-test", captured?.header("Authorization"))
        assertTrue(captured?.body?.let { body ->
            val buffer = okio.Buffer()
            body.writeTo(buffer)
            buffer.readUtf8().contains("\"model\":\"model-test\"")
        } == true)
    }

    @Test
    fun list_models_returns_model_ids() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    """{"data":[{"id":"model-b"},{"id":"model-a"}]}"""
                        .toResponseBody("application/json".toMediaType())
                )
                .build()
        }.build()
        val profile = AIProfile(
            name = "Test API",
            baseUrl = "https://api.example.com/v1",
            model = "model-a",
            apiKey = "secret-test"
        )

        val models = OpenAICompatibleConnector(profile, client).listModels()

        assertEquals(listOf("model-a", "model-b"), models)
    }
}

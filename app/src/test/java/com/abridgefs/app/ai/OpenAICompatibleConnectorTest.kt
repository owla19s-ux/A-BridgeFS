package com.abridgefs.app.ai

import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAICompatibleConnectorTest {
    @Test
    fun send_uses_configured_model_and_prior_conversation_turns() = runBlocking {
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
            model = "conversation-model",
            apiKey = "secret-test"
        )

        val response = OpenAICompatibleConnector(profile, client).send(
            AIRequest(
                null,
                "Hello",
                modelId = "conversation-model",
                history = listOf(
                    AIMessage(AIMessage.Role.USER, "Earlier question"),
                    AIMessage(AIMessage.Role.ASSISTANT, "Earlier answer")
                )
            )
        )

        assertEquals("Hello from API", response.text)
        assertEquals("https://api.example.com/v1/chat/completions", captured?.url.toString())
        assertEquals("Bearer secret-test", captured?.header("Authorization"))
        val requestBody = captured?.body?.let { body ->
            val buffer = okio.Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        }.orEmpty()
        assertTrue(requestBody.contains("\"model\":\"conversation-model\""))
        assertTrue(requestBody.contains("\"role\":\"assistant\""))
        assertTrue(requestBody.contains("Earlier answer"))
    }

    @Test
    fun send_streaming_emits_chunks_and_sets_stream_flag() = runBlocking {
        var captured: Request? = null
        val eventBody = listOf(
            """data: {"choices":[{"delta":{"content":"Hello"}}]}""",
            "",
            """data: {"choices":[{"delta":{"content":" world"}}]}""",
            "",
            "data: [DONE]",
            ""
        ).joinToString("\n")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            captured = chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(eventBody.toResponseBody("text/event-stream".toMediaType()))
                .build()
        }.build()
        val profile = AIProfile(
            name = "Test API",
            baseUrl = "https://api.example.com/v1",
            model = "model-a",
            apiKey = "secret-test"
        )
        val chunks = mutableListOf<String>()

        val response = OpenAICompatibleConnector(profile, client).sendStreaming(
            AIRequest(null, "Say hello"),
            onDelta = chunks::add
        )

        assertEquals("Hello world", response.text)
        assertEquals(listOf("Hello", " world"), chunks)
        assertEquals("text/event-stream", captured?.header("Accept"))
        val requestBody = captured?.body?.let { body ->
            val buffer = okio.Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        }.orEmpty()
        assertTrue(requestBody.contains("\"stream\":true"))
    }

    @Test
    fun test_chat_completion_uses_chat_endpoint_and_returns_reply() = runBlocking {
        var captured: Request? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            captured = chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"choices":[{"message":{"content":"OK"}}]}"""
                    .toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val profile = AIProfile(
            name = "Test API",
            baseUrl = "https://api.example.com/v1",
            model = "model-a",
            apiKey = "secret-test"
        )

        assertEquals("OK", OpenAICompatibleConnector(profile, client).testChatCompletion())
        assertEquals("https://api.example.com/v1/chat/completions", captured?.url.toString())
        assertTrue(captured?.method == "POST")
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
    @Test
    fun cancelCurrentRequest_cancelsAnActiveChatCall() {
        val interceptorEntered = CountDownLatch(1)
        val allowRequestToContinue = CountDownLatch(1)
        val client = OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                interceptorEntered.countDown()
                check(allowRequestToContinue.await(3, TimeUnit.SECONDS))
                chain.proceed(chain.request())
            }
            .build()
        val profile = AIProfile(
            name = "Test API",
            baseUrl = "https://api.example.com/v1",
            model = "model-a",
            apiKey = "secret-test"
        )
        val connector = OpenAICompatibleConnector(profile, client)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<AIResponse> {
                runBlocking { connector.send(AIRequest(null, "Cancel this request")) }
            }
            assertTrue("Chat request did not start", interceptorEntered.await(2, TimeUnit.SECONDS))
            assertTrue("Active request should accept cancellation", connector.cancelCurrentRequest())
            allowRequestToContinue.countDown()
            try {
                future.get(3, TimeUnit.SECONDS)
                throw AssertionError("Expected cancellation to abort the HTTP request")
            } catch (error: ExecutionException) {
                assertTrue("Expected IOException but was " + error.cause, error.cause is IOException)
            }
            assertFalse(connector.cancelCurrentRequest())
        } finally {
            allowRequestToContinue.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun cancelCurrentRequest_returnsFalseWhenNoChatRequestIsActive() {
        val profile = AIProfile(
            name = "Test API",
            baseUrl = "https://api.example.com/v1",
            model = "model-a",
            apiKey = "secret-test"
        )

        assertEquals(false, OpenAICompatibleConnector(profile).cancelCurrentRequest())
    }

}

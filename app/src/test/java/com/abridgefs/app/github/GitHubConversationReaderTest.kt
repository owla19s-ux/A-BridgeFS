package com.abridgefs.app.github

import com.abridgefs.app.github.api.GitHubApi
import com.google.gson.JsonObject
import java.lang.reflect.Proxy
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import kotlinx.coroutines.runBlocking

class GitHubConversationReaderTest {
    @Test
    fun readsUtf8FileAtSelectedRepositoryBranch() = runBlocking {
        var observed: List<String?> = emptyList()
        val api = proxyApi { method, args ->
            if (method.name == "file") {
                observed = args.filterNotNull().dropLast(1).map { it.toString() }
                Response.success(fileJson("你好 APS", "file"))
            } else null
        }

        val result = GitHubConversationReader(api).readFile(
            GitHubReadTarget("owla19s-ux/APS", "main"),
            "README.md"
        )

        assertEquals("你好 APS", result.text)
        assertEquals("owla19s-ux/APS", result.repository)
        assertEquals(listOf("owla19s-ux", "APS", "README.md", "main"), observed)
    }

    @Test
    fun rejectsPathsThatEscapeRepositoryContentNamespace() = runBlocking {
        var apiCalled = false
        val api = proxyApi { _, _ ->
            apiCalled = true
            Response.success(fileJson("content", "file"))
        }
        val rejected = runCatching {
            GitHubConversationReader(api).readFile(
                GitHubReadTarget("owner/repo", "main"),
                "../secrets.txt"
            )
        }.isFailure

        assertTrue(rejected)
        assertTrue(!apiCalled)
    }

    @Test
    fun rejectsNonFileAndOversizedContent() = runBlocking {
        val directoryApi = proxyApi { _, _ -> Response.success(fileJson("", "dir")) }
        val directoryRejected = runCatching {
            GitHubConversationReader(directoryApi).readFile(
                GitHubReadTarget("owner/repo", "main"), "folder"
            )
        }.isFailure
        assertTrue(directoryRejected)

        val largeApi = proxyApi { _, _ -> Response.success(fileJson("12345", "file")) }
        val largeRejected = runCatching {
            GitHubConversationReader(largeApi, maxBytes = 4).readFile(
                GitHubReadTarget("owner/repo", "main"), "large.txt"
            )
        }.isFailure
        assertTrue(largeRejected)
    }

    private fun fileJson(text: String, type: String): JsonObject = JsonObject().apply {
        addProperty("type", type)
        addProperty("encoding", "base64")
        addProperty(
            "content",
            Base64.getMimeEncoder().encodeToString(text.toByteArray(StandardCharsets.UTF_8))
        )
    }

    private fun proxyApi(handler: (java.lang.reflect.Method, Array<Any?>) -> Any?): GitHubApi =
        Proxy.newProxyInstance(
            GitHubApi::class.java.classLoader,
            arrayOf(GitHubApi::class.java)
        ) { _, method, args ->
            handler(method, args ?: emptyArray())
        } as GitHubApi
}

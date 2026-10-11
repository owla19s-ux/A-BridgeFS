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
    fun rejectsMalformedRepositoryAddressBeforeCallingApi() = runBlocking {
        var apiCalled = false
        val api = proxyApi { _, _ ->
            apiCalled = true
            Response.success(fileJson("content", "file"))
        }
        val rejected = runCatching {
            GitHubConversationReader(api).readFile(
                GitHubReadTarget("owner/repo/extra", "main"),
                "README.md"
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

    @Test
    fun listsDirectoryEntriesWithDirectoriesFirstAndIgnoresUnknownTypes() = runBlocking {
        val api = proxyApi { method, _ ->
            if (method.name == "rootContents") {
                Response.success(listOf(
                    JsonObject().apply {
                        addProperty("name", "z-file.txt")
                        addProperty("path", "z-file.txt")
                        addProperty("type", "file")
                    },
                    JsonObject().apply {
                        addProperty("name", "docs")
                        addProperty("path", "docs")
                        addProperty("type", "dir")
                    },
                    JsonObject().apply {
                        addProperty("name", "ignored")
                        addProperty("path", "ignored")
                        addProperty("type", "symlink")
                    }
                ))
            } else null
        }

        val entries = GitHubConversationReader(api).listDirectory(
            GitHubReadTarget("owner/repo", "main")
        )

        assertEquals(listOf("docs", "z-file.txt"), entries.map { it.name })
        assertEquals(listOf(true, false), entries.map { it.isDirectory })
    }

    @Test
    fun rejectsDirectoryPathsThatEscapeRepository() = runBlocking {
        var apiCalled = false
        val api = proxyApi { _, _ ->
            apiCalled = true
            Response.success(emptyList<JsonObject>())
        }
        assertTrue(runCatching {
            GitHubConversationReader(api).listDirectory(
                GitHubReadTarget("owner/repo"),
                "../outside"
            )
        }.isFailure)
        assertTrue(!apiCalled)
    }

    @Test
    fun readsSeveralFilesWithinCountAndAggregateLimits() = runBlocking {
        val api = proxyApi { method, args ->
            if (method.name == "file") {
                val path = args[2].toString()
                Response.success(fileJson("content:$path", "file"))
            } else null
        }

        val files = GitHubConversationReader(api).readFiles(
            GitHubReadTarget("owner/repo", "main"),
            listOf("README.md", "docs/STATUS.md")
        )

        assertEquals(listOf("README.md", "docs/STATUS.md"), files.map { it.path })
        assertEquals(listOf("content:README.md", "content:docs/STATUS.md"), files.map { it.text })
    }

    @Test
    fun rejectsDuplicateTooManyAndAggregateOversizedFileSelections() = runBlocking {
        val api = proxyApi { _, _ -> Response.success(fileJson("123456", "file")) }
        val reader = GitHubConversationReader(api, maxBytes = 6)

        assertTrue(runCatching {
            reader.readFiles(GitHubReadTarget("owner/repo"), listOf("a.txt", "a.txt"))
        }.isFailure)
        assertTrue(runCatching {
            reader.readFiles(
                GitHubReadTarget("owner/repo"),
                (1..(GitHubConversationReader.MAX_FILES + 1)).map { "$it.txt" }
            )
        }.isFailure)
        val largeApi = proxyApi { _, _ ->
            Response.success(fileJson("x".repeat(40 * 1024), "file"))
        }
        assertTrue(runCatching {
            GitHubConversationReader(largeApi, maxBytes = 40 * 1024).readFiles(
                GitHubReadTarget("owner/repo"),
                listOf("a.txt", "b.txt")
            )
        }.isFailure)
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

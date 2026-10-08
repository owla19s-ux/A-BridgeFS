package com.abridgefs.app.github

import com.abridgefs.app.context.Context
import com.abridgefs.app.context.ResourceRef
import com.abridgefs.app.github.api.FileWriteRequest
import com.abridgefs.app.github.api.GitHubApi
import com.abridgefs.app.github.api.GitHubApiFactory
import com.abridgefs.app.github.api.WorkflowDispatchRequest
import com.google.gson.JsonObject
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import retrofit2.Response

class GitHubConnectorTest {
    @Test
    fun connector_uses_connection_identity() {
        val connection = GitHubConnection(
            id = "github-main",
            address = GitHubAddress("owla19s-ux/APS", "main")
        )
        val api = proxyApi { _, _, _ -> null }

        val connector = GitHubConnector(api, connection)

        assertEquals(connection, connector.connection)
    }

    @Test
    fun connector_forwards_repository_branch_commit_and_file_to_connection_address() = runBlocking {
        val calls = mutableListOf<String>()
        val api = proxyApi { method, args, _ ->
            calls += method.name + ":" + args.filterNotNull().dropLast(1).joinToString(",")
            Response.success(JsonObject())
        }
        val connection = GitHubConnection(
            id = "github-main",
            address = GitHubAddress("owla19s-ux/APS", "main")
        )
        val connector = GitHubConnector(api, connection)

        connector.repository()
        connector.branch()
        connector.commit()
        connector.file("README.md")

        assertEquals(
            listOf(
                "repository:owla19s-ux,APS",
                "branch:owla19s-ux,APS,main",
                "commit:owla19s-ux,APS,main",
                "file:owla19s-ux,APS,README.md,main"
            ),
            calls
        )
    }

    @Test
    fun connector_requires_explicit_write_permission() = runBlocking {
        val calls = mutableListOf<String>()
        val api = proxyApi { method, _, _ ->
            calls += method.name
            Response.success(JsonObject())
        }
        val connection = GitHubConnection(
            id = "github-main",
            address = GitHubAddress("owla19s-ux/APS", "main")
        )
        val connector = GitHubConnector(api, connection)

        var denied = false
        try {
            connector.writeFile(
                "README.md",
                FileWriteRequest(
                    message = "test",
                    content = "dGVzdA==",
                    branch = "main"
                )
            )
        } catch (_: IllegalStateException) {
            denied = true
        }

        assertTrue(denied)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun factory_assembles_context_and_api_into_executable_connector() = runBlocking {
        val calls = mutableListOf<String>()
        val api = proxyApi { method, _, _ ->
            calls += method.name
            Response.success(JsonObject())
        }
        val context = Context("ctx-1", "APS")
            .withResource(
                ResourceRef.GitHub(
                    GitHubResource.Repository(177, "owla19s-ux/APS", "main")
                )
            )

        val connector = GitHubConnectorFactory.create(context, api)
        connector.repository()

        assertEquals(listOf("repository"), calls)
        assertEquals(
            GitHubConnection(
                "github:repository:177",
                GitHubAddress("owla19s-ux/APS", "main")
            ),
            connector.connection
        )
    }

    @Test
    fun public_github_api_reads_repository_and_file() = runBlocking {
        val api = GitHubApiFactory.create("")
        val repository = api.repository("owla19s-ux", "APS")
        val file = api.file("owla19s-ux", "APS", "README.md", "main")

        assertTrue(repository.isSuccessful)
        assertEquals("owla19s-ux/APS", repository.body()?.get("full_name")?.asString)
        assertTrue(file.isSuccessful)
        assertEquals("README.md", file.body()?.get("name")?.asString)
    }

    private fun proxyApi(
        handler: (java.lang.reflect.Method, Array<Any?>, Any?) -> Any?
    ): GitHubApi =
        Proxy.newProxyInstance(
            GitHubApi::class.java.classLoader,
            arrayOf(GitHubApi::class.java)
        ) { _, method, args ->
            handler(method, args ?: emptyArray(), null)
        } as GitHubApi
}

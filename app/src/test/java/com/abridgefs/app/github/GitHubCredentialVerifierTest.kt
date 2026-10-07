package com.abridgefs.app.github

import com.abridgefs.app.github.api.GitHubApi
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class GitHubCredentialVerifierTest {
    @Test
    fun verify_returns_account_and_repository_count() = runBlocking {
        val api = object : GitHubApi {
            override suspend fun currentUser(): Response<JsonObject> =
                Response.success(JsonObject().apply { addProperty("login", "test-user") })

            override suspend fun repositories(perPage: Int): Response<List<JsonObject>> =
                Response.success(listOf(JsonObject(), JsonObject()))

            override suspend fun repository(owner: String, repo: String) = error("unused")
            override suspend fun branches(owner: String, repo: String) = error("unused")
            override suspend fun branch(owner: String, repo: String, branch: String) = error("unused")
            override suspend fun file(owner: String, repo: String, path: String, ref: String?) = error("unused")
            override suspend fun commit(owner: String, repo: String, ref: String) = error("unused")
            override suspend fun workflowRuns(owner: String, repo: String, branch: String?, headSha: String?, perPage: Int) = error("unused")
            override suspend fun workflowRun(owner: String, repo: String, runId: Long) = error("unused")
            override suspend fun workflowArtifacts(owner: String, repo: String, runId: Long) = error("unused")
            override suspend fun dispatchWorkflow(owner: String, repo: String, workflowId: String, request: com.abridgefs.app.github.api.WorkflowDispatchRequest) = error("unused")
            override suspend fun updateFile(owner: String, repo: String, path: String, request: com.abridgefs.app.github.api.FileWriteRequest) = error("unused")
        }

        val result = GitHubCredentialVerifier(api).verify()

        assertEquals("test-user", result.login)
        assertEquals(2, result.repositoryCount)
    }
}

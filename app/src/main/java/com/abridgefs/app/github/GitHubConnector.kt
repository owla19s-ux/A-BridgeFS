package com.abridgefs.app.github

import com.abridgefs.app.github.api.GitHubApi
import com.google.gson.JsonObject
import retrofit2.Response

class GitHubConnector(
    private val api: GitHubApi,
    private val address: GitHubAddress
) {
    suspend fun repository(): Response<JsonObject> =
        api.repository(address.owner, address.repo)

    suspend fun branch(name: String = requireBranch()): Response<JsonObject> =
        api.branch(address.owner, address.repo, name)

    suspend fun commit(ref: String = requireBranch()): Response<JsonObject> =
        api.commit(address.owner, address.repo, ref)

    suspend fun file(path: String, ref: String? = address.branch): Response<JsonObject> =
        api.file(address.owner, address.repo, path, ref)

    suspend fun branches(): Response<List<JsonObject>> =
        api.branches(address.owner, address.repo)

    suspend fun workflowRuns(
        branch: String? = address.branch,
        headSha: String? = null,
        perPage: Int = 30
    ): Response<JsonObject> =
        api.workflowRuns(address.owner, address.repo, branch, headSha, perPage)

    suspend fun workflowRun(runId: Long): Response<JsonObject> =
        api.workflowRun(address.owner, address.repo, runId)

    suspend fun workflowArtifacts(runId: Long): Response<JsonObject> =
        api.workflowArtifacts(address.owner, address.repo, runId)

    suspend fun currentUser(): Response<JsonObject> =
        api.currentUser()

    private fun requireBranch(): String =
        address.branch?.takeIf { it.isNotBlank() }
            ?: error("GitHub Branch 未配置")
}

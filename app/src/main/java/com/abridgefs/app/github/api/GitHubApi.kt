package com.abridgefs.app.github.api

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApi {
    @GET("user") suspend fun currentUser(): Response<JsonObject>
    @GET("user/repos") suspend fun repositories(@Query("per_page") perPage: Int = 30): Response<List<JsonObject>>
    @GET("repos/{owner}/{repo}") suspend fun repository(@Path("owner") owner: String, @Path("repo") repo: String): Response<JsonObject>
    @GET("repos/{owner}/{repo}/branches") suspend fun branches(@Path("owner") owner: String, @Path("repo") repo: String): Response<List<JsonObject>>
    @GET("repos/{owner}/{repo}/branches/{branch}") suspend fun branch(@Path("owner") owner: String, @Path("repo") repo: String, @Path("branch") branch: String): Response<JsonObject>
    @GET("repos/{owner}/{repo}/contents/{path}") suspend fun file(@Path("owner") owner: String, @Path("repo") repo: String, @Path("path", encoded = true) path: String, @Query("ref") ref: String? = null): Response<JsonObject>
    @GET("repos/{owner}/{repo}/commits/{ref}") suspend fun commit(@Path("owner") owner: String, @Path("repo") repo: String, @Path("ref") ref: String): Response<JsonObject>
    @GET("repos/{owner}/{repo}/actions/runs") suspend fun workflowRuns(@Path("owner") owner: String, @Path("repo") repo: String, @Query("branch") branch: String? = null, @Query("head_sha") headSha: String? = null, @Query("per_page") perPage: Int = 30): Response<JsonObject>
    @GET("repos/{owner}/{repo}/actions/runs/{run_id}") suspend fun workflowRun(@Path("owner") owner: String, @Path("repo") repo: String, @Path("run_id") runId: Long): Response<JsonObject>
    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/artifacts") suspend fun workflowArtifacts(@Path("owner") owner: String, @Path("repo") repo: String, @Path("run_id") runId: Long): Response<JsonObject>
    @POST("repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches") suspend fun dispatchWorkflow(@Path("owner") owner: String, @Path("repo") repo: String, @Path("workflow_id") workflowId: String, @Body request: WorkflowDispatchRequest): Response<Unit>
    @PUT("repos/{owner}/{repo}/contents/{path}") suspend fun updateFile(@Path("owner") owner: String, @Path("repo") repo: String, @Path("path", encoded = true) path: String, @Body request: FileWriteRequest): Response<JsonObject>
}

data class WorkflowDispatchRequest(val ref: String, val inputs: Map<String, String> = emptyMap())
data class FileWriteRequest(val message: String, val content: String, val sha: String? = null, val branch: String? = null)

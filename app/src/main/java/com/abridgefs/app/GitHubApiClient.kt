package com.abridgefs.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GitHubApiClient(
    private val context: Context,
    private val accessToken: String
) {
    private val apiBase = "https://api.github.com"

    fun getCurrentUser(): JSONObject = get("/user")

    fun listRepositories(perPage: Int = 100): JSONArray =
        getArray("/user/repos?per_page=${perPage.coerceIn(1, 100)}&sort=updated")

    fun getRepository(owner: String, repo: String): JSONObject =
        get("/repos/${owner}/${repo}")

    fun getBranch(owner: String, repo: String, branch: String): JSONObject =
        get("/repos/${owner}/${repo}/branches/${java.net.URLEncoder.encode(branch, "UTF-8").replace("+", "%20")}")

    fun getCommit(owner: String, repo: String, commitSha: String): JSONObject =
        get("/repos/${owner}/${repo}/commits/${commitSha}")

    fun listBranches(owner: String, repo: String, perPage: Int = 100): JSONArray =
        getArray("/repos/${owner}/${repo}/branches?per_page=${perPage.coerceIn(1, 100)}")

    fun getFile(owner: String, repo: String, path: String, branch: String? = null): JSONObject {
        val encodedPath = path.trimStart('/').split('/').joinToString("/") {
            java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        val ref = branch?.takeIf { it.isNotBlank() }?.let {
            "?ref=" + java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }.orEmpty()
        return get("/repos/${owner}/${repo}/contents/${encodedPath}${ref}")
    }

    fun updateFile(owner: String, repo: String, path: String, content: String, message: String, branch: String?, sha: String): JSONObject {
        ensureEnabled()
        val encodedPath = path.trimStart('/').split('/').joinToString("/") {
            java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        val body = JSONObject()
            .put("message", message)
            .put("content", android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP))
            .put("sha", sha)
        if (!branch.isNullOrBlank()) body.put("branch", branch)
        return write("/repos/${owner}/${repo}/contents/${encodedPath}", body)
    }
    fun listWorkflowRuns(owner: String, repo: String, perPage: Int = 20): JSONObject =
        get("/repos/${owner}/${repo}/actions/runs?per_page=${perPage.coerceIn(1, 100)}")

    fun listWorkflowRunsForCommit(owner: String, repo: String, commitSha: String, perPage: Int = 20): JSONObject =
        get("/repos/${owner}/${repo}/actions/runs?head_sha=${commitSha}&per_page=${perPage.coerceIn(1, 100)}")

    fun getWorkflowRun(owner: String, repo: String, runId: Long): JSONObject =
        get("/repos/${owner}/${repo}/actions/runs/${runId}")

    private fun get(path: String): JSONObject {
        ensureEnabled()
        return JSONObject(readResponse(open(path)))
    }

    private fun getArray(path: String): JSONArray {
        ensureEnabled()
        return JSONArray(readResponse(open(path)))
    }

    private fun open(path: String): HttpURLConnection {
        require(accessToken.isNotBlank()) { "GitHub access token is not configured" }
        return (URL(apiBase + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer " + accessToken)
            setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
        }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (connection.responseCode !in 200..299) {
            error("GitHub API ${connection.responseCode}: ${response}")
        }
        return response
    }

    private fun write(path: String, body: JSONObject): JSONObject {
        val connection = open(path).apply {
            requestMethod = "PUT"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        return JSONObject(readResponse(connection))
    }
    private fun ensureEnabled() {
        check(AccessPolicy.isGithubEnabled(context)) {
            "GitHub 全局访问已关闭"
        }
    }
}

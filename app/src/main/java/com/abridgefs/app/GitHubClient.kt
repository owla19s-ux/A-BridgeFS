package com.abridgefs.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GitHubDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Long,
    val intervalSeconds: Long
)

data class GitHubRepository(
    val id: Long,
    val fullName: String,
    val private: Boolean,
    val defaultBranch: String
)

class GitHubPendingException(val nextIntervalSeconds: Long) : Exception()

class GitHubClient(private val accessToken: String? = null) {
    companion object {
        private const val API = "https://api.github.com"
        private const val OAUTH = "https://github.com/login"
        private const val API_VERSION = "2026-03-10"
    }

    fun requestDeviceCode(clientId: String): GitHubDeviceCode {
        require(clientId.isNotBlank()) { "GitHub Client ID 尚未配置" }
        val body = formPost("$OAUTH/device/code", mapOf("client_id" to clientId))
        return GitHubDeviceCode(
            body.getString("device_code"), body.getString("user_code"),
            body.getString("verification_uri"), body.getLong("expires_in"),
            body.optLong("interval", 5L)
        )
    }

    fun pollDeviceToken(clientId: String, deviceCode: String): String? {
        val body = formPost(
            "$OAUTH/oauth/access_token",
            mapOf(
                "client_id" to clientId,
                "device_code" to deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
            ),
            allowOAuthError = true
        )
        if (body.has("error")) {
            when (body.getString("error")) {
                "authorization_pending" -> return null
                "slow_down" -> throw GitHubPendingException(body.optLong("interval", 10L))
                "expired_token" -> error("GitHub 授权码已过期")
                "access_denied" -> error("GitHub 授权被拒绝")
                "device_flow_disabled" -> error("GitHub Device Flow 尚未启用")
                else -> error("GitHub 授权失败：" + body.optString("error"))
            }
        }
        return body.optString("access_token").ifBlank { null }
    }

    fun getLogin(): String = (request("$API/user") as JSONObject).getString("login")

    fun listRepositories(): List<GitHubRepository> {
        val array = request("$API/user/repos?per_page=100&sort=updated") as JSONArray
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            GitHubRepository(o.getLong("id"), o.getString("full_name"), o.optBoolean("private"), o.optString("default_branch"))
        }
    }

    fun listBranches(fullName: String): List<String> {
        val parts = fullName.split("/", limit = 2)
        require(parts.size == 2) { "Repository 格式必须为 owner/name" }
        val url = "$API/repos/" + parts[0] + "/" + parts[1] + "/branches?per_page=100"
        val array = request(url) as JSONArray
        return List(array.length()) { i -> array.getJSONObject(i).getString("name") }
    }

    private fun request(url: String): Any {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "GET"
        c.connectTimeout = 10000
        c.readTimeout = 20000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version", API_VERSION)
        accessToken?.takeIf { it.isNotBlank() }?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("GitHub API $code: " + text.take(300))
        return if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text)
    }

    private fun formPost(url: String, values: Map<String, String>, allowOAuthError: Boolean = false): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 10000
        c.readTimeout = 20000
        c.doOutput = true
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        val body = values.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299 && !(allowOAuthError && text.contains(""error""))) error("GitHub OAuth $code: " + text.take(300))
        return JSONObject(text)
    }
}

package com.abridgefs.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * GitHub access boundary for a Project Address.
 *
 * Repository/branch access belongs to the Project Address. Permission and
 * construction-lock checks stay at this boundary before GitHub operations.
 */
class ProjectGitHubService(
    private val context: android.content.Context,
    private val client: GitHubApiClient,
    private val address: ProjectGitHubAddress,
    private val project: BridgeProject? = null
) {
    fun currentAccount(): JSONObject { requireRead(); return client.getCurrentUser() }
    fun repositories(perPage: Int = 100): JSONArray { requireRead(); return client.listRepositories(perPage) }

    fun repository(): JSONObject {
        requireRead()
        val (owner, name) = repositoryParts()
        return client.getRepository(owner, name)
    }

    fun branches(perPage: Int = 100): JSONArray {
        requireRead()
        val (owner, name) = repositoryParts()
        return client.listBranches(owner, name, perPage)
    }

    fun branchHead(): String {
        requireRead()
        val (owner, name) = repositoryParts()
        val branch = address.branch?.takeIf { it.isNotBlank() } ?: error("GitHub Branch 未配置")
        return client.getBranch(owner, name, branch).optJSONObject("commit")?.optString("sha").orEmpty()
    }

    fun commit(commitSha: String): JSONObject {
        requireRead()
        val (owner, name) = repositoryParts()
        return client.getCommit(owner, name, commitSha)
    }

    fun file(path: String): JSONObject {
        requireRead()
        val (owner, name) = repositoryParts()
        return client.getFile(owner, name, path, address.branch)
    }

    fun workflowRuns(perPage: Int = 20): JSONObject {
        requireRead()
        val (owner, name) = repositoryParts()
        return client.listWorkflowRuns(owner, name, perPage)
    }

    fun workflowRunsForCommit(commitSha: String, perPage: Int = 20): JSONObject {
        requireRead()
        require(commitSha.isNotBlank()) { "Commit SHA 不能为空" }
        val (owner, name) = repositoryParts()
        return client.listWorkflowRunsForCommit(owner, name, commitSha, perPage)
    }

    fun workflowRun(runId: Long): JSONObject {
        requireRead()
        val (owner, name) = repositoryParts()
        return client.getWorkflowRun(owner, name, runId)
    }

    fun readText(path: String): String {
        val raw = file(path)
        val encoded = raw.optString("content").replace("\\n", "").trim()
        check(encoded.isNotBlank()) { "GitHub 文件没有可读取内容：$path" }
        return try {
            String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) {
            error("GitHub 文件内容解码失败：$path")
        }
    }

    fun commitLocalFile(
        localFile: java.io.File,
        relativePath: String,
        message: String,
        aiMemberId: String
    ): JSONObject {
        requireWritePermission(aiMemberId)
        check(localFile.isFile) { "本地文件不存在：$relativePath" }
        val (owner, name) = repositoryParts()
        val remote = runCatching { client.getFile(owner, name, relativePath, address.branch) }.getOrNull()
        return if (remote == null) {
            client.createFile(owner, name, relativePath, localFile.readText(Charsets.UTF_8), message, address.branch)
        } else {
            val remoteSha = remote.optString("sha").ifBlank { error("GitHub 文件缺少 SHA：$relativePath") }
            client.updateFile(owner, name, relativePath, localFile.readText(Charsets.UTF_8), message, address.branch, remoteSha)
        }
    }

    fun createFile(path: String, content: String, message: String, aiMemberId: String): JSONObject {
        requireWritePermission(aiMemberId)
        val (owner, name) = repositoryParts()
        return client.createFile(owner, name, path, content, message, address.branch)
    }

    fun updateFile(path: String, content: String, message: String, sha: String, aiMemberId: String): JSONObject {
        requireWritePermission(aiMemberId)
        val (owner, name) = repositoryParts()
        return client.updateFile(owner, name, path, content, message, address.branch, sha)
    }

    fun requireWritePermission() {
        requireRead()
        check(address.writeEnabled) { "当前 Project 未允许 GitHub 修改" }
    }

    fun requireWritePermission(aiMemberId: String) {
        requireWritePermission()
        ConstructionLockStore(context).requireHolder(
            project ?: error("GitHub 写入必须绑定 Project"),
            aiMemberId
        )
    }

    private fun requireRead() {
        check(address.readEnabled) { "当前 Project 未允许 GitHub 读取" }
    }

    private fun repositoryParts(): Pair<String, String> {
        check(address.isConfigured()) { "GitHub Repository 未配置" }
        val fullName = address.repository!!.trim()
        val parts = fullName.split('/', limit = 2)
        check(parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            "GitHub Repository 格式应为 owner/name"
        }
        return parts[0] to parts[1]
    }
}

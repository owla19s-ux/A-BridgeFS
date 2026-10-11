package com.abridgefs.app.github

import com.abridgefs.app.github.api.GitHubApi
import java.nio.charset.StandardCharsets
import java.util.Base64

data class GitHubReadTarget(
    val repository: String,
    val branch: String? = null
) {
    init {
        val parts = repository.trim().split('/')
        require(parts.size == 2 && parts.all { it.isNotBlank() }) {
            "GitHub Repository 格式应为 owner/name"
        }
        require(branch?.none { it.isISOControl() } != false) {
            "GitHub Branch 包含无效字符"
        }
    }
}

data class GitHubReadFile(
    val repository: String,
    val branch: String?,
    val path: String,
    val text: String
)

/** Read-only GitHub file reader for ordinary conversations, independent of a Context. */
class GitHubConversationReader(
    private val api: GitHubApi,
    private val maxBytes: Int = DEFAULT_MAX_BYTES
) {
    suspend fun readFile(target: GitHubReadTarget, path: String): GitHubReadFile {
        val address = GitHubAddress(target.repository, target.branch?.trim()?.ifBlank { null })
        val safePath = path.trim()
        require(safePath.isNotBlank()) { "GitHub 文件路径不能为空" }
        require(!safePath.startsWith("/") && safePath.split('/').none { it == ".." }) {
            "GitHub 文件路径无效"
        }
        require(safePath.none { it.isISOControl() }) { "GitHub 文件路径包含无效字符" }

        val response = api.file(address.owner, address.repo, safePath, address.branch)
        check(response.isSuccessful) { "GitHub 文件读取失败：HTTP ${response.code()}" }
        val body = response.body() ?: error("GitHub 未返回文件内容")
        check(body.get("type")?.asString == "file") { "当前路径不是普通文件" }
        check(body.get("encoding")?.asString == "base64") { "GitHub 返回了不支持的文件编码" }
        val encoded = body.get("content")?.takeUnless { it.isJsonNull }?.asString
            ?.takeIf { it.isNotBlank() }
            ?: error("GitHub 未返回可读取的文件内容")
        val bytes = runCatching { Base64.getMimeDecoder().decode(encoded) }
            .getOrElse { throw IllegalStateException("GitHub 文件内容不是有效的 Base64", it) }
        require(bytes.size <= maxBytes) {
            "文件过大（上限 ${maxBytes / 1024} KiB），请改读更小的文本文件"
        }
        val text = String(bytes, StandardCharsets.UTF_8)
        require('\u0000' !in text) { "文件包含二进制内容，不作为对话上下文读取" }
        return GitHubReadFile(address.repository, address.branch, safePath, text)
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 32 * 1024
    }
}

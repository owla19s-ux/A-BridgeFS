package com.abridgefs.app

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only GitHub context for ordinary conversations.
 *
 * This is intentionally separate from ConstructionLock and all write APIs.
 * The current active workspace supplies the repository/branch binding.
 */
class GitHubConversationReader(private val context: Context) {

    data class Result(
        val enabled: Boolean,
        val repository: String? = null,
        val branch: String? = null,
        val content: String = ""
    )

    fun readForConversation(workspace: BridgeProject?, userText: String): Result {
        if (!AccessPolicy.isGithubEnabled(context)) return Result(false)
        val current = workspace ?: return Result(false)
        val github = current.github
        if (!github.readEnabled || github.repository.isNullOrBlank()) return Result(false)

        val token = GitHubTokenStore(context).state().accessToken
            ?.takeIf { it.isNotBlank() }
            ?: return Result(false)

        val reader = GitHubWorkspaceService(
            context,
            GitHubApiClient(context, token),
            github,
            current
        )

        val requestedPaths = extractPaths(userText)
        val paths = if (requestedPaths.isNotEmpty()) {
            requestedPaths
        } else if (looksLikeRepositoryQuestion(userText)) {
            listOf("README.md")
        } else {
            emptyList()
        }

        if (paths.isEmpty()) {
            return Result(true, github.repository, github.branch)
        }

        val sections = mutableListOf<String>()
        var total = 0
        for (path in paths.distinct().take(4)) {
            runCatching {
                val file = reader.file(path)
                val content = decodeContent(file)
                if (content.isBlank()) return@runCatching
                val remaining = MAX_TOTAL_CHARS - total
                if (remaining <= 0) return@runCatching
                val clipped = content.take(remaining)
                sections += "### $path\n$clipped"
                total += clipped.length
            }
        }

        return Result(
            enabled = true,
            repository = github.repository,
            branch = github.branch,
            content = sections.joinToString("\n\n")
        )
    }

    private fun extractPaths(text: String): List<String> {
        val result = linkedSetOf<String>()
        val pattern = Regex(
            """(?<![A-Za-z0-9_.-])(?:app/src/|docs/|PROJECT/|gradle/|\.github/)[A-Za-z0-9_./-]+(?:\.kt|\.java|\.xml|\.gradle|\.md|\.yml|\.yaml|\.json|\.properties)?"""
        )
        pattern.findAll(text).forEach { result += it.value.trimEnd('.', ',', '，', '。', ')', '）', ']', '】') }

        Regex("""(?<![A-Za-z0-9_.-])(?:README\.md|build\.gradle(?:\.kts)?|settings\.gradle(?:\.kts)?|gradle\.properties)(?![A-Za-z0-9_.-])""")
            .findAll(text)
            .forEach { result += it.value }

        return result.toList()
    }

    private fun looksLikeRepositoryQuestion(text: String): Boolean {
        val lower = text.lowercase()
        return listOf(
            "github", "repository", "repo", "仓库", "代码", "源码",
            "readme", "文件", "实现", "代码怎么", "当前项目"
        ).any { lower.contains(it) }
    }

    private fun decodeContent(file: JSONObject): String {
        val encoded = file.optString("content", "")
            .replace("\n", "")
            .replace("\r", "")
        if (encoded.isBlank()) return ""
        return String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
    }

    companion object {
        private const val MAX_TOTAL_CHARS = 24000
    }
}

package com.abridgefs.app

import android.content.Context
import android.util.Base64
import org.json.JSONObject

/**
 * Read-only GitHub source for ordinary conversations.
 * Ordinary chat uses the global GitHub conversation binding, not a workspace.
 * This reader never writes to GitHub and never consults ConstructionLock.
 */
class GitHubConversationReader(private val context: Context) {
    data class Result(
        val requested: Boolean,
        val repository: String? = null,
        val branch: String? = null,
        val content: String = "",
        val error: String? = null
    )

    fun readForProject(project: BridgeProject, userText: String): Result {
        val requestedPaths = extractPaths(userText)
        val requested = requestedPaths.isNotEmpty() || looksLikeRepositoryQuestion(userText)
        if (!requested) return Result(requested = false)
        if (!AccessPolicy.isGithubEnabled(context)) return Result(true, error = "GitHub 全局访问已关闭。")
        if (!project.github.readEnabled) return Result(true, error = "当前 Project 未允许读取 GitHub。")
        val token = GitHubTokenStore(context).state().accessToken?.takeIf { it.isNotBlank() }
            ?: return Result(true, error = "GitHub 尚未授权，无法读取当前 Project Repository。")
        val repository = project.github.repository?.trim()
            ?: return Result(true, error = "当前 Project 尚未配置 GitHub Repository。")
        val reader = GitHubWorkspaceService(context, GitHubApiClient(context, token), project.github, project)
        val paths = if (requestedPaths.isNotEmpty()) requestedPaths else listOf("README.md")
        val sections = mutableListOf<String>()
        var total = 0
        for (path in paths.distinct().take(4)) {
            val file = try { reader.file(path) } catch (e: Exception) {
                return Result(true, repository, project.github.branch, error = "读取 GitHub 文件失败：" + path + "\n" + (e.message ?: "未知错误"))
            }
            val content = decodeContent(file)
            if (content.isBlank()) return Result(true, repository, project.github.branch, error = "GitHub 文件没有可读取的文本内容：" + path)
            val remaining = MAX_TOTAL_CHARS - total
            if (remaining <= 0) break
            val clipped = content.take(remaining)
            sections += "### " + path + "\n" + clipped
            total += clipped.length
        }
        return Result(true, repository, project.github.branch, sections.joinToString("\n\n"))
    }
    fun readForConversation(userText: String): Result {
        val requestedPaths = extractPaths(userText)
        val requested = requestedPaths.isNotEmpty() || looksLikeRepositoryQuestion(userText)
        if (!requested) return Result(requested = false)

        if (!AccessPolicy.isGithubEnabled(context)) {
            return Result(requested = true, error = "GitHub 全局访问已关闭，请先在「连接与访问」开启 GitHub。")
        }

        val auth = GitHubTokenStore(context).state()
        val token = auth.accessToken?.takeIf { it.isNotBlank() }
            ?: return Result(requested = true, error = "GitHub 尚未授权，无法读取仓库。")

        val configStore = GitHubConversationConfigStore(context)
        var config = configStore.state()

        if (config.repository.isNullOrBlank()) {
            val projects = BridgeProjectStore(context).load()
            val migrated = projects.asSequence()
                .map { it.github }
                .firstOrNull { !it.repository.isNullOrBlank() && it.readEnabled }
            if (migrated != null) {
                configStore.save(migrated.repository!!.trim(), migrated.branch)
                config = configStore.state()
            }
        }

        val repository = config.repository
            ?: return Result(requested = true, error = "GitHub 已授权，但尚未选择普通对话使用的 Repository。")
        val branch = config.branch

        val github = GitHubWorkspace(
            accountLogin = auth.login,
            repository = repository,
            branch = branch,
            readEnabled = true,
            writeEnabled = false
        )
        val reader = GitHubWorkspaceService(
            context,
            GitHubApiClient(context, token),
            github,
            null
        )

        val paths = if (requestedPaths.isNotEmpty()) requestedPaths else listOf("README.md")
        val sections = mutableListOf<String>()
        var total = 0

        for (path in paths.distinct().take(4)) {
            val file = try {
                reader.file(path)
            } catch (e: Exception) {
                return Result(
                    requested = true,
                    repository = repository,
                    branch = branch,
                    error = "读取 GitHub 文件失败：$path\n${e.message ?: "未知错误"}"
                )
            }

            val content = decodeContent(file)
            if (content.isBlank()) {
                return Result(
                    requested = true,
                    repository = repository,
                    branch = branch,
                    error = "GitHub 文件没有可读取的文本内容：$path"
                )
            }

            val remaining = MAX_TOTAL_CHARS - total
            if (remaining <= 0) break
            val clipped = content.take(remaining)
            sections += "### $path\n$clipped"
            total += clipped.length
        }

        return Result(
            requested = true,
            repository = repository,
            branch = branch,
            content = sections.joinToString("\n\n")
        )
    }

    private fun extractPaths(text: String): List<String> {
        val result = linkedSetOf<String>()
        val pattern = Regex(
            """(?<![A-Za-z0-9_.-])(?:app/src/|docs/|PROJECT/|gradle/|\.github/)[A-Za-z0-9_./-]+(?:\.kt|\.java|\.xml|\.gradle|\.md|\.yml|\.yaml|\.json|\.properties)?"""
        )
        pattern.findAll(text).forEach {
            result += it.value.trimEnd('.', ',', '，', '。', ')', '）', ']', '】')
        }
        Regex(
            """(?<![A-Za-z0-9_.-])(?:README\.md|build\.gradle(?:\.kts)?|settings\.gradle(?:\.kts)?|gradle\.properties)(?![A-Za-z0-9_.-])"""
        ).findAll(text).forEach { result += it.value }
        return result.toList()
    }

    private fun looksLikeRepositoryQuestion(text: String): Boolean {
        val lower = text.lowercase()
        return listOf("github", "repository", "repo", "仓库", "代码", "源码", "readme", "文件", "实现", "代码怎么", "当前项目")
            .any { lower.contains(it) }
    }

    private fun decodeContent(file: JSONObject): String {
        val encoded = file.optString("content", "").replace("\n", "").replace("\r", "")
        if (encoded.isBlank()) return ""
        return String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
    }

    companion object {
        private const val MAX_TOTAL_CHARS = 24000
    }
}

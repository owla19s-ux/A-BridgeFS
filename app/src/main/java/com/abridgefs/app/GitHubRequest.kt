package com.abridgefs.app

sealed class GitHubCommand {
    data class Write(val path: String, val content: String) : GitHubCommand()
    data class Edit(val path: String, val old: String, val new: String) : GitHubCommand()
}

object GitHubRequest {
    private val block = Regex("(?s)\\[githubfs\\](.*?)\\[/githubfs\\]", RegexOption.IGNORE_CASE)
    private val write = Regex("(?is)\\[write\\s*:\\s*([^\\]]+?)\\]\\s*(.*?)\\[/write\\]")
    private val edit = Regex("(?is)\\[edit\\s*:\\s*([^\\]]+?)\\]\\s*(.*?)\\[/edit\\]")
    var lastError: String? = null

    fun extractAll(text: String): List<String> =
        block.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.toList()

    fun parse(input: String): List<GitHubCommand> {
        lastError = null
        val normalized = input
            .replace(Regex("(?is)\\[githubfs\\]"), "")
            .replace(Regex("(?is)\\[/githubfs\\]"), "")
        val hits = mutableListOf<Pair<Int, GitHubCommand>>()
        write.findAll(normalized).forEach { m ->
            hits += m.range.first to GitHubCommand.Write(m.groupValues[1].trim(), m.groupValues[2])
        }
        edit.findAll(normalized).forEach { m ->
            val body = m.groupValues[2]
            val separator = body.indexOf("====")
            if (separator >= 0) hits += m.range.first to GitHubCommand.Edit(m.groupValues[1].trim(), body.substring(0, separator), body.substring(separator + 4))
            else lastError = "GitHub edit 指令缺少 ==== 分隔符"
        }
        if (hits.isEmpty() && lastError == null) lastError = "未识别到 GitHub 修改指令"
        return hits.sortedBy { it.first }.map { it.second }
    }
}

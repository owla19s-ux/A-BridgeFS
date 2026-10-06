package com.abridgefs.app

sealed class Command {
    data object ListTree : Command()
    data class Read(val path: String) : Command()
    data class Write(val path: String, val content: String) : Command()
    data class Edit(val path: String, val old: String, val new: String) : Command()
    data class Search(val glob: String) : Command()
    data class Grep(val keyword: String) : Command()
    data class Path(val path: String) : Command()
    data class CopyPath(val path: String) : Command()
    data class Mkdir(val path: String) : Command()
    data class Commit(val path: String, val message: String) : Command()
}

data class CommandParseResult(val commands: List<Command>, val error: String? = null)

object CommandParser {
    private val wrapperOpen = Regex("(?is)\\[bridgefs\\]")
    private val wrapperClose = Regex("(?is)\\[/bridgefs\\]")
    private val simple = Regex("(?is)\\[(list)\\]|\\[(read|search|grep|path|copy-path|mkdir|commit)\\s*:\\s*([^\\]]+?)\\]")
    private val write = Regex("(?is)\\[write\\s*:\\s*([^\\]]+?)\\]\\s*(.*?)\\[/write\\]")
    private val edit = Regex("(?is)\\[edit\\s*:\\s*([^\\]]+?)\\]\\s*(.*?)\\[/edit\\]")

    fun parse(input: String): CommandParseResult {
        if (input.isBlank()) return CommandParseResult(emptyList())
        val normalized = input.replace(wrapperOpen, "").replace(wrapperClose, "")
        val hits = mutableListOf<Pair<Int, Command>>()
        var error: String? = null

        simple.findAll(normalized).forEach { m ->
            val command = if (m.value.trim().equals("[list]", ignoreCase = true)) {
                Command.ListTree
            } else {
                val type = m.groupValues[2].trim().lowercase()
                val value = m.groupValues[3].trim()
                when (type) {
                    "read" -> Command.Read(value)
                    "search" -> Command.Search(value)
                    "grep" -> Command.Grep(value)
                    "path" -> Command.Path(value)
                    "copy-path" -> Command.CopyPath(value)
                    "mkdir" -> Command.Mkdir(value)
                    "commit" -> {
                        val parts = value.split("|", limit = 2)
                        if (parts.size != 2 || parts[0].trim().isBlank() || parts[1].trim().isBlank()) {
                            error = "commit 格式应为 [commit: 相对路径 | 提交信息]"
                            return@forEach
                        }
                        Command.Commit(parts[0].trim(), parts[1].trim())
                    }
                    else -> return@forEach
                }
            }
            hits += m.range.first to command
        }

        write.findAll(normalized).forEach { m ->
            val path = m.groupValues[1].trim()
            if (path.isNotBlank()) hits += m.range.first to Command.Write(path, m.groupValues[2])
        }

        edit.findAll(normalized).forEach { m ->
            val body = m.groupValues[2]
            val separator = body.indexOf("====")
            if (separator >= 0) {
                hits += m.range.first to Command.Edit(
                    m.groupValues[1].trim(),
                    body.substring(0, separator),
                    body.substring(separator + 4)
                )
            } else {
                error = "edit 指令缺少 ==== 分隔符，未执行该修改"
            }
        }

        val result = hits.sortedBy { it.first }.map { it.second }
        if (result.isEmpty() && error == null) {
            val hasWriteStart = Regex("(?is)\\[write\\s*:[^\\]]+?\\]").containsMatchIn(normalized)
            val hasEditStart = Regex("(?is)\\[edit\\s*:[^\\]]+?\\]").containsMatchIn(normalized)
            error = when {
                hasWriteStart -> "write 指令缺少 [/write] 结束标记，未写入文件"
                hasEditStart -> "edit 指令格式不完整，未执行修改"
                else -> "未识别到可执行的 BridgeFS 指令，请使用 [bridgefs]、[list]、[read: 路径]、[write: 路径]... 格式"
            }
        }
        return CommandParseResult(result, error)
    }
}

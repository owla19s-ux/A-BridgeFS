package com.abridgefs.app

sealed class Command {
 data object ListTree:Command()
 data class Read(val path:String):Command()
 data class Write(val path:String,val content:String):Command()
 data class Edit(val path:String,val old:String,val new:String):Command()
 data class Search(val glob:String):Command()
 data class Grep(val keyword:String):Command()
 data class Path(val path:String):Command()
 data class CopyPath(val path:String):Command()
 data class Mkdir(val path:String):Command()
}

object CommandParser {
 var lastError: String? = null

 /*
  * V0.1 parser:
  * - accepts optional [bridgefs] ... [/bridgefs] wrapper
  * - accepts commands embedded in normal AI prose / code fences
  * - keeps command order
  * - does not treat ordinary prose as executable
  * - write/edit bodies may contain arbitrary newlines and punctuation
  */
 private val wrapperOpen = Regex("(?is)\\[bridgefs\\]")
 private val wrapperClose = Regex("(?is)\\[/bridgefs\\]")
 private val simple = Regex(
     "(?is)\\[(list)\\]|\\[(read|search|grep|path|copy-path|mkdir)\\s*:\\s*([^\\]]+?)\\]"
 )
 private val write = Regex(
     "(?is)\\[write\\s*:\\s*([^\\]]+?)\\]\\s*(.*?)\\[/write\\]"
 )
 private val edit = Regex(
     "(?is)\\[edit\\s*:\\s*([^\\]]+?)\\]\\s*(.*?)\\[/edit\\]"
 )

 fun parse(input:String):List<Command>{
  lastError = null
  if (input.isBlank()) return emptyList()

  // Wrappers are protocol markers, not commands themselves.
  val normalized = input
      .replace(wrapperOpen, "")
      .replace(wrapperClose, "")

  val hits = mutableListOf<Pair<Int, Command>>()

  simple.findAll(normalized).forEach { m ->
   val whole = m.value.trim()
   val command = if (whole.equals("[list]", ignoreCase = true)) {
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
    val old = body.substring(0, separator)
    val new = body.substring(separator + 4)
    hits += m.range.first to Command.Edit(m.groupValues[1].trim(), old, new)
   } else {
    lastError = "edit 指令缺少 ==== 分隔符，未执行该修改"
   }
  }

  val result = hits.sortedBy { it.first }.map { it.second }
  if (result.isEmpty()) {
   val hasWriteStart = Regex("(?is)\\[write\\s*:[^\\]]+?\\]").containsMatchIn(normalized)
   val hasEditStart = Regex("(?is)\\[edit\\s*:[^\\]]+?\\]").containsMatchIn(normalized)
   lastError = when {
    hasWriteStart -> "write 指令缺少 [/write] 结束标记，未写入文件"
    hasEditStart && lastError == null -> "edit 指令格式不完整，未执行修改"
    lastError != null -> lastError
    else -> "未识别到可执行的 BridgeFS 指令，请使用 [bridgefs]、[list]、[read: 路径]、[write: 路径]... 格式"
   }
  }
  return result
 }
}

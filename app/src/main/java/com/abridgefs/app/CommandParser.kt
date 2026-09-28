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
 private val simple=Regex("(?m)^\\s*\\[(list)\\]\\s*$|^\\s*\\[(read|search|grep|path|copy-path|mkdir):\\s*(.*?)\\]\\s*$")
 private val write=Regex("(?s)(?:\\x60\\x60\\x60\\s*)?\\[write:\\s*(.+?)\\]\\s*(.*?)\\[/write\\]\\s*(?:\\x60\\x60\\x60)?")
 private val edit=Regex("(?s)(?:\\x60\\x60\\x60\\s*)?\\[edit:\\s*(.+?)\\]\\s*(.*?)\\[/edit\\]\\s*(?:\\x60\\x60\\x60)?")
 fun parse(input:String):List<Command>{
  lastError = null
  val h=mutableListOf<Pair<Int,Command>>()
  val trimmed=input.trim()
  if(trimmed.isBlank()) return emptyList()
  val hasUnclosedWrite=Regex("(?is)\\[write\\s*:[^\\]]+\\]").containsMatchIn(trimmed) && !write.containsMatchIn(trimmed)
  if(hasUnclosedWrite){
   lastError = "write 指令缺少 [/write] 结束标记，未写入文件"
   return emptyList()
  }
  simple.findAll(input).forEach{
   val s=it.value.trim()
   h+=it.range.first to when{
    s=="[list]"->Command.ListTree
    s.startsWith("[read:")->Command.Read(it.groupValues[3].trim())
    s.startsWith("[search:")->Command.Search(it.groupValues[3].trim())
    s.startsWith("[grep:")->Command.Grep(it.groupValues[3].trim())
    s.startsWith("[path:")->Command.Path(it.groupValues[3].trim())
    s.startsWith("[mkdir:")->Command.Mkdir(it.groupValues[3].trim())
    else->Command.CopyPath(it.groupValues[3].trim())
   }
  }
  write.findAll(input).forEach{h+=it.range.first to Command.Write(it.groupValues[1].trim(),it.groupValues[2])}
  edit.findAll(input).forEach{
   val b=it.groupValues[2]
   val p=b.indexOf("====")
   if(p>=0)h+=it.range.first to Command.Edit(it.groupValues[1].trim(),b.substring(0,p),b.substring(p+4))
  }
  val result=h.sortedBy{it.first}.map{it.second}
  if(result.isEmpty()) lastError = "未识别到可执行的 BridgeFS 指令，请使用 [list]、[read: 路径] 等格式"
  return result
 }
}

package com.abridgefs.app
sealed class Command { data object ListTree:Command(); data class Read(val path:String):Command(); data class Write(val path:String,val content:String):Command(); data class Edit(val path:String,val old:String,val new:String):Command() }
object CommandParser {
 private val simple=Regex("""(?m)^\s*\[(list)\]\s*$|^\s*\[(read):\s*(.*?)\]\s*$""")
 private val write=Regex("""(?s)\[write:\s*(.+?)\]\s*(.*?)\[/write\]""")
 private val edit=Regex("""(?s)\[edit:\s*(.+?)\]\s*(.*?)\[/edit\]""")
 fun parse(input:String):List<Command>{ val h=mutableListOf<Pair<Int,Command>>()
  simple.findAll(input).forEach{ h+=it.range.first to if(it.value.trim()=="[list]") Command.ListTree else Command.Read(it.groupValues[3].trim()) }
  write.findAll(input).forEach{ h+=it.range.first to Command.Write(it.groupValues[1].trim(),it.groupValues[2]) }
  edit.findAll(input).forEach{ val b=it.groupValues[2]; val p=b.indexOf("===="); if(p>=0) h+=it.range.first to Command.Edit(it.groupValues[1].trim(),b.substring(0,p),b.substring(p+4)) }
  return h.sortedBy{it.first}.map{it.second}
 }
}

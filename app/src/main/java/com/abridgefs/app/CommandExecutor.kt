package com.abridgefs.app
import java.io.File
import java.nio.charset.StandardCharsets
class CommandExecutor(private val root:File){
 fun execute(c:Command):String=when(c){Command.ListTree->list();is Command.Read->read(c.path);is Command.Write->write(c.path,c.content);is Command.Edit->edit(c.path,c.old,c.new)}
 private fun file(p:String)=PathSecurity.safe(root,p)
 private fun list():String=buildString{append("[Tool: List]\n");root.walkTopDown().filter{it!=root}.forEach{append("  ").append(it.relativeTo(root).path).append(if(it.isDirectory)"/" else "").append('\n')}}
 private fun read(p:String):String{val f=file(p)?:return "[Tool: Read] $p\n  ✗ 路径非法或越界";if(!f.isFile)return "[Tool: Read] $p\n  ✗ 文件不存在";return try{"[Tool: Read] $p\n  ✓ 读取成功\n\n"+f.readText(StandardCharsets.UTF_8)}catch(_:Exception){"[Tool: Read] $p\n  ✗ 读取失败"}}
 private fun write(p:String,c:String):String{val f=file(p)?:return "[Tool: Write] $p\n  ✗ 路径非法或越界";if(f.exists())return "[Tool: Write] $p\n  ✗ 文件已存在";return try{f.parentFile?.mkdirs();f.writeText(c,StandardCharsets.UTF_8);"[Tool: Write] $p\n  ✓ 创建并保存成功"}catch(_:Exception){"[Tool: Write] $p\n  ✗ 写入失败"}}
 private fun edit(p:String,o:String,n:String):String{val f=file(p)?:return "[Tool: Edit] $p\n  ✗ 路径非法或越界";if(!f.isFile)return "[Tool: Edit] $p\n  ✗ 文件不存在";return try{val t=f.readText(StandardCharsets.UTF_8);if(!t.contains(o))return "[Tool: Edit] $p\n  ✗ 找不到旧内容";f.writeText(t.replaceFirst(o,n),StandardCharsets.UTF_8);"[Tool: Edit] $p\n  ✓ 保存成功"}catch(_:Exception){"[Tool: Edit] $p\n  ✗ 编辑失败"}}
}

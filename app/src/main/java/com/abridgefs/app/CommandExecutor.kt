package com.abridgefs.app
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
class CommandExecutor(private val root:File, private val context:Context, private val project:BridgeProject? = null, private val aiMemberId:String? = null){
 fun execute(c:Command):String{
  val started=System.currentTimeMillis()
  AppLogger.log(context,"COMMAND_RECEIVED",c.toString())
  return try{
   val result=when(c){Command.ListTree->list();is Command.Read->read(c.path);is Command.Write->write(c.path,c.content);is Command.Edit->edit(c.path,c.old,c.new);is Command.Search->search(c.glob);is Command.Grep->grep(c.keyword);is Command.Path->path(c.path);is Command.CopyPath->copyPath(c.path);is Command.Mkdir->mkdir(c.path);is Command.Commit->commit(c.path,c.message)}
   val status=when { result.contains("✗") -> "FAIL"; result.contains("⚠") -> "WARN"; else -> "SUCCESS" }
   AppLogger.log(context,"COMMAND_RESULT","status=$status durationMs=${System.currentTimeMillis()-started} command=${c::class.simpleName}")
   result
  }catch(e:Exception){
   AppLogger.log(context,"COMMAND_EXCEPTION","${e::class.simpleName}: ${e.message}")
   throw e
  }
 }
 private fun file(p:String)=PathSecurity.safe(root,p)
 private fun list():String{
  val s=StringBuilder("[Tool: List]\n");var count=0
  val iterator=root.walkTopDown().iterator()
  while(iterator.hasNext()){
   val f=iterator.next();if(f==root)continue
   count++;if(count>1000){s.append("  ⚠ 已达到 1000 项上限，停止列出\n");break}
   s.append("  ").append(f.relativeTo(root).path).append(if(f.isDirectory)"/" else "").append("\n")
  }
  return s.toString()
 }
 private fun read(p:String):String{val f=file(p)?:return "[Tool: Read] $p\n  ✗ 路径非法或越界\n  — 已中止";if(!f.isFile)return "[Tool: Read] $p\n  ✗ 文件不存在\n  — 已中止";return try {"[Tool: Read] $p\n  ✓ 读取成功\n\n"+f.readText(StandardCharsets.UTF_8)} catch(e:Exception){"[Tool: Read] $p\n  ✗ 读取失败：${e.message}\n  — 已中止"}}
 private fun write(p:String,c:String):String{val f=file(p)?:return "[Tool: Write] $p\n  ✗ 路径非法或越界\n  — 已中止";if(f.exists())return "[Tool: Write] $p\n  ✗ 文件已存在\n  — 已中止";return try{val parent=f.parentFile;val existed=parent?.exists()==true;parent?.mkdirs();f.writeText(c,StandardCharsets.UTF_8);"[Tool: Write] $p\n  "+if(!existed)"✓ 创建目录 ${parent?.relativeTo(root)?.path}/\n  " else ""+"✓ 创建文件 ${f.name}\n  ✓ 写入 ${c.length} 字符\n  ✓ 保存成功"}catch(e:Exception){"[Tool: Write] $p\n  ✗ 写入失败：${e.message}\n  — 已中止"}}
 private fun edit(p:String,o:String,n:String):String{val f=file(p)?:return "[Tool: Edit] $p\n  ✗ 路径非法或越界\n  — 已中止";if(!f.isFile)return "[Tool: Edit] $p\n  ✗ 文件不存在\n  — 已中止";return try{val t=f.readText(StandardCharsets.UTF_8);if(!t.contains(o))return "[Tool: Edit] $p\n  ✗ 找不到旧内容\n  — 当前内容：\n$t";f.writeText(t.replaceFirst(o,n),StandardCharsets.UTF_8);"[Tool: Edit] $p\n  ✓ 找到并替换旧内容\n  ✓ 保存成功"}catch(e:Exception){"[Tool: Edit] $p\n  ✗ 编辑失败：${e.message}\n  — 已中止"}}
 private fun search(g:String):String{
  val rx=runCatching{g.split("*").joinToString(".*"){Regex.escape(it)}.toRegex(RegexOption.IGNORE_CASE)}
    .getOrElse{return "[Tool: Search] $g\n  ✗ 搜索条件无效\n  — 已中止"}
  val a=mutableListOf<String>();var files=0
  val iterator=root.walkTopDown().iterator()
  while(iterator.hasNext()&&files<500&&a.size<200){
   val f=iterator.next();if(!f.isFile)continue
   files++;if(rx.matches(f.name))a+=f.relativeTo(root).path
  }
  val limitMessage=if(files>=500||a.size>=200)"\n  ⚠ 已达到扫描上限，停止搜索\n" else ""
  return "[Tool: Search] $g\n"+if(a.isEmpty())"  ✗ 未找到\n" else a.joinToString("\n"){"  ✓ $it"}+limitMessage
 }
 private fun mkdir(p:String):String{val f=file(p)?:return "[Tool: Mkdir] $p\n  ✗ 路径非法或越界";if(f.exists())return "[Tool: Mkdir] $p\n  ✓ 已存在 ${f.absolutePath}";return try{if(f.mkdirs()||f.isDirectory)"[Tool: Mkdir] $p\n  ✓ 已创建 ${f.absolutePath}" else "[Tool: Mkdir] $p\n  ✗ 创建失败"}catch(e:Exception){"[Tool: Mkdir] $p\n  ✗ 创建失败：${e.message}"} }
 private fun path(p:String):String{val f=file(p)?:return "[Tool: Path] $p\n  ✗ 路径非法或越界\n  — 已中止";return "[Tool: Path] $p\n  ✓ ${f.absolutePath}"}
 private fun copyPath(p:String):String{val f=file(p)?:return "[Tool: CopyPath] $p\n  ✗ 路径非法或越界\n  — 已中止";return try{val cm=context.getSystemService(Context.CLIPBOARD_SERVICE)as ClipboardManager;cm.setPrimaryClip(ClipData.newPlainText("BridgeFS路径",f.absolutePath));"[Tool: CopyPath] $p\n  ✓ 已复制：${f.absolutePath}"}catch(e:Exception){"[Tool: CopyPath] $p\n  ✗ 复制失败：${e.message}\n  — 已中止"}}
 private fun commit(p:String,m:String):String{
  val currentProject=project?:return "[Tool: Commit] $p\n  ✗ 当前操作没有绑定 Project\n  — 已中止"
  val member=aiMemberId?.takeIf{it.isNotBlank()}?:return "[Tool: Commit] $p\n  ✗ 当前操作没有绑定 AI Member\n  — 已中止"
  val f=file(p)?:return "[Tool: Commit] $p\n  ✗ 路径非法或越界\n  — 已中止"
  return try{
   ProjectCommitService(context,currentProject,member).commit(f,p,m).receipt
  }catch(e:Exception){
   "[Tool: Commit] $p\n  ✗ 提交失败：" + (e.message ?: "未知错误") + "\n  — 已中止"
  }
 }
 private fun grep(k:String):String{
  val s=StringBuilder("[Tool: Grep] $k\n");val start=System.nanoTime();var files=0;var results=0;var stopped=false
  val binaryExt=setOf("png","jpg","jpeg","gif","webp","bmp","mp3","wav","m4a","aac","mp4","mkv","avi","webm","pdf","zip","rar","7z","apk","so","dex","bin","db","sqlite","ttf","otf")
  fun timedOut()=System.nanoTime()-start>=5_000_000_000L
  fun isBinary(f:File):Boolean{if(f.extension.lowercase()in binaryExt)return true;return try{f.inputStream().use{ins->val b=ByteArray(4096);val n=ins.read(b);n>0&&b.take(n).any{it.toInt()==0}}}catch(e:Exception){false}}
  val iterator=root.walkTopDown().iterator()
  while(iterator.hasNext()&&!stopped){
   val f=iterator.next()
   if(!f.isFile)continue
   if(timedOut()){s.append("  ⚠ 已达到 5 秒时间上限，停止扫描\n");break}
   files++;if(files>500){s.append("  ⚠ 已达到 500 个文件上限，停止扫描\n");break}
   if(isBinary(f))continue
   try{f.bufferedReader(StandardCharsets.UTF_8).useLines{lines->var lineNo=0;for(line in lines){lineNo++;if(timedOut()){s.append("  ⚠ 已达到 5 秒时间上限，停止扫描\n");stopped=true;break};if(line.contains(k,true)){results++;s.append("  ✓ ${f.relativeTo(root).path}:$lineNo: $line\n");if(results>=200){s.append("  ⚠ 已达到 200 条结果上限，停止扫描\n");stopped=true;break}}}}}
   catch(e:Exception){s.append("  ⚠ 读取失败 ${f.relativeTo(root).path}: ${e.message}\n")}
  }
  if(results==0&&!stopped&&files<=500&&!timedOut())s.append("  ✗ 未找到\n");return s.toString()
 }
}

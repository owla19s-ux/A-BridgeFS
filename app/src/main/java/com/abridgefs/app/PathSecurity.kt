package com.abridgefs.app
import java.io.File
object PathSecurity { fun safe(root:File,relative:String):File? { val clean=relative.trim().replace('\\','/'); if(clean.isBlank()||clean.startsWith("/")||clean.split('/').any{it==".."})return null; return try{val r=root.canonicalFile;val f=File(r,clean).canonicalFile;if(f.path==r.path||f.path.startsWith(r.path+File.separator))f else null}catch(_:Exception){null} } }

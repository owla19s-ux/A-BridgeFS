package com.abridgefs.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChatMessage(val role:String,val content:String,val time:Long=System.currentTimeMillis())
data class ExecutionRecord(val status:String,val command:String,val message:String,val time:Long=System.currentTimeMillis())
data class Project(
    val id:String,
    var name:String,
    val messages:MutableList<ChatMessage> = mutableListOf(),
    val executions:MutableList<ExecutionRecord> = mutableListOf()
)

class ProjectStore(private val context:Context) {
    private val prefs=context.getSharedPreferences("projects",Context.MODE_PRIVATE)
    private val key="data"

    fun load():MutableList<Project>{
        val raw=prefs.getString(key,"[]") ?: "[]"
        val a=JSONArray(raw)
        val out=mutableListOf<Project>()
        for(i in 0 until a.length()){
            val o=a.getJSONObject(i)
            val p=Project(o.getString("id"),o.getString("name"))
            val m=o.optJSONArray("messages") ?: JSONArray()
            for(j in 0 until m.length()){
                val x=m.getJSONObject(j)
                p.messages += ChatMessage(x.getString("role"),x.getString("content"),x.optLong("time"))
            }
            val e=o.optJSONArray("executions") ?: JSONArray()
            for(j in 0 until e.length()){
                val x=e.getJSONObject(j)
                p.executions += ExecutionRecord(x.getString("status"),x.getString("command"),x.getString("message"),x.optLong("time"))
            }
            out += p
        }
        return out
    }

    fun save(projects:List<Project>){
        val a=JSONArray()
        projects.forEach { p ->
            val o=JSONObject().put("id",p.id).put("name",p.name)
            o.put("messages",JSONArray().apply{p.messages.forEach{put(JSONObject().put("role",it.role).put("content",it.content).put("time",it.time))}})
            o.put("executions",JSONArray().apply{p.executions.forEach{put(JSONObject().put("status",it.status).put("command",it.command).put("message",it.message).put("time",it.time))}})
            a.put(o)
        }
        prefs.edit().putString(key,a.toString()).apply()
    }

    fun newProject(name:String):Project=Project(UUID.randomUUID().toString(),name)
}

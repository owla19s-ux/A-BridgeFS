package com.abridgefs.app

import android.content.*
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity:AppCompatActivity(){
    private lateinit var apiInput:EditText
    private lateinit var keyInput:EditText
    private lateinit var modelInput:EditText
    private lateinit var rootInput:EditText
    private lateinit var chatInput:EditText
    private lateinit var chatView:TextView
    private lateinit var projectTitle:TextView
    private val executor=Executors.newSingleThreadExecutor()
    private val store by lazy{ProjectStore(this)}
    private var projects=mutableListOf<Project>()
    private var current:Project?=null
    private val autoStepByProject=mutableMapOf<String,Int>()
    private val maxAutoSteps=8

    private val receiver=object:BroadcastReceiver(){
        override fun onReceive(context:Context,intent:Intent){
            val status=intent.getStringExtra("status") ?: "UNKNOWN"
            val command=intent.getStringExtra("command") ?: ""
            val message=intent.getStringExtra("message") ?: ""
            val projectId=intent.getStringExtra("projectId") ?: current?.id ?: return
            val project=projects.firstOrNull{it.id==projectId} ?: return
            project.executions += ExecutionRecord(status,command,message)
            project.messages += ChatMessage("tool","[BridgeFS Receipt]\nstatus="+status+"\ncommand="+command+"\n"+message)
            saveProjects()
            runOnUiThread{renderProject()}
            if(status=="SUCCEEDED"){
                continueAfterReceipt(project)
            }
        }
    }

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        projects=store.load()
        if(projects.isEmpty()) projects += store.newProject("AI+")
        current=projects.first()
        buildUi();loadSaved();renderProject()
        registerReceiver(receiver,IntentFilter("com.abridgefs.RESULT"),Context.RECEIVER_NOT_EXPORTED)
    }

    private fun buildUi(){
        val page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24)}
        projectTitle=TextView(this).apply{textSize=20f;setPadding(0,0,0,12)}
        chatView=TextView(this).apply{setPadding(0,12,0,12)}
        chatInput=EditText(this).apply{hint="输入消息";minLines=3}
        apiInput=EditText(this).apply{hint="API 地址，例如 https://.../v1"}
        keyInput=EditText(this).apply{hint="API Key";inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD}
        modelInput=EditText(this).apply{hint="模型名称"}
        rootInput=EditText(this).apply{hint="授权目录"}
        page.addView(projectTitle)
        page.addView(Button(this).apply{text="项目 / 对话记录";setOnClickListener{showProjects()}})
        page.addView(chatView);page.addView(chatInput)
        page.addView(Button(this).apply{text="发送";setOnClickListener{sendChat()}})
        page.addView(Button(this).apply{text="权限";setOnClickListener{showPermissions()}})
        page.addView(Button(this).apply{text="API 设置";setOnClickListener{showSettings()}})
        setContentView(ScrollView(this).apply{addView(page)})
    }

    private fun loadSaved(){
        val p=getPreferences(MODE_PRIVATE)
        apiInput.setText(p.getString("baseUrl",""))
        keyInput.setText(p.getString("apiKey",""))
        modelInput.setText(p.getString("model",""))
        rootInput.setText(p.getString("root","/storage/emulated/0/A3/A0项目"))
    }

    private fun showSettings(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(32,8,32,0)}
        box.addView(apiInput);box.addView(keyInput);box.addView(modelInput);box.addView(rootInput)
        AlertDialog.Builder(this).setTitle("API 设置").setView(box)
            .setPositiveButton("保存"){_,_->saveSettings()}.setNegativeButton("关闭",null).show()
    }

    private fun saveSettings(){
        getPreferences(MODE_PRIVATE).edit()
            .putString("baseUrl",apiInput.text.toString().trim())
            .putString("apiKey",keyInput.text.toString())
            .putString("model",modelInput.text.toString().trim())
            .putString("root",rootInput.text.toString().trim()).apply()
        Toast.makeText(this,"已保存",Toast.LENGTH_SHORT).show()
    }

    private fun showProjects(){
        val names=projects.map{it.name}.toTypedArray()
        AlertDialog.Builder(this).setTitle("项目").setItems(names){_,which->{current=projects[which];renderProject()}}
            .setPositiveButton("新建"){_,_->newProjectDialog()}.setNegativeButton("关闭",null).show()
    }

    private fun newProjectDialog(){
        val input=EditText(this).apply{hint="项目名称"}
        AlertDialog.Builder(this).setTitle("新建项目").setView(input)
            .setPositiveButton("创建"){_,_->
                val p=store.newProject(input.text.toString().trim().ifBlank{"未命名项目"})
                projects += p;current=p;saveProjects();renderProject()
            }.setNegativeButton("取消",null).show()
    }

    private fun renderProject(){
        val p=current ?: return
        projectTitle.text="A-BridgeFS  ·  "+p.name
        chatView.text=p.messages.joinToString("\n\n"){m->
            when(m.role){
                "user"->"你：\n"+m.content
                "assistant"->"AI：\n"+m.content
                else->"执行记录：\n"+m.content
            }
        }
    }

    private fun sendChat(){
        val message=chatInput.text.toString().trim()
        if(message.isBlank())return
        val project=current ?: return
        autoStepByProject[project.id]=0
        project.messages += ChatMessage("user",message)
        chatInput.text.clear();saveProjects();renderProject()
        requestAi(project)
    }

    private fun requestAi(project:Project){
        val config=ApiConfig(apiInput.text.toString().trim(),keyInput.text.toString(),modelInput.text.toString().trim())
        executor.execute{
            try{
                val system="你是 A-BridgeFS 的本地助手。你可以持续对话。需要本地操作时，只输出 [bridgefs] ... [/bridgefs] 操作块，不要声称已经执行；必须等待 BridgeFS Receipt。支持 [list]、[read: 文件]、[write: 文件] 内容 [/write]、[edit: 文件] 旧内容====新内容 [/edit]。收到 [BridgeFS Receipt] 后，根据真实结果继续当前任务；不要重复已经成功的操作。"
                val messages=project.messages.map{
                    if(it.role=="tool") ChatMessage("user",it.content) else it
                }
                val answer=ApiClient(config).chat(messages,system)
                runOnUiThread{
                    project.messages += ChatMessage("assistant",answer)
                    saveProjects();renderProject()
                    BridgeRequest.extract(answer)?.let{executeCommands(it,project)}
                }
            }catch(e:Exception){
                runOnUiThread{
                    project.messages += ChatMessage("tool","[API Error]\n"+(e.message?: "未知错误"))
                    saveProjects();renderProject()
                }
            }
        }
    }

    private fun continueAfterReceipt(project:Project){
        val steps=(autoStepByProject[project.id] ?: 0)+1
        autoStepByProject[project.id]=steps
        if(steps>maxAutoSteps){
            project.messages += ChatMessage("tool","[AI+ 自动执行暂停]\n已达到本轮自动连续执行上限 $maxAutoSteps 步。")
            saveProjects();renderProject()
            return
        }
        requestAi(project)
    }

    private fun executeCommands(text:String,project:Project){
        val auth=authorization()
        val commands=CommandParser.parse(text)
        if(commands.isEmpty())return
        val decision=commands.map{PermissionPolicy.check(it,auth)}
            .maxByOrNull{when(it){Decision.DENY->3;Decision.CONFIRM->2;Decision.ALLOW->1}} ?: Decision.DENY
        when(decision){
            Decision.DENY->{
                project.executions += ExecutionRecord("DENIED",text,"超出授权范围")
                project.messages += ChatMessage("tool","[BridgeFS Receipt]\nstatus=DENIED\ncommand="+text+"\n超出授权范围")
                saveProjects();renderProject()
            }
            Decision.CONFIRM->AlertDialog.Builder(this).setTitle("需要确认")
                .setMessage(text).setPositiveButton("执行"){_,_->startBridge(text,auth,project)}
                .setNegativeButton("拒绝"){_,_->recordDenied(text,project)}.show()
            Decision.ALLOW->startBridge(text,auth,project)
        }
    }

    private fun startBridge(text:String,auth:Authorization,project:Project){
        val i=Intent(this,BridgeService::class.java)
            .putExtra("root",auth.root)
            .putExtra("command",text)
            .putExtra("projectId",project.id)
        i.putStringArrayListExtra("allowed",ArrayList(auth.allowed.map{it.name}))
        i.putStringArrayListExtra("confirm",ArrayList(auth.confirm.map{it.name}))
        startForegroundService(i)
    }

    private fun recordDenied(text:String,project:Project){
        project.executions += ExecutionRecord("DENIED",text,"用户拒绝执行")
        project.messages += ChatMessage("tool","[BridgeFS Receipt]\nstatus=DENIED\ncommand="+text+"\n用户拒绝执行")
        saveProjects();renderProject()
    }

    private fun authorization():Authorization{
        val prefs=getPreferences(MODE_PRIVATE)
        val allowed=mutableSetOf<FileAction>();val confirm=mutableSetOf<FileAction>()
        FileAction.values().forEach{
            when(prefs.getString("perm_"+it.name,"allow")){
                "allow"->allowed+=it
                "confirm"->{allowed+=it;confirm+=it}
            }
        }
        return Authorization(rootInput.text.toString().trim(),allowed,confirm)
    }

    private fun showPermissions(){
        val actions=FileAction.values()
        val labels=arrayOf("查看目录","读取文件","创建文件","修改文件")
        val prefs=getPreferences(MODE_PRIVATE)
        val selected=actions.map{prefs.getString("perm_"+it.name,"allow")}.toMutableList()
        val rows=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,0,24,0)}
        actions.forEachIndexed{i,a->
            val spinner=Spinner(this)
            spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,arrayOf("允许","需确认","禁止"))
            spinner.setSelection(when(selected[i]){"confirm"->1;"deny"->2;else->0})
            spinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
                override fun onNothingSelected(parent:AdapterView<*>?){}
                override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){selected[i]=arrayOf("allow","confirm","deny")[position]}
            }
            rows.addView(LinearLayout(this).apply{
                orientation=LinearLayout.HORIZONTAL
                addView(TextView(this@MainActivity).apply{text=labels[i];layoutParams=LinearLayout.LayoutParams(0,-2,1f)})
                addView(spinner)
            })
        }
        AlertDialog.Builder(this).setTitle("本地权限")
            .setMessage("授权目录："+rootInput.text)
            .setView(rows)
            .setPositiveButton("保存"){_,_->
                actions.forEachIndexed{i,a->prefs.edit().putString("perm_"+a.name,selected[i]).apply()}
                Toast.makeText(this,"权限规则已保存",Toast.LENGTH_SHORT).show()
            }.setNegativeButton("关闭",null).show()
    }

    private fun saveProjects(){store.save(projects)}
    override fun onDestroy(){unregisterReceiver(receiver);executor.shutdownNow();super.onDestroy()}
}

package com.abridgefs.app

import android.app.*
import android.content.*
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.*
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.UUID

class ApsActivity : Activity() {
    private val store by lazy { BridgeProjectStore(this) }
    private val apis by lazy { ApiProfileStore(this) }
    private val prefs by lazy { getSharedPreferences("bridgefs",0) }
    private lateinit var host: FrameLayout
    private var projects=mutableListOf<BridgeProject>()
    private var project: BridgeProject?=null
    private var page=0
    private var configOpen=false
    private var displayOpen=false
    private var chatOpen=true
    private val settingOpen=mutableSetOf<String>()

    override fun onCreate(b:Bundle?){
        super.onCreate(b)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        projects=store.load()
        if(projects.isEmpty()) projects+=store.newProject("默认项目")
        val id=prefs.getString("active_project_id",null)
        project=projects.firstOrNull{it.id==id}?:projects.first()
        prefs.edit().putString("active_project_id",project!!.id).apply()
        shell()
    }
    private fun shell(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(c(R.color.bridgefs_surface))}
        host=FrameLayout(this)
        ViewCompat.setOnApplyWindowInsetsListener(host){v,i->v.setPadding(0,0,0,i.getInsets(WindowInsetsCompat.Type.ime()).bottom);i}
        root.addView(host,LinearLayout.LayoutParams(-1,0,1f))
        val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(8),dp(4),dp(8),dp(4))}
        nav.addView(nav("项目",0),LinearLayout.LayoutParams(0,dp(54),1f))
        nav.addView(nav("对话",1),LinearLayout.LayoutParams(0,dp(54),1f))
        nav.addView(nav("设置",2),LinearLayout.LayoutParams(0,dp(54),1f))
        root.addView(nav)
        ViewCompat.setOnApplyWindowInsetsListener(root){v,i->val x=i.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(0,x.top,0,x.bottom);i}
        setContentView(root);render()
    }
    private fun nav(s:String,p:Int)=TextView(this).apply{text=s;textSize=13f;gravity=Gravity.CENTER;setOnClickListener{page=p;render()}}
    private fun render(){host.removeAllViews();when(page){0->projectPage();1->chatPage();2->settingsPage()}}
    private fun projectPage(){
        val p=project?:return
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(10),dp(14),dp(8))}
        root.addView(TextView(this).apply{text=p.name;textSize=21f;typeface=Typeface.DEFAULT_BOLD;setTextColor(c(R.color.bridgefs_text_primary))})
        root.addView(TextView(this).apply{text=address(p);textSize=12f;setTextColor(c(R.color.bridgefs_text_secondary));setPadding(0,dp(3),0,dp(8))})
        root.addView(section("项目配置",configOpen){configOpen=!configOpen;render()})
        if(configOpen)root.addView(config(p))
        root.addView(card().apply{
            addView(TextView(this@ApsActivity).apply{text="待处理任务";textSize=15f;typeface=Typeface.DEFAULT_BOLD})
            listOf("UI 输入框问题","构建问题","签名冲突").forEach{t->addView(CheckBox(this@ApsActivity).apply{text=t;textSize=13f;setOnCheckedChangeListener{_,on->if(on)Toast.makeText(this@ApsActivity,"@$t 已加入输入区",Toast.LENGTH_SHORT).show()}})}
        },LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
        root.addView(section("项目主要对话",chatOpen){chatOpen=!chatOpen;render()},LinearLayout.LayoutParams(-1,dp(52)).apply{topMargin=dp(8)})
        if(chatOpen){
            val cv=p.activeConversation()
            root.addView(card().apply{
                if(cv.messages.isEmpty())addView(TextView(this@ApsActivity).apply{text="项目对话是主要工作入口。直接提问即可。";textSize=13f;setTextColor(c(R.color.bridgefs_text_secondary))})
                cv.messages.forEach{m->addView(TextView(this@ApsActivity).apply{text=m.content;textSize=14f;setTextIsSelectable(true);setPadding(dp(10),dp(8),dp(10),dp(8))})}
            })
        }
        root.addView(Button(this).apply{text="Request AI Assistance";setOnClickListener{Toast.makeText(this@ApsActivity,"协助入口已建立，下一阶段接入协助链",Toast.LENGTH_SHORT).show()}},LinearLayout.LayoutParams(-1,dp(44)).apply{topMargin=dp(8)})
        val scroll=ScrollView(this).apply{isFillViewport=true;addView(root)}
        host.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(10),dp(5),dp(10),dp(5))}
        val input=EditText(this).apply{hint="输入问题或工作目标……";maxLines=4;setPadding(dp(12),dp(8),dp(12),dp(8));background=round(c(R.color.bridgefs_input_surface),dp(14))}
        bar.addView(input,LinearLayout.LayoutParams(0,dp(52),1f))
        bar.addView(Button(this).apply{text="发送";setOnClickListener{send(input)}},LinearLayout.LayoutParams(dp(78),dp(52)).apply{marginStart=dp(6)})
        host.addView(bar,LinearLayout.LayoutParams(-1,dp(62)))
    }
    private fun config(p:BridgeProject)=card().apply{
        val name=EditText(this@ApsActivity).apply{setText(p.name);hint="项目名称";maxLines=1}
        addView(name)
        addView(Button(this@ApsActivity).apply{text="保存项目名称";setOnClickListener{p.name=name.text.toString().trim().ifBlank{"未命名项目"};save();render()}})
        addView(TextView(this@ApsActivity).apply{text="Project Address";textSize=15f;typeface=Typeface.DEFAULT_BOLD;setPadding(0,dp(10),0,dp(4))})
        addView(TextView(this@ApsActivity).apply{text="Local：${p.localAddress?: "未设置"}";textSize=13f;setTextColor(c(R.color.bridgefs_text_secondary))})
        addView(Button(this@ApsActivity).apply{text="选择 Local Project Address";setOnClickListener{pickLocal()}})
        addView(TextView(this@ApsActivity).apply{text="GitHub：${p.githubAddress.repository?: "未设置"} / ${p.githubAddress.branch?: "默认分支"}";textSize=13f;setTextColor(c(R.color.bridgefs_text_secondary));setPadding(0,dp(8),0,dp(4))})
        addView(Button(this@ApsActivity).apply{text="配置 GitHub Project Address";setOnClickListener{startActivity(Intent(this@ApsActivity,GitHubActivity::class.java).putExtra("projectId",p.id))}})
        val m=p.defaultMemberId?.let{id->p.aiMembers.firstOrNull{it.id==id}}
        addView(TextView(this@ApsActivity).apply{text="Default AI：${m?.name?:"未配置"}";textSize=13f;setPadding(0,dp(8),0,dp(4))})
        addView(Button(this@ApsActivity).apply{text="选择 API Profile";setOnClickListener{chooseApi()}})
        addView(CheckBox(this@ApsActivity).apply{text="允许当前 Project 修改本地文件";isChecked=p.localFileModifyEnabled;setOnCheckedChangeListener{_,v->p.localFileModifyEnabled=v;save()}})
    }.also{it.layoutParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(6)}}
    private fun chatPage(){
        val p=project?:return;val cv=p.activeConversation()
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(10),dp(14),dp(8))}
        root.addView(TextView(this).apply{text="对话";textSize=21f;typeface=Typeface.DEFAULT_BOLD})
        val m=p.defaultMemberId?.let{id->p.aiMembers.firstOrNull{it.id==id}};val an=m?.apiProfileId?.let{apis.find(it)?.name}?:"未绑定 API"
        root.addView(info("当前 API",an));root.addView(info("项目访问",address(p)))
        root.addView(card().apply{if(cv.messages.isEmpty())addView(TextView(this@ApsActivity).apply{text="还没有消息。";setTextColor(c(R.color.bridgefs_text_secondary))});cv.messages.forEach{addView(TextView(this@ApsActivity).apply{text=it.content;textSize=14f;setPadding(dp(8),dp(8),dp(8),dp(8))})}})
        host.addView(ScrollView(this).apply{addView(root)},LinearLayout.LayoutParams(-1,0,1f))
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(10),dp(5),dp(10),dp(5))}
        val input=EditText(this).apply{hint="输入消息……";maxLines=4;background=round(c(R.color.bridgefs_input_surface),dp(14))}
        bar.addView(input,LinearLayout.LayoutParams(0,dp(52),1f));bar.addView(Button(this).apply{text="发送";setOnClickListener{send(input)}},LinearLayout.LayoutParams(dp(78),dp(52)))
        host.addView(bar,LinearLayout.LayoutParams(-1,dp(62)))
    }
    private fun settingsPage(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(10),dp(14),dp(14))}
        root.addView(TextView(this).apply{text="设置";textSize=21f;typeface=Typeface.DEFAULT_BOLD;setPadding(0,0,0,dp(10))})
        listOf("连接","权限","文件","执行","外观","通知","日志","系统").forEach{n->
            val open=settingOpen.contains(n);root.addView(section(n,open){if(open)settingOpen.remove(n)else settingOpen.add(n);render()},LinearLayout.LayoutParams(-1,dp(52)).apply{bottomMargin=dp(4)})
            if(open)root.addView(card().apply{
                addView(TextView(this@ApsActivity).apply{text=when(n){"连接"->"API Profiles / GitHub / 其他连接模块";"权限"->"全局 API、GitHub 与 Project 权限";else->"系统级 ${n} 设置";};textSize=12f;setTextColor(c(R.color.bridgefs_text_secondary))})
                if(n=="连接") {addView(Button(this@ApsActivity).apply{text="API Profiles";setOnClickListener{startActivity(Intent(this@ApsActivity,ApiSettingsActivity::class.java))}});addView(Button(this@ApsActivity).apply{text="GitHub";setOnClickListener{startActivity(Intent(this@ApsActivity,GlobalAccessActivity::class.java))}})}
                if(n=="权限")addView(Button(this@ApsActivity).apply{text="打开权限设置";setOnClickListener{startActivity(Intent(this@ApsActivity,GlobalAccessActivity::class.java))}})
            })
        }
        host.addView(ScrollView(this).apply{addView(root)},LinearLayout.LayoutParams(-1,0,1f))
    }
    private fun send(input:EditText){
        val text=input.text.toString().trim();if(text.isBlank())return
        val p=project?:return;val cv=p.activeConversation();cv.messages+=BridgeChatMessage("user",text);input.setText("");save();render()
        Thread{val r=ProjectConversationService(this).send(p,cv,text);runOnUiThread{r.answer?.let{cv.messages+=BridgeChatMessage("assistant",it)};r.error?.let{cv.messages+=BridgeChatMessage("system",it)};save();render()}}.start()
    }
    private fun chooseApi(){
        val p=project?:return;val list=apis.list();if(list.isEmpty()){Toast.makeText(this,"请先在设置 → 连接 → API Profiles 添加 API",Toast.LENGTH_SHORT).show();return}
        AlertDialog.Builder(this).setTitle("选择 Default AI API").setItems(list.map{it.name.ifBlank{"未命名 API"}}.toTypedArray()){_,i->
            val a=list[i];val m=p.defaultMemberId?.let{id->p.aiMembers.firstOrNull{it.id==id}}
            if(m!=null)m.apiProfileId=a.id else {val x=BridgeAiMember(UUID.randomUUID().toString(),a.name.ifBlank{"默认 AI"},a.id);p.aiMembers+=x;p.defaultMemberId=x.id};save();render()
        }.show()
    }
    private fun pickLocal(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),3101)}
    @Suppress("DEPRECATION") override fun onActivityResult(r:Int,result:Int,data:Intent?){super.onActivityResult(r,result,data);if(r!=3101||result!=RESULT_OK)return;val id=DocumentsContract.getTreeDocumentId(data?.data?:return);val s=id.indexOf(':');if(s<=0)return;val rel=id.substring(s+1).trim('/');project?.localAddress=if(rel.isBlank())"/storage/emulated/0" else "/storage/emulated/0/$rel";save();render()}
    private fun save(){store.save(projects)}
    private fun address(p:BridgeProject):String{val l=p.localAddress.orEmpty();val g=p.githubAddress.repository.orEmpty();return when{l.isNotBlank()&&g.isNotBlank()->"Local：$l\nGitHub：$g / ${p.githubAddress.branch?: "默认分支"}";l.isNotBlank()->"Local：$l";g.isNotBlank()->"GitHub：$g / ${p.githubAddress.branch?: "默认分支"}";else->"尚未设置 Project Address"}}
    private fun section(s:String,o:Boolean,f:()->Unit)=TextView(this).apply{text="$s    ${if(o)"▴"else"▾"}";textSize=15f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(14),0,dp(14),0);background=round(c(R.color.bridgefs_input_surface),dp(12));setOnClickListener{f()}}
    private fun info(a:String,b:String)=card().apply{addView(TextView(this@ApsActivity).apply{text=a;textSize=11f;setTextColor(c(R.color.bridgefs_text_secondary))});addView(TextView(this@ApsActivity).apply{text=b;textSize=14f})}
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10));background=round(c(R.color.bridgefs_input_surface),dp(14))}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun c(id:Int)=resources.getColor(id,theme)
    private fun round(c:Int,r:Int)=GradientDrawable().apply{setColor(c);cornerRadius=r.toFloat()}
}

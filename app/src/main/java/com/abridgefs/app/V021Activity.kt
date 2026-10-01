package com.abridgefs.app

import android.app.*
import android.os.Bundle
import android.view.*
import android.widget.*
import android.graphics.drawable.GradientDrawable
import org.json.*
import java.util.UUID

data class ApiProfile(val id:String,val name:String,val baseUrl:String,val key:String,val model:String,val write:Boolean)

class V021Activity : Activity() {
 private val prefs by lazy{getSharedPreferences("bridgefs",0)}
 private val store by lazy{BridgeProjectStore(this)}
 private var projects=mutableListOf<BridgeProject>()
 private var project:BridgeProject?=null
 private var content:FrameLayout?=null
 private var apiId=""
 private enum class Page{WORKSPACE,CHAT,CONFIG}
 private var page=Page.WORKSPACE

 override fun onCreate(b:Bundle?){super.onCreate(b);window.statusBarColor=resources.getColor(R.color.bridgefs_surface);window.navigationBarColor=resources.getColor(R.color.bridgefs_surface);projects=store.load();if(projects.isEmpty())projects+=store.newProject("默认工作区");project=projects.first();apiId=apis().firstOrNull()?.id.orEmpty();build()}
 private fun build(){
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(resources.getColor(R.color.bridgefs_surface))}
  content=FrameLayout(this);root.addView(content,LinearLayout.LayoutParams(-1,0,1f))
  val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  nav.addView(nav("工作区",Page.WORKSPACE),LinearLayout.LayoutParams(0,58.dp(),1f))
  nav.addView(nav("对话",Page.CHAT),LinearLayout.LayoutParams(0,58.dp(),1f))
  nav.addView(nav("配置",Page.CONFIG),LinearLayout.LayoutParams(0,58.dp(),1f))
  root.addView(nav);setContentView(root);render()
 }
 private fun nav(t:String,p:Page)=TextView(this).apply{text=t;textSize=14f;gravity=Gravity.CENTER;setOnClickListener{page=p;render()}}
 private fun render(){content!!.removeAllViews();when(page){Page.WORKSPACE->workspace();Page.CHAT->chat();Page.CONFIG->config()}}

 private fun workspace(){
  val box=page("工作区","管理可参与协作的 API。默认读取权，修改权单独授权。")
  box.addView(label("API"))
  val list=apis()
  if(list.isEmpty())box.addView(info("还没有 API。添加后才能新建对话。"))
  list.forEach{a->
   val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;padding(12)}
   val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
   row.addView(TextView(this).apply{text=if(a.name.isBlank())"未命名 API" else a.name;textSize=16f},LinearLayout.LayoutParams(0,44.dp(),1f))
   row.addView(button("修改"){editApi(a)},LinearLayout.LayoutParams(72.dp(),40.dp()))
   row.addView(button("移除"){removeApi(a)},LinearLayout.LayoutParams(72.dp(),40.dp()).apply{marginStart=6.dp()})
   card.addView(row)
   card.addView(info((if(a.model.isBlank())"未设置模型" else a.model)+"\n读取：允许    修改："+if(a.write)"允许" else "未授权"))
   card.addView(Switch(this).apply{text="允许修改";isChecked=a.write;setOnCheckedChangeListener{_,v->saveApi(a.copy(write=v))}})
   box.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=10.dp()})
  }
  box.addView(button("＋ 添加 API"){editApi(null)},LinearLayout.LayoutParams(-1,48.dp()))
  box.addView(label("当前工作区"))
  box.addView(info("名称："+project!!.name+"\nGitHub："+(project!!.githubRepository?:"未连接")+"\nBranch："+(project!!.githubBranch?:"未设置")))
  box.addView(button("设置工作区 GitHub"){githubDialog()})
  content!!.addView(scroll(box))
 }
 private fun chat(){
  val box=page("对话","新建聊天并选择一个 API。API 权限由工作区统一管理。")
  val list=apis();val names=if(list.isEmpty())arrayOf("暂无 API")else list.map{if(it.name.isBlank())"未命名 API" else it.name}.toTypedArray()
  val spinner=Spinner(this);spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,names)
  val idx=list.indexOfFirst{it.id==apiId};if(idx>=0)spinner.setSelection(idx)
  spinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:View?,pos:Int,id:Long){if(pos<list.size)apiId=list[pos].id}}
  box.addView(spinner,LinearLayout.LayoutParams(-1,50.dp()))
  val msgs=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  project!!.messages.forEach{m->msgs.addView(TextView(this).apply{text=(if(m.role=="user")"你\n" else "AI\n")+m.content;textSize=14f;setPadding(12.dp(),10.dp(),12.dp(),10.dp());setTextIsSelectable(true);background=rounded(R.color.bridgefs_input_surface,12)},LinearLayout.LayoutParams(-1,-2).apply{topMargin=6.dp()})}
  box.addView(ScrollView(this).apply{addView(msgs)},LinearLayout.LayoutParams(-1,0,1f))
  val input=EditText(this).apply{hint="输入消息……";minLines=1;maxLines=4}
  val row=LinearLayout(this).apply{addView(input,LinearLayout.LayoutParams(0,56.dp(),1f));addView(button("发送"){send(input,apiId);input.text.clear()},LinearLayout.LayoutParams(72.dp(),56.dp()).apply{marginStart=6.dp()})}
  box.addView(row);box.addView(button("＋ 新建聊天"){project=store.newProject("新聊天 "+(projects.size+1));projects+=project!!;store.save(projects);render()})
  content!!.addView(scroll(box))
 }
 private fun send(input:EditText,id:String){
  val text=input.text.toString().trim();if(text.isBlank())return
  val a=apis().firstOrNull{it.id==id}?:run{Toast.makeText(this,"请先添加 API",Toast.LENGTH_SHORT).show();return}
  if(!AccessPolicy.isApiEnabled(this)){Toast.makeText(this,"API 全局访问已关闭",Toast.LENGTH_SHORT).show();return}
  project!!.messages+=BridgeChatMessage("user",text);store.save(projects);render()
  Thread{runCatching{BridgeApiClient(BridgeApiConfig(a.baseUrl,a.key,a.model)).chat(project!!.messages,"你是 A-BridgeFS 协作 AI。")}
   .onSuccess{answer->runOnUiThread{project!!.messages+=BridgeChatMessage("assistant",answer);store.save(projects);render()}}
   .onFailure{e->runOnUiThread{project!!.messages+=BridgeChatMessage("assistant","请求失败："+(e.message?:"未知错误"));store.save(projects);render()}}}.start()
 }
 private fun config(){
  val box=page("页面配置","设置不再藏在抽屉里；全局访问仍由独立页面控制。")
  box.addView(configRow("AI 与 API","管理 API 与模型"){page=Page.WORKSPACE;render()})
  box.addView(configRow("连接与访问","API / GitHub 全局访问开关"){startActivity(Intent(this,GlobalAccessActivity::class.java))})
  listOf("执行与权限","指令","文件与目录","通知","外观","系统","日志与诊断").forEach{c->box.addView(configRow(c,"打开 "+c+" 设置"){startActivity(Intent(this,SettingsCategoryActivity::class.java).putExtra("category",c))})}
  content!!.addView(scroll(box))
 }
 private fun editApi(old:ApiProfile?){
  val l=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;padding(16)}
  val n=field("名称",old?.name);val u=field("API 地址",old?.baseUrl);val k=field("API Key",old?.key);val m=field("模型",old?.model)
  listOf(n,u,k,m).forEach{l.addView(it)}
  AlertDialog.Builder(this).setTitle(if(old==null)"添加 API" else "修改 API").setView(l).setPositiveButton("保存"){_,_->saveApi(ApiProfile(old?.id?:UUID.randomUUID().toString(),n.text.toString().trim(),u.text.toString().trim(),k.text.toString(),m.text.toString().trim(),old?.write?:false))}.setNegativeButton("取消",null).show()
 }
 private fun githubDialog(){
  if(!AccessPolicy.isGithubEnabled(this)){Toast.makeText(this,"请先在配置中开启 GitHub 全局访问",Toast.LENGTH_SHORT).show();return}
  val l=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;padding(16)}
  val r=field("Repository（owner/name）",project!!.githubRepository);val b=field("Branch",project!!.githubBranch?:"main");l.addView(r);l.addView(b)
  AlertDialog.Builder(this).setTitle("工作区 GitHub").setView(l).setPositiveButton("保存"){_,_->project!!.githubRepository=r.text.toString().trim().ifBlank{null};project!!.githubBranch=b.text.toString().trim().ifBlank{null};store.save(projects);render()}.setNegativeButton("取消",null).show()
 }
 private fun removeApi(a:ApiProfile){AlertDialog.Builder(this).setTitle("移除 API").setMessage("确定移除 "+a.name+"？").setPositiveButton("移除"){_,_->saveApis(apis().filterNot{it.id==a.id})}.setNegativeButton("取消",null).show()}
 private fun saveApi(a:ApiProfile){saveApis(apis().filterNot{it.id==a.id}+a)}
 private fun saveApis(a:List<ApiProfile>){prefs.edit().putString("api_profiles",JSONArray().apply{a.forEach{put(JSONObject().put("id",it.id).put("name",it.name).put("baseUrl",it.baseUrl).put("key",it.key).put("model",it.model).put("write",it.write))}}.toString()).apply();render()}
 private fun apis():List<ApiProfile>{
  val raw=prefs.getString("api_profiles",null)?:return legacyApi()
  val arr=JSONArray(raw);return List(arr.length()){i->val o=arr.getJSONObject(i);ApiProfile(o.getString("id"),o.optString("name"),o.optString("baseUrl"),o.optString("key"),o.optString("model"),o.optBoolean("write",false))}
 }
 private fun legacyApi():List<ApiProfile>{val u=prefs.getString("api_base_url","").orEmpty();val m=prefs.getString("api_model","").orEmpty();if(u.isBlank()&&m.isBlank())return emptyList();val a=ApiProfile("legacy",prefs.getString("api_provider","API")?:"API",u,prefs.getString("api_key","").orEmpty(),m,false);prefs.edit().putString("api_profiles",JSONArray().put(JSONObject().put("id",a.id).put("name",a.name).put("baseUrl",a.baseUrl).put("key",a.key).put("model",a.model).put("write",false)).toString()).apply();return listOf(a)}
 private fun field(h:String,v:String?)=EditText(this).apply{hint=h;setText(v.orEmpty());textSize=14f}
 private fun configRow(t:String,s:String,a:()->Unit)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;padding(12);background=rounded(R.color.bridgefs_input_surface,12);setOnClickListener{a()};addView(TextView(this@V021Activity).apply{text=t+"  ›";textSize=15f});addView(TextView(this@V021Activity).apply{text=s;textSize=12f;setTextColor(resources.getColor(R.color.bridgefs_text_secondary));setPadding(0,4.dp(),0,0)})}.also{it.layoutParams=LinearLayout.LayoutParams(-1,72.dp()).apply{bottomMargin=10.dp()}}
 private fun page(t:String,s:String)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;padding(14);addView(TextView(this@V021Activity).apply{text=t;textSize=22f;setTypeface(null,1)});addView(info(s))}
 private fun label(t:String)=TextView(this).apply{text=t;textSize=14f;setTypeface(null,1);setPadding(4.dp(),14.dp(),4.dp(),6.dp())}
 private fun info(t:String)=TextView(this).apply{text=t;textSize=13f;setTextColor(resources.getColor(R.color.bridgefs_text_secondary));setPadding(4.dp(),8.dp(),4.dp(),10.dp())}
 private fun button(t:String,a:()->Unit)=TextView(this).apply{text=t;textSize=13f;gravity=Gravity.CENTER;setTextColor(resources.getColor(R.color.bridgefs_button_text));background=rounded(R.color.bridgefs_button_bg,10);setOnClickListener{a()}}
 private fun scroll(v:View)=ScrollView(this).apply{addView(v)}
 private fun rounded(res:Int,r:Int)=GradientDrawable().apply{setColor(resources.getColor(res));cornerRadius=r.dp().toFloat()}
 private fun Int.dp()=(this*resources.displayMetrics.density).toInt()
 private fun LinearLayout.padding(v:Int){setPadding(v.dp(),v.dp(),v.dp(),v.dp())}
}
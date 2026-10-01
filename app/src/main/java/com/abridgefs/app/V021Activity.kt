package com.abridgefs.app

import android.app.*
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

data class ApiProfile(val id:String,val name:String,val baseUrl:String,val key:String,val model:String,val write:Boolean)

class V021Activity : Activity() {
    private val prefs by lazy { getSharedPreferences("bridgefs", 0) }
    private val store by lazy { BridgeProjectStore(this) }
    private val apiSecrets by lazy { ApiSecretStore(this) }
    private var projects = mutableListOf<BridgeProject>()
    private var project: BridgeProject? = null
    private lateinit var content: FrameLayout
    private lateinit var navWorkspace: TextView
    private lateinit var navChat: TextView
    private lateinit var navConfig: TextView
    private var apiId = ""
    private enum class Page { WORKSPACE, CHAT, CONFIG }
    private var page = Page.WORKSPACE
    private val executor = Executors.newSingleThreadExecutor()
    private var pendingReceipt: String? = null
    private val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            val status = intent.getStringExtra("status") ?: "UNKNOWN"
            val command = intent.getStringExtra("command") ?: ""
            val message = intent.getStringExtra("message") ?: ""
            val projectId = intent.getStringExtra("projectId")
            val target = projects.firstOrNull { it.id == projectId } ?: project
            if (target != null) {
                val receipt = BridgeReceiptRecord(status, command, message)
                target.executions += receipt
                pendingReceipt = formatReceipt(receipt)
                store.save(projects)
                if (page == Page.CHAT) render()
                else Toast.makeText(this@V021Activity, "收到执行回执：$status", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.statusBarColor = resources.getColor(R.color.bridgefs_surface)
        window.navigationBarColor = resources.getColor(R.color.bridgefs_surface)
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        projects = store.load()
        if (projects.isEmpty()) projects += store.newProject("默认工作区")
        project = projects.first()
        apiId = project?.apiId ?: apis().firstOrNull()?.id.orEmpty()
        registerReceiver(receiver, IntentFilter("com.bridgefs.RESULT"), Context.RECEIVER_NOT_EXPORTED)
        buildShell()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.bridgefs_surface))
        }

        content = FrameLayout(this)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val system = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(0, 0, 0, if (page == Page.CHAT) ime else 0)
            view.tag = system
            insets
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = colorDrawable(R.color.bridgefs_surface, 0)
        }
        navWorkspace = navItem("⌂\n工作区", Page.WORKSPACE)
        navChat = navItem("◯\n对话", Page.CHAT)
        navConfig = navItem("⚙\n配置", Page.CONFIG)
        nav.addView(navWorkspace, LinearLayout.LayoutParams(0, dp(58), 1f))
        nav.addView(navChat, LinearLayout.LayoutParams(0, dp(58), 1f))
        nav.addView(navConfig, LinearLayout.LayoutParams(0, dp(58), 1f))
        root.addView(nav)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        setContentView(root)
        render()
    }

    private fun navItem(text:String, target:Page) = TextView(this).apply {
        this.text = text
        textSize = 12f
        gravity = Gravity.CENTER
        setLineSpacing(0f, 0.9f)
        setOnClickListener { page = target; render() }
    }

    private fun render() {
        content.removeAllViews()
        when (page) {
            Page.WORKSPACE -> renderWorkspace()
            Page.CHAT -> renderChat()
            Page.CONFIG -> renderConfig()
        }
        updateNav()
    }

    private fun updateNav() {
        listOf(
            navWorkspace to Page.WORKSPACE,
            navChat to Page.CHAT,
            navConfig to Page.CONFIG
        ).forEach { (v,p) ->
            v.setTextColor(if (p == page) color(R.color.bridgefs_accent) else color(R.color.bridgefs_text_secondary))
            v.background = if (p == page)
                colorDrawable(R.color.bridgefs_selected_surface, 14)
            else
                colorDrawable(R.color.bridgefs_surface, 14)
        }
    }

    private fun renderWorkspace() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(14))
        }

        root.addView(header("工作区", "管理协作资源与权限"))
        root.addView(workspaceCard())
        root.addView(sectionTitle("API"))
        val list = apis()
        if (list.isEmpty()) {
            root.addView(emptyCard("还没有 API", "添加一个 API 后即可进入对话。"))
        } else {
            list.forEach { root.addView(apiCard(it)) }
        }
        root.addView(actionButton("＋ 添加 API") { editApi(null) }, LinearLayout.LayoutParams(-1, dp(46)).apply {
            topMargin = dp(8)
        })

        val scroll = ScrollView(this)
        scroll.addView(root)
        content.addView(scroll)
    }

    private fun workspaceCard(): View {
        val box = card()
        val auth = GitHubTokenStore(this).state()
        val connected = AccessPolicy.isGithubEnabled(this) && !auth.accessToken.isNullOrBlank()
        box.addView(TextView(this).apply {
            text = project?.name ?: "默认工作区"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(this).apply {
            text = "GitHub"
            textSize = 12f
            setTextColor(color(R.color.bridgefs_text_secondary))
            setPadding(0, dp(10), 0, dp(2))
        })
        box.addView(TextView(this).apply {
            text = if (connected) "● 已连接 · " + (auth.login ?: "GitHub") else "○ 未连接"
            textSize = 14f
            setTextColor(if (connected) color(R.color.bridgefs_accent) else color(R.color.bridgefs_text_secondary))
        })
        box.addView(TextView(this).apply {
            text = project?.githubRepository ?: "未选择 Repository"
            textSize = 14f
            setPadding(0, dp(4), 0, dp(2))
        })
        box.addView(TextView(this).apply {
            text = "Branch  ·  " + (project?.githubBranch ?: "未选择")
            textSize = 12f
            setTextColor(color(R.color.bridgefs_text_secondary))
            setPadding(0, dp(2), 0, dp(8))
        })
        box.addView(actionButton("进入 GitHub") {
            startActivity(Intent(this, GitHubActivity::class.java))
        })
        return box
    }

    private fun apiCard(a:ApiProfile): View {
        val box = card()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply {
            text = a.name.ifBlank { "未命名 API" }
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        row.addView(textButton("修改") { editApi(a) }, LinearLayout.LayoutParams(dp(64), dp(38)))
        row.addView(textButton("移除") { removeApi(a) }, LinearLayout.LayoutParams(dp(64), dp(38)).apply { marginStart = dp(6) })
        box.addView(row)
        box.addView(TextView(this).apply {
            text = a.model.ifBlank { "未设置模型" }
            textSize = 13f
            setTextColor(color(R.color.bridgefs_text_secondary))
            setPadding(0, dp(4), 0, dp(8))
        })
        val access = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        access.addView(TextView(this).apply {
            text = "读取  已允许"
            textSize = 12f
            setTextColor(color(R.color.bridgefs_text_secondary))
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        access.addView(Switch(this).apply {
            text = "允许修改"
            textSize = 12f
            isChecked = a.write
            setOnCheckedChangeListener { _, v -> saveApi(a.copy(write = v)) }
        })
        box.addView(access)
        return box
    }

    private fun renderChat() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
        }
        root.addView(header("对话", "与当前工作区中的 API 协作"))

        val chatSelector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(8), dp(8))
            background = colorDrawable(R.color.bridgefs_input_surface, 14)
            setOnClickListener {
                if (projects.isEmpty()) return@setOnClickListener
                val labels = projects.map { it.name.ifBlank { "未命名对话" } }.toTypedArray()
                val currentIndex = projects.indexOfFirst { it.id == project?.id }.coerceAtLeast(0)
                AlertDialog.Builder(this@V021Activity)
                    .setTitle("切换对话")
                    .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                        project = projects[which]
                        apiId = project?.apiId.orEmpty()
                        store.save(projects)
                        dialog.dismiss()
                        render()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
        chatSelector.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@V021Activity).apply {
                text = "当前对话"
                textSize = 12f
                setTextColor(color(R.color.bridgefs_text_secondary))
            })
            addView(TextView(this@V021Activity).apply {
                text = project?.name ?: "未选择对话"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color(R.color.bridgefs_text_primary))
                setPadding(0, dp(3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        chatSelector.addView(TextView(this).apply {
            text = "切换 ›"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(color(R.color.bridgefs_accent))
        }, LinearLayout.LayoutParams(dp(72), dp(44)))
        root.addView(chatSelector, LinearLayout.LayoutParams(-1, dp(64)).apply { bottomMargin = dp(8) })

        val apis = apis()
        val selected = apis.firstOrNull { it.id == (project?.apiId ?: apiId) }
        val selector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(8), dp(10))
            background = colorDrawable(R.color.bridgefs_input_surface, 14)
            setOnClickListener {
                if (apis.isEmpty()) {
                    Toast.makeText(this@V021Activity, "请先在工作区添加 API", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val labels = apis.map { it.name.ifBlank { "未命名 API" } }.toTypedArray()
                val current = apis.indexOfFirst { it.id == (project?.apiId ?: apiId) }.coerceAtLeast(0)
                AlertDialog.Builder(this@V021Activity)
                    .setTitle("选择对话 API")
                    .setSingleChoiceItems(labels, current) { dialog, which ->
                        apiId = apis[which].id
                        project?.apiId = apiId
                        store.save(projects)
                        dialog.dismiss()
                        render()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
        selector.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@V021Activity).apply {
                text = "当前 API"
                textSize = 12f
                setTextColor(color(R.color.bridgefs_text_secondary))
            })
            addView(TextView(this@V021Activity).apply {
                text = selected?.name?.ifBlank { "未命名 API" } ?: "未选择 API"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color(R.color.bridgefs_text_primary))
                setPadding(0, dp(3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        selector.addView(TextView(this).apply {
            text = "选择 ›"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(color(R.color.bridgefs_accent))
        }, LinearLayout.LayoutParams(dp(72), dp(44)))
        root.addView(selector, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(8) })

        root.addView(textButton("＋ 新建对话") {
            project = store.newProject("新聊天 " + (projects.size + 1)).also { it.apiId = apis.firstOrNull()?.id }
            apiId = project?.apiId.orEmpty()
            projects += project!!
            store.save(projects)
            render()
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(4) })

        val messages = ScrollView(this)
        val messageBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        project?.messages?.forEach { m ->
            val bubbleBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(4), dp(4), dp(2))
                background = colorDrawable(
                    if (m.role == "user") R.color.bridgefs_selected_surface else R.color.bridgefs_input_surface,
                    14
                )
            }
            val bubble = TextView(this).apply {
                text = m.content
                textSize = 14f
                setTextColor(color(R.color.bridgefs_text_primary))
                setPadding(dp(8), dp(6), dp(8), dp(6))
                setTextIsSelectable(true)
            }
            val copy = textButton("复制") {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("A-BridgeFS 消息", m.content))
                Toast.makeText(this@V021Activity, "消息已复制", Toast.LENGTH_SHORT).show()
            }
            bubbleBox.addView(bubble)
            bubbleBox.addView(copy, LinearLayout.LayoutParams(dp(58), dp(30)).apply {
                gravity = if (m.role == "user") Gravity.RIGHT else Gravity.LEFT
            })
            val row = FrameLayout(this)
            row.addView(bubbleBox, FrameLayout.LayoutParams(-2, -2).apply {
                gravity = if (m.role == "user") Gravity.RIGHT else Gravity.LEFT
                leftMargin = dp(4); rightMargin = dp(4)
            })
            messageBox.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(4); bottomMargin = dp(4)
            })
        }
        messages.addView(messageBox)
        root.addView(messages, LinearLayout.LayoutParams(-1, 0, 1f))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, dp(6), 0, 0)
        }
        val input = EditText(this).apply {
            hint = "输入消息……"
            textSize = 14f
            minLines = 1
            maxLines = 4
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = colorDrawable(R.color.bridgefs_input_surface, 14)
            setTextColor(color(R.color.bridgefs_text_primary))
            setHintTextColor(color(R.color.bridgefs_text_secondary))
        }
        composer.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
        composer.addView(actionButton("回执") {
            val receipt = pendingReceipt
            if (receipt.isNullOrBlank()) {
                Toast.makeText(this, "当前没有待处理回执", Toast.LENGTH_SHORT).show()
            } else {
                input.setText(receipt)
                input.setSelection(input.text.length)
                pendingReceipt = null
            }
        }, LinearLayout.LayoutParams(dp(58), dp(52)).apply { marginStart = dp(6) })
        composer.addView(actionButton("发送") {
            send(input, apiId)
            input.text.clear()
        }, LinearLayout.LayoutParams(dp(70), dp(52)).apply { marginStart = dp(6) })
        root.addView(composer)
        content.addView(root)
    }

    private fun renderConfig() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(14))
        }
        root.addView(header("配置", "全局运行设置"))
        val items = listOf(
            "AI 与 API" to "API、模型与连接",
            "连接与访问" to "API / GitHub 全局访问",
            "执行与权限" to "执行范围与确认策略",
            "指令" to "指令协议与说明",
            "文件与目录" to "工作目录与文件",
            "通知" to "执行与回执通知",
            "外观" to "界面显示",
            "系统" to "后台与系统权限",
            "日志与诊断" to "运行日志"
        )
        items.forEach { (title,summary) ->
            root.addView(configCard(title,summary) {
                when(title) {
                    "AI 与 API" -> { page = Page.WORKSPACE; render() }
                    "连接与访问" -> startActivity(Intent(this, GlobalAccessActivity::class.java))
                    else -> startActivity(Intent(this, SettingsCategoryActivity::class.java).putExtra("category", title))
                }
            })
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        content.addView(scroll)
    }

    private fun header(title:String, subtitle:String): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(2), 0, dp(12)) }
        box.addView(TextView(this).apply { text=title; textSize=25f; typeface=Typeface.DEFAULT_BOLD })
        box.addView(TextView(this).apply {
            text=subtitle; textSize=13f; setTextColor(color(R.color.bridgefs_text_secondary)); setPadding(0,dp(4),0,0)
        })
        return box
    }

    private fun sectionTitle(text:String) = TextView(this).apply {
        this.text=text; textSize=14f; typeface=Typeface.DEFAULT_BOLD
        setTextColor(color(R.color.bridgefs_text_secondary))
        setPadding(dp(2), dp(12), dp(2), dp(6))
    }

    private fun configCard(title:String,summary:String,action:()->Unit) = LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL; setPadding(dp(14),dp(11),dp(14),dp(11))
        background=colorDrawable(R.color.bridgefs_input_surface,14); setOnClickListener{action()}
        addView(TextView(this@V021Activity).apply{text=title+"  ›";textSize=15f;typeface=Typeface.DEFAULT_BOLD})
        addView(TextView(this@V021Activity).apply{text=summary;textSize=12f;setTextColor(color(R.color.bridgefs_text_secondary));setPadding(0,dp(4),0,0)})
    }.also { it.layoutParams=LinearLayout.LayoutParams(-1,dp(70)).apply{bottomMargin=dp(9)} }

    private fun emptyCard(title:String,subtitle:String)=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(14),dp(14),dp(14))
        background=colorDrawable(R.color.bridgefs_input_surface,14)
        addView(TextView(this@V021Activity).apply{text=title;textSize=15f;typeface=Typeface.DEFAULT_BOLD})
        addView(TextView(this@V021Activity).apply{text=subtitle;textSize=12f;setTextColor(color(R.color.bridgefs_text_secondary));setPadding(0,dp(4),0,0)})
    }.also { it.layoutParams=LinearLayout.LayoutParams(-1,dp(76)) }

    private fun card()=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(12))
        background=colorDrawable(R.color.bridgefs_surface,14)
        elevation=dp(1).toFloat()
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)}
    }

    private fun actionButton(text:String,action:()->Unit)=TextView(this).apply {
        this.text=text;textSize=13f;gravity=Gravity.CENTER
        setTextColor(color(R.color.bridgefs_button_text));background=colorDrawable(R.color.bridgefs_button_bg,12)
        setOnClickListener{action()}
    }

    private fun textButton(text:String,action:()->Unit)=TextView(this).apply {
        this.text=text;textSize=12f;gravity=Gravity.CENTER
        setTextColor(color(R.color.bridgefs_accent));setOnClickListener{action()}
    }

    private fun editApi(old:ApiProfile?) {
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(4),0,dp(4),0)}
        val n=field("名称",old?.name);val u=field("API 地址",old?.baseUrl);val k=field("API Key",old?.key);val m=field("模型",old?.model)
        listOf(n,u,k,m).forEach{box.addView(it)}
        AlertDialog.Builder(this).setTitle(if(old==null)"添加 API" else "修改 API").setView(box)
            .setPositiveButton("保存"){_,_->saveApi(ApiProfile(old?.id?:UUID.randomUUID().toString(),n.text.toString().trim(),u.text.toString().trim(),k.text.toString(),m.text.toString().trim(),old?.write?:false))}
            .setNegativeButton("取消",null).show()
    }


    private fun removeApi(a:ApiProfile) {
        AlertDialog.Builder(this).setTitle("移除 API").setMessage("确定移除「"+a.name+"」？")
            .setPositiveButton("移除"){_,_->
                projects.forEach { if (it.apiId == a.id) it.apiId = null }
                apiSecrets.remove(a.id)
                saveApis(apis().filterNot{it.id==a.id})
            }
            .setNegativeButton("取消",null).show()
    }

    private fun send(input:EditText,id:String) {
        val text=input.text.toString().trim()
        if(text.isBlank()) return
        val current=project ?: return
        val selectedId=current.apiId ?: id
        val a=apis().firstOrNull{it.id==selectedId} ?: run {
            Toast.makeText(this,"请先选择 API",Toast.LENGTH_SHORT).show()
            return
        }
        if(!AccessPolicy.isApiEnabled(this)){
            Toast.makeText(this,"API 全局访问已关闭",Toast.LENGTH_SHORT).show()
            return
        }

        current.apiId=a.id
        apiId=a.id
        current.messages += BridgeChatMessage("user",text)
        store.save(projects)
        render()

        executor.execute {
            try {
                val limit=prefs.getInt("command_limit",3).coerceIn(1,20)
                val answer=BridgeApiClient(
                    BridgeApiConfig(normalizeBaseUrl(a.baseUrl),a.key,a.model)
                ).chat(current.messages,BridgeCommandSpec.aiSystemPrompt(limit))
                runOnUiThread {
                    current.messages += BridgeChatMessage("assistant",answer)
                    store.save(projects)
                    render()
                    executeAiCommands(answer,current,limit)
                }
            } catch(e:Exception) {
                runOnUiThread {
                    current.messages += BridgeChatMessage("tool","[API 错误]\n"+(e.message ?: "未知错误"))
                    store.save(projects)
                    render()
                }
            }
        }
    }

    private fun executeAiCommands(answer:String, current:BridgeProject, limit:Int) {
        val blocks=BridgeRequest.extractAll(answer)
        if(blocks.isEmpty()) {
            recordReceipt(current,"NOT_TRIGGERED","AI command","AI 回复未包含 [bridgefs]...[/bridgefs] 指令区块，本轮未执行本地操作。")
            return
        }

        val commands=blocks.flatMap { CommandParser.parse(it) }
        if(commands.isEmpty()) {
            recordReceipt(current,"FAILED","AI command",CommandParser.lastError ?: "未识别到 BridgeFS 指令")
            return
        }

        if(commands.size>limit) {
            recordReceipt(current,"DENIED","AI command batch","本轮指令数量 ${commands.size} 超过限制 ${limit}，未执行。")
            return
        }

        val auth=authorization()
        val denied=commands.firstOrNull { PermissionPolicy.check(it,auth)==Decision.DENY }
        if(denied!=null) {
            recordReceipt(current,"DENIED",denied.toString(),"当前权限设置禁止该操作")
            return
        }

        val confirm=commands.firstOrNull { PermissionPolicy.check(it,auth)==Decision.CONFIRM }
        if(confirm!=null) {
            AlertDialog.Builder(this)
                .setTitle("需要确认")
                .setMessage(blocks.joinToString("\n\n"))
                .setPositiveButton("执行") { _,_ -> dispatchToBridge(blocks.joinToString("\n\n"),current) }
                .setNegativeButton("拒绝") { _,_ -> recordReceipt(current,"DENIED",confirm.toString(),"用户拒绝了本次执行") }
                .show()
        } else {
            dispatchToBridge(blocks.joinToString("\n\n"),current)
        }
    }

    private fun dispatchToBridge(command:String,current:BridgeProject) {
        val root=prefs.getString("root_path","").orEmpty().trim()
        if(root.isBlank()) {
            recordReceipt(current,"FAILED","AI command","未设置 BridgeFS 工作目录，指令未执行。")
            return
        }

        val intent=Intent(this,FileBridgeService::class.java)
            .putExtra("bridgefs_external_command",command)
            .putExtra("bridgefs_root",root)
            .putExtra("projectId",current.id)

        runCatching { startForegroundService(intent) }.onFailure {
            recordReceipt(current,"FAILED","AI command","启动 BridgeFS 执行服务失败："+(it.message ?: "未知错误"))
        }
    }

    private fun authorization():Authorization {
        val allowed=mutableSetOf<FileAction>()
        val confirm=mutableSetOf<FileAction>()
        FileAction.values().forEach { action ->
            when(prefs.getString("perm_"+action.name,
                if(action==FileAction.LIST || action==FileAction.READ) "allow" else "confirm")) {
                "allow" -> allowed += action
                "confirm" -> { allowed += action; confirm += action }
            }
        }
        return Authorization(prefs.getString("root_path","").orEmpty(),allowed,confirm)
    }

    private fun recordReceipt(current:BridgeProject,status:String,command:String,message:String) {
        val receipt=BridgeReceiptRecord(status,command,message)
        current.executions += receipt
        pendingReceipt=formatReceipt(receipt)
        store.save(projects)
        if(page==Page.CHAT) render()
    }

    private fun formatReceipt(receipt:BridgeReceiptRecord):String =
        "[Receipt] ${receipt.status}\ncommand=${receipt.command}\n${receipt.message}"


    private fun field(h:String,v:String?)=EditText(this).apply{
        hint=h
        setText(v.orEmpty())
        textSize=14f
        setTextColor(color(R.color.bridgefs_text_primary))
        setHintTextColor(color(R.color.bridgefs_text_secondary))
    }
    private fun saveApi(a:ApiProfile){
        apiSecrets.put(a.id,a.key)
        saveApis(apis().filterNot{it.id==a.id}+a.copy(key=""))
    }
    private fun saveApis(list:List<ApiProfile>){
        prefs.edit().putString("api_profiles",JSONArray().apply{
            list.forEach{
                put(JSONObject()
                    .put("id",it.id)
                    .put("name",it.name)
                    .put("baseUrl",it.baseUrl)
                    .put("model",it.model)
                    .put("write",it.write))
            }
        }.toString()).apply()
        val current = project?.apiId
        if (current != null && list.none { it.id == current }) {
            project?.apiId = list.firstOrNull()?.id
        }
        apiId = project?.apiId ?: list.firstOrNull()?.id.orEmpty()
        store.save(projects)
        render()
    }
    private fun apis():List<ApiProfile>{
        val raw=prefs.getString("api_profiles",null)?:return legacyApi()
        val arr=JSONArray(raw)
        return List(arr.length()){i->
            val o=arr.getJSONObject(i)
            val id=o.getString("id")
            val legacyKey=o.optString("key","")
            if(legacyKey.isNotBlank()){
                apiSecrets.put(id,legacyKey)
            }
            ApiProfile(id,o.optString("name"),o.optString("baseUrl"),apiSecrets.get(id).orEmpty(),o.optString("model"),o.optBoolean("write",false))
        }
    }
    private fun legacyApi():List<ApiProfile>{
        val u=prefs.getString("api_base_url","").orEmpty();val m=prefs.getString("api_model","").orEmpty()
        if(u.isBlank()&&m.isBlank())return emptyList()
        val a=ApiProfile("legacy",prefs.getString("api_provider","API")?:"API",u,prefs.getString("api_key","").orEmpty(),m,false)
        apiSecrets.put(a.id,a.key)
        prefs.edit().putString("api_profiles",JSONArray().put(JSONObject().put("id",a.id).put("name",a.name).put("baseUrl",a.baseUrl).put("model",a.model).put("write",false)).toString()).apply()
        return listOf(a)
    }

    private fun normalizeBaseUrl(raw:String):String {
        var value=raw.trim().trimEnd('/')
        if(value.endsWith("/chat/completions")) value=value.removeSuffix("/chat/completions")
        if(value.endsWith("/models")) value=value.removeSuffix("/models")
        return value
    }

    private fun color(res:Int)=resources.getColor(res)
    private fun colorDrawable(res:Int,r:Int)=GradientDrawable().apply{setColor(color(res));cornerRadius=dp(r).toFloat()}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        executor.shutdownNow()
        super.onDestroy()
    }
}

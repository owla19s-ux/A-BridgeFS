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

class V021Activity : Activity() {
    private val prefs by lazy { getSharedPreferences("bridgefs", 0) }
    private val store by lazy { BridgeProjectStore(this) }
    private val apiProfiles by lazy { ApiProfileStore(this) }
    private var projects = mutableListOf<BridgeProject>()
    private var project: BridgeProject? = null
    private lateinit var content: FrameLayout
    private lateinit var navWorkspace: TextView
    private lateinit var navChat: TextView
    private lateinit var navConfig: TextView
    private var apiId = ""
    private enum class Page { WORKSPACE, CHAT, NEW_CHAT, CONFIG }
    private var page = Page.WORKSPACE
    private val executor = Executors.newSingleThreadExecutor()
    private var pendingReceipt: String? = null
    private val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            val status = intent.getStringExtra("status") ?: "UNKNOWN"
            val command = intent.getStringExtra("command") ?: ""
            val message = intent.getStringExtra("message") ?: ""
            val projectId = intent.getStringExtra("projectId")
            val conversationId = intent.getStringExtra("conversationId")
            val target = projects.firstOrNull { it.id == projectId } ?: project
            val conversation = target?.let { ws -> conversationId?.let { id -> ws.conversations.firstOrNull { it.id == id } } ?: ws.activeConversation() }
            if (conversation != null) {
                val receipt = BridgeReceiptRecord(status, command, message)
                conversation.executions += receipt
                conversation.messages += BridgeChatMessage("receipt", formatReceipt(receipt))
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
        val activeWorkspaceId = prefs.getString("active_workspace_id", null)
        project = projects.firstOrNull { it.id == activeWorkspaceId } ?: projects.first()
        prefs.edit().putString("active_workspace_id", project?.id).apply()
        apiId = project?.activeConversation()?.apiId ?: apis().firstOrNull()?.id.orEmpty()
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
            Page.NEW_CHAT -> renderNewChat()
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
        root.addView(workspaceSelectorCard())
        root.addView(workspaceCard())
        root.addView(collaborationCard())
        root.addView(localFilePermissionCard())
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

    private fun workspaceSelectorCard(): View {
        val box = card()
        val current = project ?: return box
        val workspaces = projects
        box.addView(TextView(this).apply {
            text = "当前工作区"
            textSize = 12f
            setTextColor(color(R.color.bridgefs_text_secondary))
        })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            text = current.name.ifBlank { "未命名工作区" }
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.bridgefs_text_primary))
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        row.addView(textButton("切换") {
            val labels = workspaces.map { it.name.ifBlank { "未命名工作区" } }.toTypedArray()
            val index = workspaces.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
            AlertDialog.Builder(this@V021Activity)
                .setTitle("切换工作区")
                .setSingleChoiceItems(labels, index) { dialog, which ->
                    project = workspaces[which]
                    prefs.edit().putString("active_workspace_id", project?.id).apply()
                    apiId = project?.activeConversation()?.apiId ?: apis().firstOrNull()?.id.orEmpty()
                    store.save(projects)
                    dialog.dismiss()
                    render()
                }
                .setNegativeButton("取消", null)
                .show()
        }, LinearLayout.LayoutParams(dp(64), dp(40)))
        row.addView(textButton("重命名") {
            val input = field("工作区名称", current.name)
            AlertDialog.Builder(this@V021Activity)
                .setTitle("重命名工作区")
                .setView(input)
                .setPositiveButton("保存") { _, _ ->
                    current.name = input.text.toString().trim().ifBlank { "未命名工作区" }
                    store.save(projects)
                    render()
                }
                .setNegativeButton("取消", null)
                .show()
        }, LinearLayout.LayoutParams(dp(76), dp(40)))
        box.addView(row)
        box.addView(actionButton("＋ 新建工作区") {
            val input = field("工作区名称", "新工作区 ${workspaces.size + 1}")
            AlertDialog.Builder(this@V021Activity)
                .setTitle("新建工作区")
                .setView(input)
                .setPositiveButton("创建") { _, _ ->
                    val name = input.text.toString().trim().ifBlank { "新工作区 ${projects.size + 1}" }
                    val created = store.newProject(name)
                    projects += created
                    project = created
                    prefs.edit().putString("active_workspace_id", created.id).apply()
                    apiId = created.activeConversation().apiId ?: apis().firstOrNull()?.id.orEmpty()
                    store.save(projects)
                    render()
                }
                .setNegativeButton("取消", null)
                .show()
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
        return box
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
            text = project?.github?.displayRepository() ?: "未选择 Repository"
            textSize = 14f
            setPadding(0, dp(4), 0, dp(2))
        })
        box.addView(TextView(this).apply {
            text = "Branch  ·  " + (project?.github?.displayBranch() ?: "未选择")
            textSize = 12f
            setTextColor(color(R.color.bridgefs_text_secondary))
            setPadding(0, dp(2), 0, dp(8))
        })
        box.addView(actionButton("进入 GitHub") {
            startActivity(Intent(this, GitHubActivity::class.java).putExtra("workspaceId", project?.id))
        })
        return box
    }

    private fun collaborationCard(): View {
        val box = card()
        val collaborationProfiles = collaborationProfileIds()
        val configured = collaborationProfiles.first.isNotBlank() && collaborationProfiles.second.isNotBlank()
        box.addView(TextView(this).apply { text = "AI 协作"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD })
        box.addView(TextView(this).apply {
            text = "两个 AI 共享工作区资源；API 是连接资源，不再代表固定 Decision / Worker 身份。"
            textSize = 13f
            setTextColor(color(R.color.bridgefs_text_secondary))
            setPadding(0, dp(4), 0, dp(8))
        })
        box.addView(TextView(this).apply {
            text = if (configured) {
                "参与 AI：" + (apis().firstOrNull { it.id == collaborationProfiles.first }?.name ?: "AI A") +
                    " ↔ " + (apis().firstOrNull { it.id == collaborationProfiles.second }?.name ?: "AI B")
            } else {
                "请先选择两个不同的 API Profile 作为本轮协作参与者"
            }
            textSize = 13f
            setTextColor(color(R.color.bridgefs_text_secondary))
            setPadding(0, dp(2), 0, dp(8))
        })
        box.addView(actionButton("选择两个协作 AI") { selectCollaborationProfiles() })
        box.addView(Switch(this).apply {
            text = "启用 AI 协作"
            textSize = 13f
            isChecked = prefs.getBoolean("collaboration_mode_enabled", false)
            isEnabled = configured
            setOnCheckedChangeListener { _, value -> prefs.edit().putBoolean("collaboration_mode_enabled", value).apply() }
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
        box.addView(TextView(this).apply {
            text = "API 仅表示连接资源；文件/GitHub 修改权限由工作区与对话权限控制。"
            textSize = 12f
            setTextColor(color(R.color.bridgefs_text_secondary))
            setPadding(0, dp(4), 0, 0)
        })
        return box
    }

    private fun localFilePermissionCard(): View {
        val box = card()
        val enabled = project?.localFileModifyEnabled ?: false
        box.addView(TextView(this).apply {
            text = "本地文件"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(CheckBox(this).apply {
            text = "允许当前工作区进行本地文件修改"
            isChecked = enabled
            setOnCheckedChangeListener { _, checked ->
                project?.localFileModifyEnabled = checked
                store.save(projects)
            }
        })
        box.addView(TextView(this).apply {
            text = if (enabled) "修改权限：已开启" else "修改权限：已关闭"
            textSize = 12f
            setTextColor(color(R.color.bridgefs_text_secondary))
        })
        return box
    }

    private fun renderChat() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
        }
        root.addView(header("对话", "与当前工作区中的 API 协作"))

        val workspace = project ?: return
        val conversation = workspace.activeConversation()
        val chatSelector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(8), dp(8))
            background = colorDrawable(R.color.bridgefs_input_surface, 14)
            setOnClickListener {
                val conversations = workspace.conversations
                if (conversations.isEmpty()) return@setOnClickListener
                val labels = conversations.map { it.name.ifBlank { "未命名对话" } }.toTypedArray()
                val currentIndex = conversations.indexOfFirst { it.id == workspace.activeConversationId }.coerceAtLeast(0)
                AlertDialog.Builder(this@V021Activity)
                    .setTitle("切换对话")
                    .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                        workspace.activeConversationId = conversations[which].id
                        apiId = workspace.activeConversation().apiId.orEmpty()
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
                text = conversation.name.ifBlank { "未命名对话" }
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
        val selected = apis.firstOrNull { it.id == (conversation.apiId ?: apiId) }
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
                val current = apis.indexOfFirst { it.id == (conversation.apiId ?: apiId) }.coerceAtLeast(0)
                AlertDialog.Builder(this@V021Activity)
                    .setTitle("选择对话 API")
                    .setSingleChoiceItems(labels, current) { dialog, which ->
                        apiId = apis[which].id
                        workspace.activeConversation().apiId = apiId
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
            page = Page.NEW_CHAT
            render()
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(4) })

        val messages = ScrollView(this)
        val messageBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        conversation.messages.forEach { m ->
            val bubbleBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(4), dp(4), dp(2))
                background = colorDrawable(
                    when (m.role) {
                        "user" -> R.color.bridgefs_selected_surface
                        "receipt" -> R.color.bridgefs_button_bg
                        else -> R.color.bridgefs_input_surface
                    },
                    14
                )
            }
            if (m.role == "assistant" && (!m.apiName.isNullOrBlank() || !m.apiAvatar.isNullOrBlank())) {
                val identity = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(this@V021Activity).apply {
                        text = m.apiAvatar?.ifBlank { "AI" } ?: "AI"
                        gravity = Gravity.CENTER
                        textSize = 11f
                        setTextColor(color(R.color.bridgefs_text_primary))
                        background = colorDrawable(R.color.bridgefs_selected_surface, 20)
                    }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(4) })
                    addView(TextView(this@V021Activity).apply {
                        text = m.apiName?.ifBlank { "AI" } ?: "AI"
                        textSize = 12f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(color(R.color.bridgefs_text_secondary))
                        setPadding(dp(7), 0, 0, 0)
                    })
                }
                bubbleBox.addView(identity)
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
            row.addView(bubbleBox, FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.82f).toInt(), -2
            ).apply {
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

    private fun renderNewChat() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(14)) }
        root.addView(header("新聊天", "创建一个独立对话，并选择初始 API"))
        val name = EditText(this).apply { hint = "对话名称"; textSize = 14f; setText("新聊天 " + (projects.size + 1)); setTextColor(color(R.color.bridgefs_text_primary)); setHintTextColor(color(R.color.bridgefs_text_secondary)) }
        root.addView(name, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(sectionTitle("初始 API"))
        val list = apis()
        var selectedId = list.firstOrNull()?.id.orEmpty()
        val apiLabel = TextView(this).apply {
            text = list.firstOrNull()?.name?.ifBlank { "未命名 API" } ?: "未选择 API"
            textSize = 15f; gravity = Gravity.CENTER_VERTICAL; setTextColor(color(R.color.bridgefs_text_primary));
            background = colorDrawable(R.color.bridgefs_input_surface, 14); setPadding(dp(14), 0, dp(14), 0)
        }
        apiLabel.setOnClickListener {
            if (list.isEmpty()) { Toast.makeText(this@V021Activity, "请先在工作区添加 API", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            AlertDialog.Builder(this@V021Activity).setTitle("选择初始 API")
                .setSingleChoiceItems(list.map { it.name.ifBlank { "未命名 API" } }.toTypedArray(), list.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)) { dialog, which ->
                    selectedId = list[which].id; apiLabel.text = list[which].name.ifBlank { "未命名 API" }; dialog.dismiss()
                }.setNegativeButton("取消", null).show()
        }
        root.addView(apiLabel, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(actionButton("创建并进入对话") {
            val workspace = project ?: run {
                Toast.makeText(this@V021Activity, "当前没有可用工作区", Toast.LENGTH_SHORT).show()
                return@actionButton
            }
            val chat = BridgeConversation(
                id = UUID.randomUUID().toString(),
                name = name.text.toString().trim().ifBlank { "新聊天 " + (workspace.conversations.size + 1) },
                apiId = selectedId.ifBlank { null }
            )
            workspace.conversations += chat
            workspace.activeConversationId = chat.id
            project = workspace
            apiId = chat.apiId.orEmpty()
            store.save(projects)
            page = Page.CHAT
            render()
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(14) })
        root.addView(textButton("取消") { page = Page.CHAT; render() }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
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
        val n=field("名称",old?.name)
        val av=field("头像（文字 / Emoji）",old?.avatar)
        val u=field("API 地址",old?.baseUrl)
        val k=field("API Key",old?.key)
        val m=field("模型",old?.model)
        listOf(n,av,u,k,m).forEach{box.addView(it)}
        AlertDialog.Builder(this).setTitle(if(old==null)"添加 API" else "修改 API").setView(box)
            .setPositiveButton("保存"){_,_->
                saveApi(ApiProfile(
                    old?.id?:UUID.randomUUID().toString(),
                    n.text.toString().trim(),
                    u.text.toString().trim(),
                    k.text.toString(),
                    m.text.toString().trim(),
                    av.text.toString().trim()
                ))
            }
            .setNegativeButton("取消",null).show()
    }

    private fun removeApi(a:ApiProfile) {
        AlertDialog.Builder(this).setTitle("移除 API").setMessage("确定移除「"+a.name+"」？")
            .setPositiveButton("移除"){_,_->
                projects.forEach { workspace ->
                    workspace.conversations.forEach { conversation ->
                        if (conversation.apiId == a.id) conversation.apiId = null
                    }
                    workspace.aiMembers.forEach { member ->
                        if (member.apiProfileId == a.id) member.apiProfileId = null
                    }
                }
                apiProfiles.remove(a.id)
                if (collaborationProfileIds().first.isBlank() || collaborationProfileIds().second.isBlank()) {
                    prefs.edit().putBoolean("collaboration_mode_enabled", false).apply()
                }
                syncSelectedApi()
                store.save(projects)
            }
            .setNegativeButton("取消",null).show()
    }

    private fun runCollaboration(current: BridgeProject, objective: String) {
        executor.execute {
            runCatching {
                val ids = collaborationProfileIds()
                require(ids.first.isNotBlank() && ids.second.isNotBlank()) { "请先选择两个协作 AI" }
                val conversation = current.activeConversation()
                val coordinator = CollaborationCoordinator(this, current.id, conversation.id, ids.first, ids.second)
                val messages = coordinator.runObjective(
                    objective = objective,
                    decisionSystemPrompt = "你是本轮协作的规划参与者。将用户目标转成一个合法的协作 TASK JSON。当前阶段只负责分析、拆解与提出任务，不执行本地文件或 GitHub 操作。",
                    workerSystemPrompt = "你是本轮协作的执行参与者。严格返回合法的协作协议 JSON。当前阶段只负责分析任务并返回执行结果或 DECISION_REQUEST，不直接修改 GitHub 或本地文件。"
                )
                val profileStore = ApiProfileStore(this)
                val profileByRole = mapOf(
                    CollaborationProtocol.Role.DECISION_AI to profileStore.find(ids.first),
                    CollaborationProtocol.Role.WORKER to profileStore.find(ids.second)
                )
                val collaborationMessages = messages.map { message ->
                    val profile = profileByRole[message.from]
                    BridgeChatMessage(
                        role = "assistant",
                        content = "[协作 ${message.type.name}] ${message.from.name} → ${message.to.name}\n${message.toJson()}",
                        apiId = profile?.id,
                        apiName = profile?.name?.ifBlank { "未命名 API" },
                        apiAvatar = profile?.avatar?.ifBlank { profile.name.trim().take(1).ifBlank { "AI" } }
                    )
                }
                runOnUiThread {
                    current.activeConversation().messages += collaborationMessages
                    store.save(projects)
                    render()
                }
            }.onFailure { e ->
                val reason = e.message ?: e::class.simpleName ?: "未知错误"
                AppLogger.log(this, AppLogger.Category.COLLABORATION, "ROUND_FAILED", reason)
                runOnUiThread { current.activeConversation().messages += BridgeChatMessage("tool", "[协作错误]\n" + reason); store.save(projects); render() }
            }
        }
    }

    private fun collaborationProfileIds(): Pair<String,String> {
        val members = project?.aiMembers.orEmpty()
        return (members.getOrNull(0)?.apiProfileId.orEmpty()) to
            (members.getOrNull(1)?.apiProfileId.orEmpty())
    }

    private fun selectCollaborationProfiles() {
        val list = apis()
        if (list.size < 2) {
            Toast.makeText(this, "至少需要两个 API Profile", Toast.LENGTH_SHORT).show()
            return
        }
        val current = collaborationProfileIds()
        var first = list.indexOfFirst { it.id == current.first }.takeIf { it >= 0 } ?: 0
        var second = list.indexOfFirst { it.id == current.second }.takeIf { it >= 0 } ?: if (first == 0) 1 else 0
        val labels = list.map { it.name.ifBlank { "未命名 API" } }.toTypedArray()
        fun save() {
            project?.let { workspace ->
                if (workspace.aiMembers.size < 2) {
                    while (workspace.aiMembers.size < 2) {
                        workspace.aiMembers += BridgeAiMember(UUID.randomUUID().toString(), "AI " + ('A'.code + workspace.aiMembers.size).toChar())
                    }
                }
                workspace.aiMembers[0].apiProfileId = list[first].id
                workspace.aiMembers[1].apiProfileId = list[second].id
                store.save(projects)
            }
        }
        AlertDialog.Builder(this)
            .setTitle("先选择 AI A")
            .setSingleChoiceItems(labels, first) { dialog, which ->
                first = which
                if (second == first) second = (first + 1) % list.size
                save()
                dialog.dismiss()
                AlertDialog.Builder(this)
                    .setTitle("再选择 AI B")
                    .setSingleChoiceItems(labels, second) { dialog2, which2 ->
                        if (which2 == first) {
                            Toast.makeText(this, "AI A 与 AI B 必须使用不同的 API Profile", Toast.LENGTH_SHORT).show()
                        } else {
                            second = which2
                            save()
                            dialog2.dismiss()
                            render()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun send(input:EditText,id:String) {
        val text=input.text.toString().trim()
        if(text.isBlank()) return
        val current=project ?: return
        val conversation=current.activeConversation()
        val selectedId=conversation.apiId ?: id
        val a=apis().firstOrNull{it.id==selectedId} ?: run {
            Toast.makeText(this,"请先选择 API",Toast.LENGTH_SHORT).show()
            return
        }
        if(!AccessPolicy.isApiEnabled(this)){
            Toast.makeText(this,"API 全局访问已关闭",Toast.LENGTH_SHORT).show()
            return
        }

        conversation.apiId=a.id
        apiId=a.id
        conversation.messages += BridgeChatMessage("user",text)
        store.save(projects)
        render()

        if (prefs.getBoolean("collaboration_mode_enabled", false)) {
            val collaborationIds = collaborationProfileIds()
            if (collaborationIds.first.isBlank() || collaborationIds.second.isBlank() || collaborationIds.first == collaborationIds.second) {
                conversation.messages += BridgeChatMessage("tool", "[协作未启动]\n当前协作 API 尚未完成正式双 AI 配置。")
                store.save(projects); render(); return
            }
            runCollaboration(current, text)
            return
        }

        executor.execute {
            try {
                val limit=prefs.getInt("command_limit",3).coerceIn(1,20)
                val answer=BridgeApiClient(
                    BridgeApiConfig(normalizeBaseUrl(a.baseUrl),a.key,a.model)
                ).chat(conversation.messages,BridgeCommandSpec.aiSystemPrompt(limit))
                runOnUiThread {
                    conversation.messages += BridgeChatMessage("assistant", answer, apiId = a.id, apiName = a.name.ifBlank { "未命名 API" }, apiAvatar = a.avatar.ifBlank { a.name.trim().take(1).ifBlank { "AI" } })
                    store.save(projects)
                    render()
                    if (prefs.getBoolean("ai_auto_bridgefs_enabled", true)) {
                        executeAiCommands(answer,current,limit)
                    } else {
                        conversation.messages += BridgeChatMessage("tool", "[BridgeFS 未执行]\\nAI 自动执行已关闭，本次回复中的指令未触发本地执行。")
                        store.save(projects)
                        render()
                    }
                }
            } catch(e:Exception) {
                runOnUiThread {
                    conversation.messages += BridgeChatMessage("tool","[API 错误]\n"+(e.message ?: "未知错误"))
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
        val conversation = current.activeConversation()
        val root=prefs.getString("root_path","").orEmpty().trim()
        if(root.isBlank()) {
            recordReceipt(current,"FAILED","AI command","未设置 BridgeFS 工作目录，指令未执行。")
            return
        }

        val intent=Intent(this,FileBridgeService::class.java)
            .putExtra("bridgefs_external_command",command)
            .putExtra("bridgefs_root",root)
            .putExtra("projectId",current.id)
            .putExtra("conversationId",conversation.id)

        runCatching { startForegroundService(intent) }.onFailure {
            recordReceipt(current,"FAILED","AI command","启动 BridgeFS 执行服务失败："+(it.message ?: "未知错误"))
        }
    }

    private fun authorization():Authorization = PermissionPolicy.authorization(this, project, project?.activeConversation())

    private fun recordReceipt(current:BridgeProject,status:String,command:String,message:String) {
        val conversation = current.activeConversation()
        val receipt=BridgeReceiptRecord(status,command,message)
        conversation.executions += receipt
        conversation.messages += BridgeChatMessage("receipt", formatReceipt(receipt))
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
        apiProfiles.save(a)
        syncSelectedApi()
    }

    private fun apis():List<ApiProfile> = apiProfiles.list()

    private fun syncSelectedApi(){
        val list=apis()
        val current=project?.activeConversation()?.apiId
        if(current != null && list.none { it.id == current }){
            project?.activeConversation()?.apiId=list.firstOrNull()?.id
        }
        apiId=project?.activeConversation()?.apiId ?: list.firstOrNull()?.id.orEmpty()
        store.save(projects)
        render()
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
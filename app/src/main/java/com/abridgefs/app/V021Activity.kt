package com.abridgefs.app

import android.app.*
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.DocumentsContract
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
    companion object {
        private const val REQUEST_WORKSPACE_DIRECTORY = 2101
    }
    private val prefs by lazy { getSharedPreferences("bridgefs", 0) }
    private val store by lazy { BridgeProjectStore(this) }
    private val apiProfiles by lazy { ApiProfileStore(this) }
    private val conversationStore by lazy { BridgeConversationStore(this) }
    private var standaloneConversations = mutableListOf<BridgeConversation>()
    private var activeStandaloneConversationId: String? = null
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
    private var standaloneSendingConversationId: String? = null
    private var projectConstructionConversationId: String? = null
    private var projectConstructionIterations = 0
    private val maxProjectConstructionIterations = 8
    private val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            val status = intent.getStringExtra("status") ?: "UNKNOWN"
            val command = intent.getStringExtra("command") ?: ""
            val message = intent.getStringExtra("message") ?: ""
            val projectId = intent.getStringExtra("projectId")
            val conversationId = intent.getStringExtra("conversationId")
            val workspaceId = intent.getStringExtra("workspaceId")
            val standaloneConversationId = intent.getStringExtra("standaloneConversationId")
            val receipt = BridgeReceiptRecord(status, command, message, intent.getLongExtra("time", System.currentTimeMillis()), intent.getStringExtra("receiptId")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString())
            pendingReceipt = formatReceipt(receipt)

            if (!standaloneConversationId.isNullOrBlank()) {
                val conversation = standaloneConversations.firstOrNull { it.id == standaloneConversationId }
                if (conversation != null) {
                    if (!conversation.executions.any { it.receiptId == receipt.receiptId }) {
                        conversation.executions += receipt
                        conversation.messages += BridgeChatMessage("receipt", formatReceipt(receipt), receipt.time)
                    }
                    conversationStore.save(standaloneConversations)
                    removePendingReceipt(intent.getStringExtra("receiptId"))
                    if (page == Page.CHAT && activeStandaloneConversationId == standaloneConversationId) render()
                    else Toast.makeText(this@V021Activity, "收到执行回执：$status", Toast.LENGTH_SHORT).show()
                }
                return
            }

            val target = projects.firstOrNull { it.id == workspaceId }
                ?: projects.firstOrNull { it.id == projectId }
                ?: project
            val conversation = target?.let { ws ->
                conversationId?.let { id -> ws.conversations.firstOrNull { it.id == id } } ?: ws.activeConversation()
            }
            if (conversation != null) {
                if (!conversation.executions.any { it.receiptId == receipt.receiptId }) {
                    conversation.executions += receipt
                    conversation.messages += BridgeChatMessage("receipt", formatReceipt(receipt), receipt.time)
                }
                store.save(projects)
                removePendingReceipt(intent.getStringExtra("receiptId"))
                if (page == Page.WORKSPACE && target?.id == project?.id) render()
                else Toast.makeText(this@V021Activity, "收到执行回执：$status", Toast.LENGTH_SHORT).show()
                if (target != null && conversation != null && target.id == project?.id && conversation.id == projectConstructionConversationId && projectConstructionIterations < maxProjectConstructionIterations && status != "DENIED") {
                    continueProjectConstruction(target, conversation, receipt)
                }
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
        if (projects.isEmpty()) projects += store.newProject("默认项目")
        standaloneConversations = conversationStore.load()
        if (standaloneConversations.isEmpty()) {
            standaloneConversations += conversationStore.newConversation("默认对话", apis().firstOrNull()?.id)
            activeStandaloneConversationId = standaloneConversations.first().id
            conversationStore.save(standaloneConversations)
        } else {
            activeStandaloneConversationId = prefs.getString("active_standalone_conversation_id", null)
                ?.takeIf { id -> standaloneConversations.any { it.id == id } }
                ?: standaloneConversations.first().id
        }
        val activeProjectId = prefs.getString("active_project_id", null)
        project = projects.firstOrNull { it.id == activeProjectId } ?: projects.first()
        migrateLegacyWorkspaceDirectory()
        prefs.edit().putString("active_project_id", project?.id).apply()
        apiId = project?.activeConversation()?.apiId ?: apis().firstOrNull()?.id.orEmpty()
        registerReceiver(receiver, IntentFilter("com.bridgefs.RESULT"), Context.RECEIVER_NOT_EXPORTED)
        recoverPendingReceipt()
        buildShell()
    }

    /**
     * Recover a receipt written by FileBridgeService before this Activity was alive.
     * The receipt keeps its original workspace/standalone destination.
     */
    private fun recoverPendingReceipt() {
        val prefsStore = getSharedPreferences("bridgefs", Context.MODE_PRIVATE)
        val queued = org.json.JSONArray(prefsStore.getString("pending_receipts", "[]") ?: "[]")
        val legacy = prefsStore.getString("pending_receipt", "").orEmpty()
        val items = mutableListOf<org.json.JSONObject>()
        for (i in 0 until queued.length()) queued.optJSONObject(i)?.let { items += it }
        if (items.isEmpty() && legacy.isNotBlank()) runCatching { items += org.json.JSONObject(legacy) }
        if (items.isEmpty()) return
        val remaining = mutableListOf<org.json.JSONObject>()
        var recoveredAny = false
        for (obj in items) {
            runCatching {
                val status = obj.optString("status", "UNKNOWN")
                val command = obj.optString("command", "")
                val message = obj.optString("message", "")
                val projectId = obj.optString("projectId", "").ifBlank { null }
                val conversationId = obj.optString("conversationId", "").ifBlank { null }
                val workspaceId = obj.optString("workspaceId", "").ifBlank { null }
                val standaloneConversationId = obj.optString("standaloneConversationId", "").ifBlank { null }
                val receipt = BridgeReceiptRecord(status, command, message, obj.optLong("time", System.currentTimeMillis()), obj.optString("receiptId", "").ifBlank { UUID.randomUUID().toString() })
                if (!standaloneConversationId.isNullOrBlank()) {
                    val conversation = standaloneConversations.firstOrNull { it.id == standaloneConversationId }
                    if (conversation == null) { remaining += obj; return@runCatching }
                    if (!conversation.executions.any { it.receiptId == receipt.receiptId }) {
                        conversation.executions += receipt
                        conversation.messages += BridgeChatMessage("receipt", formatReceipt(receipt), receipt.time)
                    }
                    conversationStore.save(standaloneConversations)
                    pendingReceipt = formatReceipt(receipt)
                    recoveredAny = true
                } else {
                    val target = projects.firstOrNull { it.id == workspaceId }
                        ?: projects.firstOrNull { it.id == projectId }
                    val conversation = target?.let { ws -> conversationId?.let { id -> ws.conversations.firstOrNull { it.id == id } } }
                    if (conversation == null) { remaining += obj; return@runCatching }
                    if (!conversation.executions.any { it.receiptId == receipt.receiptId }) {
                        conversation.executions += receipt
                        conversation.messages += BridgeChatMessage("receipt", formatReceipt(receipt), receipt.time)
                    }
                    store.save(projects)
                    pendingReceipt = formatReceipt(receipt)
                    recoveredAny = true
                }
            }.onFailure {
                remaining += obj
                AppLogger.log(this, AppLogger.Category.EXECUTION, "PENDING_RECEIPT_RECOVERY_FAILED", it.message ?: "invalid pending receipt")
            }
        }
        prefsStore.edit().putString("pending_receipts", org.json.JSONArray().apply { remaining.forEach { put(it) } }.toString()).remove("pending_receipt").apply()
        if (!recoveredAny && remaining.isNotEmpty()) AppLogger.log(this, AppLogger.Category.EXECUTION, "PENDING_RECEIPT_RECOVERY_DEFERRED", "count=${remaining.size}")
    }

    private fun removePendingReceipt(receiptId: String?) {
        val id = receiptId?.trim().orEmpty()
        if (id.isBlank()) return
        val prefsStore = getSharedPreferences("bridgefs", Context.MODE_PRIVATE)
        val queued = org.json.JSONArray(prefsStore.getString("pending_receipts", "[]") ?: "[]")
        val remaining = org.json.JSONArray()
        for (i in 0 until queued.length()) {
            val item = queued.optJSONObject(i) ?: continue
            if (item.optString("receiptId", "") != id) remaining.put(item)
        }
        prefsStore.edit().putString("pending_receipts", remaining.toString()).remove("pending_receipt").apply()
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
            val chatPage = page == Page.CHAT || page == Page.WORKSPACE
            view.setPadding(0, 0, 0, if (chatPage) ime else 0)
            view.tag = system
            insets
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = colorDrawable(R.color.bridgefs_surface, 0)
        }
        navWorkspace = navItem("⌂\n项目", Page.WORKSPACE)
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
        val selectedPage = page
        listOf(
            navWorkspace to Page.WORKSPACE,
            navChat to Page.CHAT,
            navConfig to Page.CONFIG
        ).forEach { (v,p) ->
            v.setTextColor(if (p == selectedPage) color(R.color.bridgefs_accent) else color(R.color.bridgefs_text_secondary))
            v.background = if (p == selectedPage)
                colorDrawable(R.color.bridgefs_selected_surface, 14)
            else
                colorDrawable(R.color.bridgefs_surface, 14)
        }
    }

    private var projectConfigExpanded = false

    private fun renderWorkspace() {
        val current = project ?: return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
        }

        root.addView(header("项目", "当前项目的主要对话入口"))

        val projectBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(8), dp(8))
            background = colorDrawable(R.color.bridgefs_input_surface, 14)
        }
        projectBar.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@V021Activity).apply {
                text = "当前项目"
                textSize = 12f
                setTextColor(color(R.color.bridgefs_text_secondary))
            })
            addView(TextView(this@V021Activity).apply {
                text = current.name.ifBlank { "未命名项目" }
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color(R.color.bridgefs_text_primary))
                setPadding(0, dp(3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        projectBar.addView(textButton("切换") {
            val labels = projects.map { it.name.ifBlank { "未命名项目" } }.toTypedArray()
            val index = projects.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
            AlertDialog.Builder(this@V021Activity)
                .setTitle("切换项目")
                .setSingleChoiceItems(labels, index) { dialog, which ->
                    project = projects[which]
                    prefs.edit().putString("active_project_id", project?.id).apply()
                    apiId = project?.activeConversation()?.apiId ?: apis().firstOrNull()?.id.orEmpty()
                    dialog.dismiss()
                    render()
                }
                .setNegativeButton("取消", null)
                .show()
        }, LinearLayout.LayoutParams(dp(64), dp(40)))
        root.addView(projectBar, LinearLayout.LayoutParams(-1, dp(66)).apply { bottomMargin = dp(6) })

        val displayBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        displayBar.addView(TextView(this).apply {
            text = "屏幕显示"
            textSize = 12f
            setTextColor(color(R.color.bridgefs_text_secondary))
        }, LinearLayout.LayoutParams(0, dp(36), 1f))

        val conversation = current.activeConversation()
        val messages = ScrollView(this)
        val messageBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(8))
        }
        conversation.messages.forEach { m -> messageBox.addView(messageBubble(m)) }
        messages.addView(messageBox)
        root.addView(messages, LinearLayout.LayoutParams(-1, dp(360)))
        messages.post { messages.fullScroll(View.FOCUS_DOWN) }

        root.addView(requestAssistanceCard())

        val configHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(8), dp(8))
            background = colorDrawable(R.color.bridgefs_input_surface, 14)
            setOnClickListener {
                projectConfigExpanded = !projectConfigExpanded
                render()
            }
        }
        configHeader.addView(TextView(this).apply {
            text = "项目配置"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        configHeader.addView(TextView(this).apply {
            text = if (projectConfigExpanded) "收起" else "展开"
            textSize = 13f
            setTextColor(color(R.color.bridgefs_accent))
        }, LinearLayout.LayoutParams(dp(56), dp(46)))
        root.addView(configHeader, LinearLayout.LayoutParams(-1, dp(62)).apply { topMargin = dp(6) })

        if (projectConfigExpanded) {
            root.addView(projectSelectorCard())
            root.addView(projectGithubCard())
            root.addView(projectLocalAddressCard())
            root.addView(localFilePermissionCard())
            root.addView(projectMembersCard())
        }

        val projectScroll = ScrollView(this).apply {
            addView(root)
            isFillViewport = true
        }
        displayBar.addView(textButton("↑") {
            projectScroll.smoothScrollTo(0, 0)
        }, LinearLayout.LayoutParams(dp(44), dp(36)))
        displayBar.addView(textButton("↓") {
            projectScroll.post { smoothScrollTo(0, getChildAt(0).measuredHeight) }
        }, LinearLayout.LayoutParams(dp(44), dp(36)))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, dp(6), 0, 0)
        }
        val input = EditText(this).apply {
            hint = "输入问题或工作目标……"
            textSize = 14f
            minLines = 1
            maxLines = 4
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = colorDrawable(R.color.bridgefs_input_surface, 14)
            setTextColor(color(R.color.bridgefs_text_primary))
            setHintTextColor(color(R.color.bridgefs_text_secondary))
        }
        composer.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
        composer.addView(actionButton("发送") { sendProjectMessage(input, conversation) },
            LinearLayout.LayoutParams(dp(82), dp(52)).apply { marginStart = dp(6) })

        val pageArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        pageArea.addView(displayBar)
        pageArea.addView(projectScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(pageArea, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(composer, LinearLayout.LayoutParams(-1, dp(58)))
    }

    private fun projectSelectorCard(): View {
        val box = card()
        val current = project ?: return box
        val projectList = projects
        box.addView(TextView(this).apply {
            text = "当前项目"
            textSize = 12f
            setTextColor(color(R.color.bridgefs_text_secondary))
        })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            text = current.name.ifBlank { "未命名项目" }
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.bridgefs_text_primary))
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        row.addView(textButton("切换") {
            val labels = projectList.map { it.name.ifBlank { "未命名项目" } }.toTypedArray()
            val index = projectList.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
            AlertDialog.Builder(this@V021Activity)
                .setTitle("切换项目")
                .setSingleChoiceItems(labels, index) { dialog, which ->
                    project = projectList[which]
                    prefs.edit().putString("active_project_id", project?.id).apply()
                    apiId = project?.activeConversation()?.apiId ?: apis().firstOrNull()?.id.orEmpty()
                    store.save(projects)
                    dialog.dismiss()
                    render()
                }
                .setNegativeButton("取消", null)
                .show()
        }, LinearLayout.LayoutParams(dp(64), dp(40)))
        row.addView(textButton("重命名") {
            val input = field("项目名称", current.name)
            AlertDialog.Builder(this@V021Activity)
                .setTitle("重命名项目")
                .setView(input)
                .setPositiveButton("保存") { _, _ ->
                    current.name = input.text.toString().trim().ifBlank { "未命名项目" }
                    store.save(projects)
                    render()
                }
                .setNegativeButton("取消", null)
                .show()
        }, LinearLayout.LayoutParams(dp(76), dp(40)))
        box.addView(row)
        box.addView(actionButton("＋ 新建项目") {
            val input = field("项目名称", "新项目 ${projects.size + 1}")
            AlertDialog.Builder(this@V021Activity)
                .setTitle("新建项目")
                .setView(input)
                .setPositiveButton("创建") { _, _ ->
                    val name = input.text.toString().trim().ifBlank { "新项目 ${projects.size + 1}" }
                    val created = store.newProject(name)
                    projects += created
                    project = created
                    prefs.edit().putString("active_project_id", created.id).apply()
                    apiId = created.activeConversation().apiId ?: apis().firstOrNull()?.id.orEmpty()
                    store.save(projects)
                    render()
                }
                .setNegativeButton("取消", null)
                .show()
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
        return box
    }

    private fun projectGithubCard(): View {
        val box = card()
        val auth = GitHubTokenStore(this).state()
        val connected = AccessPolicy.isGithubEnabled(this) && !auth.accessToken.isNullOrBlank()
        box.addView(TextView(this).apply {
            text = project?.name ?: "默认项目"
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
        box.addView(actionButton("配置 GitHub Project Address") {
            startActivity(Intent(this, GitHubActivity::class.java).putExtra("workspaceId", project?.id))
        })
        return box
    }







    private fun projectMembersCard(): View {
        val box = card()
        val current = project ?: return box
        box.addView(TextView(this).apply {
            text = "Project Members"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(this).apply {
            val defaultName = current.defaultMemberId
                ?.let { id -> current.aiMembers.firstOrNull { it.id == id }?.name }
                ?.ifBlank { null }
            text = if (defaultName != null) "默认 AI：$defaultName" else "默认 AI：未配置"
            textSize = 12f
            setTextColor(color(R.color.bridgefs_text_secondary))
            setPadding(0, dp(4), 0, dp(8))
        })
        if (current.aiMembers.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "当前项目尚未配置 AI Member。"
                textSize = 12f
                setTextColor(color(R.color.bridgefs_text_secondary))
            })
        } else {
            current.aiMembers.forEach { member ->
                val profile = member.apiProfileId?.let { id -> apis().firstOrNull { it.id == id } }
                box.addView(TextView(this).apply {
                    text = member.name + "  ·  " + (profile?.name ?: "未绑定 API Profile")
                    textSize = 13f
                    setPadding(0, dp(5), 0, dp(5))
                })
            }
        }
        box.addView(actionButton("管理成员") {
            manageProjectMembers()
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
        return box
    }

    private fun manageProjectMembers() {
        val current = project ?: return
        val labels = current.aiMembers.map { member ->
            val profile = member.apiProfileId?.let { id -> apis().firstOrNull { it.id == id } }
            val marker = if (current.defaultMemberId == member.id) "（默认 AI）" else ""
            member.name.ifBlank { "未命名 AI" } + "  ·  " + (profile?.name ?: "未绑定 API") + " " + marker
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Project Members")
            .setItems(labels) { _, which -> editProjectMember(current.aiMembers[which]) }
            .setPositiveButton("新增成员") { _, _ -> editProjectMember(null) }
            .setNegativeButton("关闭", null).show()
    }

    private fun editProjectMember(member: BridgeAiMember?) {
        val current = project ?: return
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, dp(8), 0) }
        val name = field("成员名称", member?.name)
        val profiles = apis()
        val labels = mutableListOf("未绑定 API Profile")
        labels += profiles.map { it.name.ifBlank { "未命名 API" } }
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val selected = member?.apiProfileId?.let { id -> profiles.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1) } ?: 0
        spinner.setSelection(selected)
        box.addView(name)
        box.addView(TextView(this).apply { text = "API Profile"; textSize = 12f; setTextColor(color(R.color.bridgefs_text_secondary)); setPadding(0, dp(10), 0, dp(4)) })
        box.addView(spinner, LinearLayout.LayoutParams(-1, dp(48)))
        val default = CheckBox(this).apply { text = "设为当前 Project 的默认 AI"; isChecked = member?.id == current.defaultMemberId }
        box.addView(default)
        val dialog = AlertDialog.Builder(this).setTitle(if (member == null) "新增 Project Member" else "编辑 Project Member").setView(box)
            .setPositiveButton("保存", null).setNeutralButton("删除", null).setNegativeButton("取消", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val memberName = name.text.toString().trim()
                if (memberName.isBlank()) { name.error = "请输入成员名称"; return@setOnClickListener }
                val profileId = profiles.getOrNull(spinner.selectedItemPosition - 1)?.id
                val target = member ?: BridgeAiMember(UUID.randomUUID().toString(), memberName, profileId)
                if (member == null) current.aiMembers += target else { target.name = memberName; target.apiProfileId = profileId }
                if (default.isChecked || current.defaultMemberId == null) current.defaultMemberId = target.id
                else if (current.defaultMemberId == target.id) current.defaultMemberId = current.aiMembers.firstOrNull { it.id != target.id }?.id
                store.save(projects); dialog.dismiss(); render()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (member == null) { dialog.dismiss(); return@setOnClickListener }
                AlertDialog.Builder(this).setTitle("删除 Project Member").setMessage("确定删除「" + member.name + "」？")
                    .setPositiveButton("删除") { _, _ ->
                        current.aiMembers.removeAll { it.id == member.id }
                        if (current.defaultMemberId == member.id) current.defaultMemberId = current.aiMembers.firstOrNull()?.id
                        store.save(projects); dialog.dismiss(); render()
                    }.setNegativeButton("取消", null).show()
            }
        }
        dialog.show()
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
            text = "API 仅表示连接资源；文件/GitHub 修改权限由 Project 与对话权限控制。"
            textSize = 12f
            setTextColor(color(R.color.bridgefs_text_secondary))
            setPadding(0, dp(4), 0, 0)
        })
        return box
    }

    private fun apiSummaryCard(): View {
        val box = card()
        val list = apis()
        box.addView(TextView(this).apply {
            text = "AI 与 API"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(this).apply {
            text = if (list.isEmpty()) {
                "尚未配置 API。普通对话和 AI 协作都需要至少一个可用 API。"
            } else {
                "已配置 ${list.size} 个 API；Project Member 通过这里关联 API Profile。"
            }
            textSize = 12f
            setTextColor(color(R.color.bridgefs_text_secondary))
            setPadding(0, dp(4), 0, dp(8))
        })
        box.addView(actionButton("管理 API") {
            startActivity(Intent(this, ApiSettingsActivity::class.java))
        }, LinearLayout.LayoutParams(-1, dp(42)))
        return box
    }

    private fun projectLocalAddressCard(): View {
        val box = card()
        val rootPath = project?.workspaceDirectory.orEmpty().trim()

        box.addView(TextView(this).apply {
            text = "Local Project Address"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(this).apply {
            text = if (rootPath.isBlank()) "尚未设置。本地项目工作需要先指定地址。" else rootPath
            textSize = 13f
            setTextColor(
                if (rootPath.isBlank()) color(R.color.bridgefs_text_secondary)
                else color(R.color.bridgefs_text_primary)
            )
            setPadding(0, dp(6), 0, dp(10))
        })
        box.addView(actionButton(if (rootPath.isBlank()) "选择项目地址" else "更换项目地址") {
            openWorkspaceDirectoryPicker()
        }, LinearLayout.LayoutParams(-1, dp(44)))
        if (rootPath.isNotBlank()) {
            box.addView(TextView(this).apply {
                text = "AI 本地工作将使用当前 Project Address。"
                textSize = 12f
                setTextColor(color(R.color.bridgefs_text_secondary))
                setPadding(0, dp(7), 0, 0)
            })
        }
        return box
    }

    private fun localFilePermissionCard(): View {
        val box = card()
        val enabled = project?.localFileModifyEnabled ?: false

        box.addView(TextView(this).apply {
            text = "本地执行权限"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(this).apply {
            text = "控制当前 Project 的 AI 是否可以执行需要修改本地文件的指令。"
            textSize = 12f
            setTextColor(color(R.color.bridgefs_text_secondary))
            setPadding(0, dp(4), 0, dp(8))
        })
        box.addView(CheckBox(this).apply {
            text = "允许当前 Project 进行本地文件修改"
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
    private fun migrateLegacyWorkspaceDirectory() {
        val legacyRoot = prefs.getString("root_path", "").orEmpty().trim()
        if (legacyRoot.isBlank() || projects.any { !it.workspaceDirectory.isNullOrBlank() }) return
        val target = project ?: projects.firstOrNull() ?: return
        target.workspaceDirectory = legacyRoot
        store.save(projects)
    }

    private fun openWorkspaceDirectoryPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        startActivityForResult(intent, REQUEST_WORKSPACE_DIRECTORY)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_WORKSPACE_DIRECTORY || resultCode != RESULT_OK) return

        val uri = data?.data ?: return
        val path = documentTreeUriToPath(uri)
        if (path == null) {
            Toast.makeText(this, "暂时只支持设备主存储目录，请重新选择。", Toast.LENGTH_LONG).show()
            return
        }

        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }

        val workspace = project
        if (workspace == null) {
            Toast.makeText(this, "当前没有可用 Project。", Toast.LENGTH_SHORT).show()
            return
        }
        workspace.workspaceDirectory = path
        store.save(projects)
        Toast.makeText(this, "当前 Project Address 已设置：$path", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun documentTreeUriToPath(uri: Uri): String? {
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val separator = documentId.indexOf(':')
        if (separator <= 0) return null

        val volume = documentId.substring(0, separator)
        val relative = documentId.substring(separator + 1).trim('/')

        return when {
            volume.equals("primary", ignoreCase = true) -> {
                if (relative.isBlank()) "/storage/emulated/0"
                else "/storage/emulated/0/$relative"
            }
            else -> null
        }
    }

    private fun renderChat() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
        }
        root.addView(header("对话", "与一个指定 API 直接交流"))

        val conversation = standaloneConversations.firstOrNull { it.id == activeStandaloneConversationId }
            ?: standaloneConversations.firstOrNull()
            ?: run {
                val created = conversationStore.newConversation("默认对话", apis().firstOrNull()?.id)
                standaloneConversations += created
                activeStandaloneConversationId = created.id
                conversationStore.save(standaloneConversations)
                created
            }

        val chatSelector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(8), dp(8))
            background = colorDrawable(R.color.bridgefs_input_surface, 14)
            setOnClickListener {
                val labels = standaloneConversations.map { it.name.ifBlank { "未命名对话" } }.toTypedArray()
                val currentIndex = standaloneConversations.indexOfFirst { it.id == activeStandaloneConversationId }.coerceAtLeast(0)
                AlertDialog.Builder(this@V021Activity)
                    .setTitle("切换独立对话")
                    .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                        activeStandaloneConversationId = standaloneConversations[which].id
                        prefs.edit().putString("active_standalone_conversation_id", activeStandaloneConversationId).apply()
                        apiId = standaloneConversations[which].apiId.orEmpty()
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
                text = "当前独立对话"
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
        val selected = apis.firstOrNull { it.id == conversation.apiId }
        val selector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(8), dp(10))
            background = colorDrawable(R.color.bridgefs_input_surface, 14)
            setOnClickListener {
                if (apis.isEmpty()) {
                    Toast.makeText(this@V021Activity, "请先添加 API", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val labels = apis.map { it.name.ifBlank { "未命名 API" } }.toTypedArray()
                val current = apis.indexOfFirst { it.id == conversation.apiId }.coerceAtLeast(0)
                AlertDialog.Builder(this@V021Activity)
                    .setTitle("选择独立对话 API")
                    .setSingleChoiceItems(labels, current) { dialog, which ->
                        conversation.apiId = apis[which].id
                        apiId = conversation.apiId.orEmpty()
                        conversationStore.save(standaloneConversations)
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
        root.addView(selector, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(6) })

        root.addView(CheckBox(this).apply {
            text = "允许本独立对话修改本地文件"
            textSize = 13f
            isChecked = conversation.localFileModifyOverride == true
            setOnCheckedChangeListener { _, checked ->
                conversation.localFileModifyOverride = checked
                conversationStore.save(standaloneConversations)
            }
        }, LinearLayout.LayoutParams(-1, dp(42)))

        root.addView(textButton("＋ 新建独立对话") {
            page = Page.NEW_CHAT
            render()
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(4) })

        val messages = ScrollView(this)
        val messageBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        conversation.messages.forEach { m -> messageBox.addView(messageBubble(m)) }
        messages.addView(messageBox)
        root.addView(messages, LinearLayout.LayoutParams(-1, 0, 1f))
        messages.post { messages.fullScroll(View.FOCUS_DOWN) }

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
        val standaloneSendButton = actionButton(if (standaloneSendingConversationId == conversation.id) "发送中…" else "发送") {
            if (standaloneSendingConversationId == conversation.id) {
                Toast.makeText(this@V021Activity, "正在等待 API 回复，请稍候。", Toast.LENGTH_SHORT).show()
            } else {
                sendStandalone(input, conversation)
                input.text.clear()
            }
        }
        standaloneSendButton.isEnabled = standaloneSendingConversationId != conversation.id
        composer.addView(standaloneSendButton, LinearLayout.LayoutParams(dp(78), dp(52)).apply { marginStart = dp(6) })
        root.addView(composer)
        content.addView(root)
    }

    private fun messageBubble(m: BridgeChatMessage): View {
        val bubbleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(2))
            background = colorDrawable(
                when (m.role) {
                    "user" -> R.color.bridgefs_selected_surface
                    "receipt" -> R.color.bridgefs_button_bg
                    "tool" -> R.color.bridgefs_button_bg
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
        bubbleBox.addView(TextView(this).apply {
            text = m.content
            textSize = 14f
            setTextColor(color(R.color.bridgefs_text_primary))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setTextIsSelectable(true)
        })
        bubbleBox.addView(textButton("复制") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("A-BridgeFS 消息", m.content))
            Toast.makeText(this, "消息已复制", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(dp(58), dp(30)).apply {
            gravity = if (m.role == "user") Gravity.RIGHT else Gravity.LEFT
        })
        return FrameLayout(this).apply {
            addView(bubbleBox, FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.92f).toInt(), -2
            ).apply {
                gravity = if (m.role == "user") Gravity.RIGHT else Gravity.LEFT
                leftMargin = dp(4); rightMargin = dp(4)
            })
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(4); bottomMargin = dp(4)
            }
        }
    }

    private fun renderNewChat() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(14)) }
        root.addView(header("新聊天", "创建一个独立对话，不加入任何 Project 协作"))
        val name = EditText(this).apply {
            hint = "对话名称"
            textSize = 14f
            setText("新对话 " + (standaloneConversations.size + 1))
            setTextColor(color(R.color.bridgefs_text_primary))
            setHintTextColor(color(R.color.bridgefs_text_secondary))
        }
        root.addView(name, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(sectionTitle("初始 API"))
        val list = apis()
        var selectedId = list.firstOrNull()?.id.orEmpty()
        val apiLabel = TextView(this).apply {
            text = list.firstOrNull()?.name?.ifBlank { "未命名 API" } ?: "未选择 API"
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(color(R.color.bridgefs_text_primary))
            background = colorDrawable(R.color.bridgefs_input_surface, 14)
            setPadding(dp(14), 0, dp(14), 0)
        }
        apiLabel.setOnClickListener {
            if (list.isEmpty()) {
                Toast.makeText(this@V021Activity, "请先添加 API", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this@V021Activity)
                .setTitle("选择初始 API")
                .setSingleChoiceItems(
                    list.map { it.name.ifBlank { "未命名 API" } }.toTypedArray(),
                    list.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
                ) { dialog, which ->
                    selectedId = list[which].id
                    apiLabel.text = list[which].name.ifBlank { "未命名 API" }
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        root.addView(apiLabel, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(actionButton("创建并进入对话") {
            val chat = conversationStore.newConversation(
                name.text.toString().trim().ifBlank { "新对话 " + (standaloneConversations.size + 1) },
                selectedId.ifBlank { null }
            )
            standaloneConversations += chat
            activeStandaloneConversationId = chat.id
            apiId = chat.apiId.orEmpty()
            prefs.edit().putString("active_standalone_conversation_id", chat.id).apply()
            conversationStore.save(standaloneConversations)
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
                    "AI 与 API" -> startActivity(Intent(this, ApiSettingsActivity::class.java))
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
                standaloneConversations.forEach { conversation ->
                    if (conversation.apiId == a.id) conversation.apiId = null
                }
                apiProfiles.remove(a.id)
                syncSelectedApi()
                store.save(projects)
                conversationStore.save(standaloneConversations)
            }
            .setNegativeButton("取消",null).show()
    }







    private fun requestAssistanceCard(): View {
        val box = card()
        box.addView(TextView(this).apply {
            text = "请求 AI 协助"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(this).apply {
            text = "默认 AI 负责当前 Project 工作，需要其他 AI 时再主动请求协助。"
            textSize = 13f
            setTextColor(color(R.color.bridgefs_text_secondary))
            setPadding(0, dp(4), 0, dp(8))
        })
        box.addView(actionButton("请求 AI 协助") {
            Toast.makeText(this, "AI 协助入口已保留，协助流程正在迁移到 Project 模型。", Toast.LENGTH_SHORT).show()
        })
        return box
    }

    private fun sendProjectMessage(input: EditText, conversation: BridgeConversation) {
        val text = input.text.toString().trim()
        if (text.isBlank()) return
        val current = project ?: return
        if (!AccessPolicy.isApiEnabled(this)) {
            Toast.makeText(this, "API 全局访问已关闭", Toast.LENGTH_SHORT).show()
            return
        }
        val member = current.defaultMemberId?.let { id -> current.aiMembers.firstOrNull { it.id == id } }
            ?: current.aiMembers.firstOrNull()
        val profileId = member?.apiProfileId
        val profile = profileId?.let { id -> apis().firstOrNull { it.id == id } }
        if (profile == null) {
            Toast.makeText(this, "当前 Project 尚未配置默认 AI", Toast.LENGTH_SHORT).show()
            return
        }

        conversation.messages += BridgeChatMessage("user", text)
        store.save(projects)
        render()
        executor.execute {
            try {
                val address = current.workspaceDirectory?.trim().orEmpty()
                val githubRepository = current.github.repository?.trim().orEmpty()
                val branch = current.github.branch?.trim().orEmpty()
                val projectInfo = buildString {
                    append("当前 Project：").append(current.name)
                    if (address.isNotBlank()) append("\nLocal Project Address：").append(address)
                    if (githubRepository.isNotBlank()) {
                        append("\nGitHub Repository：").append(githubRepository)
                        if (branch.isNotBlank()) append("\nGitHub Branch：").append(branch)
                    }
                }
                val githubRead = GitHubConversationReader(this).readForProject(current, text)
                if (githubRead.error != null) {
                    runOnUiThread {
                        conversation.messages += BridgeChatMessage("tool", "[GitHub 错误]\n" + githubRead.error)
                        store.save(projects)
                        render()
                    }
                    return@execute
                }
                val githubPrompt = if (githubRead.content.isNotBlank()) {
                    "\n\n[Project GitHub 只读资料]\nRepository: " + githubRead.repository +
                        "\nBranch: " + (githubRead.branch ?: "默认分支") +
                        "\n以下内容来自当前 Project Address，仅用于本轮回答；不要执行任何修改操作。\n\n" + githubRead.content
                } else ""
                val answer = BridgeApiClient(
                    BridgeApiConfig(normalizeBaseUrl(profile.baseUrl), profile.key, profile.model)
                ).chat(
                    conversation.messages,
                    BridgeCommandSpec.aiSystemPrompt(prefs.getInt("command_limit", 3).coerceIn(1, 20)) +
                        "\n\n[Project 信息]\n" + projectInfo +
                        githubPrompt +
                        "\n你是当前 Project 的默认 AI。先直接回答用户问题；只有用户明确要求执行工作时，才进入后续工作流程。不要自动启动其他 AI 协作，也不要恢复已经废弃的固定阶段角色模型。"
                )
                runOnUiThread {
                    conversation.messages += BridgeChatMessage(
                        "assistant",
                        answer,
                        apiId = profile.id,
                        apiName = profile.name.ifBlank { "默认 AI" },
                        apiAvatar = profile.avatar.ifBlank { profile.name.trim().take(1).ifBlank { "AI" } }
                    )
                    store.save(projects)
                    render()
                    if (!executeProjectGitHubCommands(answer, current, conversation)) {
                        executeProjectCommands(answer, current, conversation)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    conversation.messages += BridgeChatMessage("tool", "[API 错误]\n" + (e.message ?: "未知错误"))
                    store.save(projects)
                    render()
                }
            }
        }
        input.text.clear()
    }


    private fun executeProjectCommands(answer: String, project: BridgeProject, conversation: BridgeConversation) {
        val blocks = BridgeRequest.extractAll(answer)
        if (blocks.isEmpty()) return
        val commands = blocks.flatMap { CommandParser.parse(it) }
        if (commands.isEmpty()) {
            addProjectReceipt(conversation, "FAILED", "AI command", CommandParser.lastError ?: "未识别到 BridgeFS 指令")
            return
        }
        val limit = prefs.getInt("command_limit", 3).coerceIn(1, 20)
        if (commands.size > limit) {
            addProjectReceipt(conversation, "DENIED", "AI command batch", "本轮指令数量 " + commands.size + " 超过限制 " + limit + "，未执行。")
            return
        }
        val auth = PermissionPolicy.authorization(this, project, conversation)
        val denied = commands.firstOrNull { PermissionPolicy.check(it, auth) == Decision.DENY }
        if (denied != null) {
            addProjectReceipt(conversation, "DENIED", denied.toString(), "当前 Project 权限设置禁止该操作")
            return
        }
        val confirm = commands.firstOrNull { PermissionPolicy.check(it, auth) == Decision.CONFIRM }
        if (confirm != null) {
            AlertDialog.Builder(this)
                .setTitle("需要确认")
                .setMessage(blocks.joinToString("\n\n"))
                .setPositiveButton("执行") { _, _ ->
                    dispatchProjectCommand(blocks.joinToString("\n\n"), project, conversation)
                }
                .setNegativeButton("拒绝") { _, _ ->
                    addProjectReceipt(conversation, "DENIED", confirm.toString(), "用户拒绝了本次执行")
                }
                .show()
        } else {
            dispatchProjectCommand(blocks.joinToString("\n\n"), project, conversation)
        }
    }

    private fun executeProjectGitHubCommands(answer: String, project: BridgeProject, conversation: BridgeConversation): Boolean {
        val blocks = GitHubRequest.extractAll(answer)
        if (blocks.isEmpty()) return false
        val commands = blocks.flatMap { GitHubRequest.parse(it) }
        if (commands.isEmpty()) { addProjectReceipt(conversation, "FAILED", "GitHub command", GitHubRequest.lastError ?: "未识别 GitHub 指令"); return true }
        val limit = prefs.getInt("command_limit", 3).coerceIn(1, 20)
        if (commands.size > limit) { addProjectReceipt(conversation, "DENIED", "GitHub command batch", "本轮 GitHub 指令数量超过限制 " + limit); return true }
        val member = project.defaultMemberId?.let { id -> project.aiMembers.firstOrNull { it.id == id } } ?: project.aiMembers.firstOrNull()
        val memberId = member?.id
        if (memberId.isNullOrBlank()) { addProjectReceipt(conversation, "DENIED", "GitHub construction", "当前 Project 没有可用的 Default AI Member"); return true }
        executor.execute {
            try {
                val lockStore = ConstructionLockStore(this)
                val lock = lockStore.acquire(project, memberId)
                val token = GitHubTokenStore(this).state().accessToken?.takeIf { it.isNotBlank() } ?: error("GitHub 尚未授权")
                val service = GitHubWorkspaceService(this, GitHubApiClient(this, token), project.github, project)
                var lastCommit = ""
                for (command in commands) {
                    when (command) {
                        is GitHubCommand.Write -> {
                            val existing = runCatching { service.file(command.path) }.getOrNull()
                            check(existing == null) { "目标文件已存在，请使用 edit：" + command.path }
                            val result = service.createFile(command.path, command.content, "AI: create " + command.path, memberId)
                            lastCommit = result.optJSONObject("commit")?.optString("sha").orEmpty()
                        }
                        is GitHubCommand.Edit -> {
                            val raw = service.file(command.path)
                            val sha = raw.optString("sha").takeIf { it.isNotBlank() } ?: error("无法取得文件 SHA：" + command.path)
                            val oldContent = service.readText(command.path)
                            check(oldContent.contains(command.old)) { "GitHub 文件未找到待替换内容：" + command.path }
                            val newContent = oldContent.replaceFirst(command.old, command.new)
                            val result = service.updateFile(command.path, newContent, "AI: edit " + command.path, sha, memberId)
                            lastCommit = result.optJSONObject("commit")?.optString("sha").orEmpty()
                        }
                    }
                }
                val verify = if (lastCommit.isNotBlank()) service.workflowRunsForCommit(lastCommit, 10) else JSONObject()
                val runs = verify.optJSONArray("workflow_runs")
                val summary = buildString {
                    append("GitHub 修改完成")
                    if (lastCommit.isNotBlank()) append("\nCommit: ").append(lastCommit)
                    if (runs != null) append("\nActions runs: ").append(runs.length())
                    append("\nConstructionLock: ").append(lock.holderAiMemberId)
                }
                                runOnUiThread {
                    addProjectReceipt(conversation, "SUCCEEDED", "GitHub construction", summary)
                    continueProjectConstruction(project, conversation, conversation.executions.last())
                }
            } catch (e: Exception) {
                runOnUiThread { addProjectReceipt(conversation, "FAILED", "GitHub construction", e.message ?: "GitHub 施工失败") }
            }
        }
        return true
    }
    private fun dispatchProjectCommand(command: String, project: BridgeProject, conversation: BridgeConversation) {
        val root = project.workspaceDirectory?.trim().orEmpty()
        if (root.isBlank()) {
            addProjectReceipt(conversation, "FAILED", "AI command", "当前 Project 未设置 Local Project Address，指令未执行。")
            return
        }
        if (projectConstructionConversationId != conversation.id) {
            projectConstructionConversationId = conversation.id
            projectConstructionIterations = 0
        }
        val intent = Intent(this, FileBridgeService::class.java)
            .putExtra("bridgefs_external_command", command)
            .putExtra("bridgefs_root", root)
            .putExtra("projectId", project.id)
            .putExtra("conversationId", conversation.id)
            .putExtra("workspaceId", project.id)
        runCatching { startForegroundService(intent) }.onFailure {
            addProjectReceipt(conversation, "FAILED", "AI command", "启动 BridgeFS 执行服务失败：" + (it.message ?: "未知错误"))
        }
    }

    private fun addProjectReceipt(conversation: BridgeConversation, status: String, command: String, message: String) {
        val receipt = BridgeReceiptRecord(status, command, message)
        conversation.executions += receipt
        conversation.messages += BridgeChatMessage("receipt", formatReceipt(receipt), receipt.time)
        store.save(projects)
        pendingReceipt = formatReceipt(receipt)
        if (page == Page.WORKSPACE) render()
    }


    private fun continueProjectConstruction(project: BridgeProject, conversation: BridgeConversation, receipt: BridgeReceiptRecord) {
        projectConstructionIterations += 1
        if (projectConstructionIterations > maxProjectConstructionIterations) {
            projectConstructionConversationId = null
            addProjectReceipt(conversation, "STOPPED", "construction", "已达到连续施工迭代上限 " + maxProjectConstructionIterations + "，等待用户继续。")
            return
        }
        val member = project.defaultMemberId?.let { id -> project.aiMembers.firstOrNull { it.id == id } }
            ?: project.aiMembers.firstOrNull()
        val profile = member?.apiProfileId?.let { id -> apis().firstOrNull { it.id == id } }
        if (profile == null) {
            projectConstructionConversationId = null
            return
        }
        executor.execute {
            try {
                val githubRead = GitHubConversationReader(this).readForProject(project, receipt.message)
                if (githubRead.error != null) throw IllegalStateException(githubRead.error)
                val githubPrompt = if (githubRead.content.isNotBlank()) {
                    "\n\n[Project GitHub 只读资料]\nRepository: " + githubRead.repository +
                        "\nBranch: " + (githubRead.branch ?: "默认分支") + "\n" + githubRead.content
                } else ""
                val answer = BridgeApiClient(
                    BridgeApiConfig(normalizeBaseUrl(profile.baseUrl), profile.key, profile.model)
                ).chat(
                    conversation.messages,
                    BridgeCommandSpec.aiSystemPrompt(prefs.getInt("command_limit", 3).coerceIn(1, 20)) +
                        "\n\n你正在继续当前 Project 的施工。上一轮执行回执如下：\n" +
                        formatReceipt(receipt) +
                        "\n如果工作已经完成，直接说明完成，不要输出 bridgefs/githubfs 指令；如果仍需修改本地文件，请输出下一轮完整的 [bridgefs]...[/bridgefs] 指令；如果需要修改 GitHub Project，请输出 [githubfs]...[/githubfs]。不要声称操作成功，必须依据回执判断。" +
                        githubPrompt
                )
                runOnUiThread {
                    conversation.messages += BridgeChatMessage(
                        "assistant", answer,
                        apiId = profile.id,
                        apiName = profile.name.ifBlank { "默认 AI" },
                        apiAvatar = profile.avatar.ifBlank { profile.name.trim().take(1).ifBlank { "AI" } }
                    )
                    store.save(projects)
                    render()
                    if (BridgeRequest.extractAll(answer).isEmpty()) {
                        projectConstructionConversationId = null
                        projectConstructionIterations = 0
                    } else {
                        executeProjectCommands(answer, project, conversation)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    projectConstructionConversationId = null
                    projectConstructionIterations = 0
                    conversation.messages += BridgeChatMessage("tool", "[施工继续失败]\n" + (e.message ?: "未知错误"))
                    store.save(projects)
                    render()
                }
            }
        }
    }

    private fun sendStandalone(input: EditText, conversation: BridgeConversation) {
        val text = input.text.toString().trim()
        if (text.isBlank()) return
        val selectedId = conversation.apiId ?: apiId
        val a = apis().firstOrNull { it.id == selectedId } ?: run {
            Toast.makeText(this, "请先选择 API", Toast.LENGTH_SHORT).show()
            return
        }
        if (!AccessPolicy.isApiEnabled(this)) {
            Toast.makeText(this, "API 全局访问已关闭", Toast.LENGTH_SHORT).show()
            return
        }

        conversation.apiId = a.id
        apiId = a.id
        conversation.messages += BridgeChatMessage("user", text)
        standaloneSendingConversationId = conversation.id
        conversationStore.save(standaloneConversations)
        render()

        executor.execute {
            try {
                val limit = prefs.getInt("command_limit", 3).coerceIn(1, 20)
                val github = GitHubConversationReader(this).readForConversation(text)
                if (github.error != null) {
                    runOnUiThread {
                        standaloneSendingConversationId = null
                        conversation.messages += BridgeChatMessage("tool", "[GitHub 错误]\\n" + github.error)
                        conversationStore.save(standaloneConversations)
                        render()
                    }
                    return@execute
                }
                val githubPrompt = if (github.content.isNotBlank()) {
                    "\n\n[GitHub 只读资料]\nRepository: ${github.repository}\nBranch: ${github.branch ?: "默认分支"}\n以下内容来自已连接的 GitHub Repository，仅用于本轮回答；不要执行任何修改操作。\n\n${github.content}"
                } else {
                    ""
                }
                val answer = BridgeApiClient(
                    BridgeApiConfig(normalizeBaseUrl(a.baseUrl), a.key, a.model)
                ).chat(
                    conversation.messages,
                    BridgeCommandSpec.aiSystemPrompt(limit) + githubPrompt
                )
                runOnUiThread {
                    conversation.messages += BridgeChatMessage(
                        "assistant",
                        answer,
                        apiId = a.id,
                        apiName = a.name.ifBlank { "未命名 API" },
                        apiAvatar = a.avatar.ifBlank { a.name.trim().take(1).ifBlank { "AI" } }
                    )
                    conversationStore.save(standaloneConversations)
                    standaloneSendingConversationId = null
                    render()
                    if (prefs.getBoolean("ai_auto_bridgefs_enabled", true)) {
                        executeAiCommands(answer, conversation, limit)
                    } else {
                        conversation.messages += BridgeChatMessage(
                            "tool",
                            "[BridgeFS 未执行]\nAI 自动执行已关闭，本次回复中的指令未触发本地执行。"
                        )
                        conversationStore.save(standaloneConversations)
                        render()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    standaloneSendingConversationId = null
                    conversation.messages += BridgeChatMessage("tool", "[API 错误]\n" + (e.message ?: "未知错误"))
                    conversationStore.save(standaloneConversations)
                    render()
                }
            }
        }
    }



    private fun executeAiCommands(answer: String, conversation: BridgeConversation, limit: Int) {
        val blocks = BridgeRequest.extractAll(answer)
        if (blocks.isEmpty()) {
            recordReceipt(conversation, "NOT_TRIGGERED", "AI command", "AI 回复未包含 [bridgefs]...[/bridgefs] 指令区块，本轮未执行本地操作。")
            return
        }

        val commands = blocks.flatMap { CommandParser.parse(it) }
        if (commands.isEmpty()) {
            recordReceipt(conversation, "FAILED", "AI command", CommandParser.lastError ?: "未识别到 BridgeFS 指令")
            return
        }

        if (commands.size > limit) {
            recordReceipt(conversation, "DENIED", "AI command batch", "本轮指令数量 " + commands.size + " 超过限制 " + limit + "，未执行。")
            return
        }

        val workspace = project
        val auth = PermissionPolicy.authorization(this, workspace, conversation)
        val denied = commands.firstOrNull { PermissionPolicy.check(it, auth) == Decision.DENY }
        if (denied != null) {
            recordReceipt(conversation, "DENIED", denied.toString(), "当前独立对话权限设置禁止该操作")
            return
        }

        val confirm = commands.firstOrNull { PermissionPolicy.check(it, auth) == Decision.CONFIRM }
        if (confirm != null) {
            AlertDialog.Builder(this)
                .setTitle("需要确认")
                .setMessage(blocks.joinToString("\n\n"))
                .setPositiveButton("执行") { _, _ ->
                    dispatchToBridge(blocks.joinToString("\n\n"), conversation)
                }
                .setNegativeButton("拒绝") { _, _ ->
                    recordReceipt(conversation, "DENIED", confirm.toString(), "用户拒绝了本次执行")
                }
                .show()
        } else {
            dispatchToBridge(blocks.joinToString("\n\n"), conversation)
        }
    }

    private fun dispatchToBridge(command: String, conversation: BridgeConversation) {
        val workspace = project
        val root = workspace?.workspaceDirectory.orEmpty().trim()
        if (root.isBlank()) {
            recordReceipt(conversation, "FAILED", "AI command", "当前 Project 未设置 Local Project Address，指令未执行。")
            return
        }

        val intent = Intent(this, FileBridgeService::class.java)
            .putExtra("bridgefs_external_command", command)
            .putExtra("bridgefs_root", root)
            .putExtra("projectId", null as String?)
            .putExtra("conversationId", null as String?)
            .putExtra("workspaceId", workspace?.id)
            .putExtra("standaloneConversationId", conversation.id)

        runCatching { startForegroundService(intent) }.onFailure {
            recordReceipt(conversation, "FAILED", "AI command", "启动 BridgeFS 执行服务失败：" + (it.message ?: "未知错误"))
        }
    }

    private fun recordReceipt(
        conversation: BridgeConversation,
        status: String,
        command: String,
        message: String
    ) {
        val receipt = BridgeReceiptRecord(status, command, message)
        conversation.executions += receipt
        conversation.messages += BridgeChatMessage("receipt", formatReceipt(receipt))
        pendingReceipt = formatReceipt(receipt)
        conversationStore.save(standaloneConversations)
        if (page == Page.CHAT) render()
    }

    private fun authorization(): Authorization =
        PermissionPolicy.authorization(this, null, standaloneConversations.firstOrNull { it.id == activeStandaloneConversationId })

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
        val list = apis()
        projects.forEach { workspace ->
            workspace.conversations.forEach { conversation ->
                if (conversation.apiId != null && list.none { it.id == conversation.apiId }) {
                    conversation.apiId = list.firstOrNull()?.id
                }
            }
        }
        standaloneConversations.forEach { conversation ->
            if (conversation.apiId != null && list.none { it.id == conversation.apiId }) {
                conversation.apiId = list.firstOrNull()?.id
            }
        }
        apiId = standaloneConversations.firstOrNull { it.id == activeStandaloneConversationId }?.apiId
            ?: list.firstOrNull()?.id.orEmpty()
        store.save(projects)
        conversationStore.save(standaloneConversations)
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
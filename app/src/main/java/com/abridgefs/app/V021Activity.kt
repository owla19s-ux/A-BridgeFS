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
    private enum class Page { WORKSPACE, CHAT, WORKSPACE_CHAT, NEW_CHAT, CONFIG }
    private var page = Page.WORKSPACE
    private val executor = Executors.newSingleThreadExecutor()
    private var pendingReceipt: String? = null
    private var collaborationRunningConversationId: String? = null
    private var standaloneSendingConversationId: String? = null
    private var collaborationTaskActionRunningId: String? = null
    private val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            val status = intent.getStringExtra("status") ?: "UNKNOWN"
            val command = intent.getStringExtra("command") ?: ""
            val message = intent.getStringExtra("message") ?: ""
            val projectId = intent.getStringExtra("projectId")
            val conversationId = intent.getStringExtra("conversationId")
            val workspaceId = intent.getStringExtra("workspaceId")
            val standaloneConversationId = intent.getStringExtra("standaloneConversationId")
            val receipt = BridgeReceiptRecord(status, command, message)
            pendingReceipt = formatReceipt(receipt)

            if (!standaloneConversationId.isNullOrBlank()) {
                val conversation = standaloneConversations.firstOrNull { it.id == standaloneConversationId }
                if (conversation != null) {
                    conversation.executions += receipt
                    conversation.messages += BridgeChatMessage("receipt", formatReceipt(receipt))
                    conversationStore.save(standaloneConversations)
                    removePendingReceipt(intent.getStringExtra("receiptId"))
                    if (page == Page.CHAT) render()
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
                conversation.executions += receipt
                conversation.messages += BridgeChatMessage("receipt", formatReceipt(receipt))
                store.save(projects)
                removePendingReceipt(intent.getStringExtra("receiptId"))
                if (page == Page.WORKSPACE_CHAT && target?.id == project?.id) render()
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
        val activeWorkspaceId = prefs.getString("active_workspace_id", null)
        project = projects.firstOrNull { it.id == activeWorkspaceId } ?: projects.first()
        migrateLegacyWorkspaceDirectory()
        prefs.edit().putString("active_workspace_id", project?.id).apply()
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
                val receipt = BridgeReceiptRecord(status, command, message, obj.optLong("time", System.currentTimeMillis()))
                if (!standaloneConversationId.isNullOrBlank()) {
                    val conversation = standaloneConversations.firstOrNull { it.id == standaloneConversationId }
                    if (conversation == null) { remaining += obj; return@runCatching }
                    conversation.executions += receipt
                    conversation.messages += BridgeChatMessage("receipt", formatReceipt(receipt))
                    conversationStore.save(standaloneConversations)
                    pendingReceipt = formatReceipt(receipt)
                    recoveredAny = true
                } else {
                    val target = projects.firstOrNull { it.id == workspaceId }
                        ?: projects.firstOrNull { it.id == projectId }
                    val conversation = target?.let { ws -> conversationId?.let { id -> ws.conversations.firstOrNull { it.id == id } } }
                    if (conversation == null) { remaining += obj; return@runCatching }
                    conversation.executions += receipt
                    conversation.messages += BridgeChatMessage("receipt", formatReceipt(receipt))
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
            val chatPage = page == Page.CHAT || page == Page.WORKSPACE_CHAT
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
            Page.WORKSPACE_CHAT -> renderWorkspaceChat()
            Page.NEW_CHAT -> renderNewChat()
            Page.CONFIG -> renderConfig()
        }
        updateNav()
    }

    private fun updateNav() {
        val selectedPage = if (page == Page.WORKSPACE_CHAT) Page.WORKSPACE else page
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

    private fun renderWorkspace() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(14))
        }

        root.addView(header("工作区", "管理协作资源与权限"))
        root.addView(workspaceSelectorCard())
        root.addView(workspaceCard())
        root.addView(collaborationCard())
        root.addView(collaborationTaskCard())
        root.addView(workspaceDirectoryCard())
        root.addView(localFilePermissionCard())
        root.addView(apiSummaryCard())

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
        box.addView(actionButton("进入协作对话") {
            if (!configured) {
                Toast.makeText(this, "请先选择两个协作 AI", Toast.LENGTH_SHORT).show()
            } else {
                page = Page.WORKSPACE_CHAT
                render()
            }
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(4) })
        return box
    }

    private fun collaborationTaskCard(): View {
        val box = card()
        val workspace = project
        val conversation = workspace?.activeConversation()
        val task = if (workspace != null && conversation != null) {
            CollaborationTaskStore(this).latest(workspace.id, conversation.id)
        } else {
            null
        }
        box.addView(TextView(this).apply {
            text = "协作任务"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })
        if (task == null) {
            box.addView(TextView(this).apply {
                text = "当前协作对话还没有任务。发送协作目标后，这里会显示分析、施工与 Verify 状态。"
                textSize = 13f
                setTextColor(color(R.color.bridgefs_text_secondary))
                setPadding(0, dp(5), 0, dp(8))
            })
            return box
        }

        val memberA = workspace?.aiMembers?.getOrNull(0)
        val memberB = workspace?.aiMembers?.getOrNull(1)
        val holderName = task.constructionHolderAiMemberId?.let { id ->
            workspace?.aiMembers?.firstOrNull { it.id == id }?.name
        }
        box.addView(TextView(this).apply {
            text = "状态：${collaborationTaskStatusLabel(task.status)}"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(5), 0, dp(2))
        })
        box.addView(TextView(this).apply {
            text = buildString {
                append("任务：")
                append(task.objective)
                if (!task.lastCommitSha.isNullOrBlank()) {
                    append("\nCommit：")
                    append(task.lastCommitSha)
                }
                if (!holderName.isNullOrBlank()) {
                    append("\n施工者：")
                    append(holderName)
                }
            }
            textSize = 12f
            setTextColor(color(R.color.bridgefs_text_secondary))
            setPadding(0, dp(2), 0, dp(8))
        })

        if (task.status == CollaborationTaskRecord.STATUS_WAITING_CONSTRUCTION && memberB != null) {
            box.addView(actionButton("Worker AI 申请施工锁") {
                runCatching {
                    val coordinator = CollaborationCoordinator(
                        this,
                        workspace!!.id,
                        conversation!!.id,
                        memberA?.apiProfileId.orEmpty(),
                        memberB.apiProfileId.orEmpty(),
                        memberB.id
                    )
                    coordinator.requestConstruction(task.taskId, memberB.id)
                    AppLogger.log(this, AppLogger.Category.COLLABORATION, "CONSTRUCTION_REQUESTED_UI", "taskId=${task.taskId}")
                    render()
                }.onFailure {
                    Toast.makeText(this, "申请施工失败：${it.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            })
        }

        if (task.status == CollaborationTaskRecord.STATUS_FAILED && !task.constructionHolderAiMemberId.isNullOrBlank()) {
            val actionRunning = collaborationTaskActionRunningId == task.taskId
            val repairButton = actionButton(if (actionRunning) "修复处理中…" else "根据 Verify 失败结果继续修复") {
                if (collaborationTaskActionRunningId == task.taskId) return@actionButton
                collaborationTaskActionRunningId = task.taskId
                render()
                executor.execute {
                    try {
                        val coordinator = CollaborationCoordinator(
                            this,
                            workspace!!.id,
                            conversation!!.id,
                            memberA?.apiProfileId.orEmpty(),
                            memberB?.apiProfileId.orEmpty(),
                            memberB?.id
                        )
                        val result = coordinator.retryAfterVerifyFailure(
                            task.taskId,
                            "你是 Decision AI。上一 Commit 的 GitHub Actions Verify 已失败。请分析失败结果并给出下一步修复指令，不得直接宣布完成。",
                            "你是 Worker AI。根据 Decision AI 的修复指令进行有限范围施工。"
                        )
                        runOnUiThread {
                            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                            collaborationTaskActionRunningId = null
                            render()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            collaborationTaskActionRunningId = null
                            Toast.makeText(this, "修复轮启动失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                            render()
                        }
                    }
                }
            }
            repairButton.isEnabled = !actionRunning
            box.addView(repairButton)
        }

        if (task.status == CollaborationTaskRecord.STATUS_WAITING_VERIFY ||
            task.status == CollaborationTaskRecord.STATUS_VERIFY_PASSED ||
            task.status == CollaborationTaskRecord.STATUS_CONSTRUCTION_WRITING
        ) {
            val actionRunning = collaborationTaskActionRunningId == task.taskId
            val verifyButton = actionButton(if (actionRunning) "Verify处理中…" else "检查当前 Commit") {
                if (collaborationTaskActionRunningId == task.taskId) return@actionButton
                collaborationTaskActionRunningId = task.taskId
                render()
                executor.execute {
                    try {
                        val coordinator = CollaborationCoordinator(
                            this,
                            workspace!!.id,
                            conversation!!.id,
                            memberA?.apiProfileId.orEmpty(),
                            memberB?.apiProfileId.orEmpty(),
                            memberB?.id
                        )
                        val result = coordinator.verifyAndContinue(
                            task.taskId,
                            "你是 Decision AI。只根据真实 Verify 结果决定是否完成任务或继续施工。",
                            "你是 Worker AI。根据 Decision AI 的施工指令执行有限范围内的下一步。"
                        )
                        runOnUiThread {
                            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                            collaborationTaskActionRunningId = null
                            render()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            collaborationTaskActionRunningId = null
                            Toast.makeText(this, "Verify 失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                            render()
                        }
                    }
                }
            }
            verifyButton.isEnabled = !actionRunning
            box.addView(verifyButton)
        }
        return box
    }

    private fun collaborationTaskStatusLabel(status: String): String = when (status) {
        CollaborationTaskRecord.STATUS_CREATED -> "已创建"
        CollaborationTaskRecord.STATUS_RUNNING -> "协作处理中"
        CollaborationTaskRecord.STATUS_WAITING_CONSTRUCTION -> "等待进入施工"
        CollaborationTaskRecord.STATUS_CONSTRUCTING -> "施工中"
        CollaborationTaskRecord.STATUS_WAITING_VERIFY -> "等待 Verify"
        CollaborationTaskRecord.STATUS_VERIFY_PASSED -> "Verify 已通过，等待继续"
        CollaborationTaskRecord.STATUS_CONSTRUCTION_WRITING -> "GitHub 写入恢复中"
        CollaborationTaskRecord.STATUS_COMPLETE -> "已完成"
        CollaborationTaskRecord.STATUS_FAILED -> "失败"
        CollaborationTaskRecord.STATUS_CANCELLED -> "已取消"
        else -> status
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
                "已配置 ${list.size} 个 API；当前工作区的协作 AI 从这里选择。"
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

    private fun workspaceDirectoryCard(): View {
        val box = card()
        val rootPath = project?.workspaceDirectory.orEmpty().trim()

        box.addView(TextView(this).apply {
            text = "工作目录"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(this).apply {
            text = if (rootPath.isBlank()) "尚未选择。AI 本地执行需要先指定一个工作目录。" else rootPath
            textSize = 13f
            setTextColor(
                if (rootPath.isBlank()) color(R.color.bridgefs_text_secondary)
                else color(R.color.bridgefs_text_primary)
            )
            setPadding(0, dp(6), 0, dp(10))
        })
        box.addView(actionButton(if (rootPath.isBlank()) "选择工作目录" else "更换工作目录") {
            openWorkspaceDirectoryPicker()
        }, LinearLayout.LayoutParams(-1, dp(44)))
        if (rootPath.isNotBlank()) {
            box.addView(TextView(this).apply {
                text = "AI 本地指令将使用当前工作区的这个目录。"
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
            text = "控制当前工作区的 AI 是否可以执行需要修改本地文件的指令。"
            textSize = 12f
            setTextColor(color(R.color.bridgefs_text_secondary))
            setPadding(0, dp(4), 0, dp(8))
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
            Toast.makeText(this, "当前没有可用工作区。", Toast.LENGTH_SHORT).show()
            return
        }
        workspace.workspaceDirectory = path
        store.save(projects)
        Toast.makeText(this, "当前工作区目录已设置：$path", Toast.LENGTH_SHORT).show()
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

    private fun renderWorkspaceChat() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
        }
        root.addView(textButton("← 返回工作区") { page = Page.WORKSPACE; render() },
            LinearLayout.LayoutParams(-1, dp(34)))
        root.addView(header("协作对话", "当前工作区内的双 AI 协作现场"))

        val workspace = project ?: return
        val conversation = workspace.activeConversation()

        val selector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(8), dp(8))
            background = colorDrawable(R.color.bridgefs_input_surface, 14)
            setOnClickListener {
                val labels = workspace.conversations.map { it.name.ifBlank { "未命名协作对话" } }.toTypedArray()
                val currentIndex = workspace.conversations.indexOfFirst { it.id == workspace.activeConversationId }.coerceAtLeast(0)
                AlertDialog.Builder(this@V021Activity)
                    .setTitle("切换协作对话")
                    .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                        workspace.activeConversationId = workspace.conversations[which].id
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
                text = "当前工作区协作对话"
                textSize = 12f
                setTextColor(color(R.color.bridgefs_text_secondary))
            })
            addView(TextView(this@V021Activity).apply {
                text = conversation.name.ifBlank { "未命名协作对话" }
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color(R.color.bridgefs_text_primary))
                setPadding(0, dp(3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        selector.addView(TextView(this).apply {
            text = "切换 ›"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(color(R.color.bridgefs_accent))
        }, LinearLayout.LayoutParams(dp(72), dp(44)))
        root.addView(selector, LinearLayout.LayoutParams(-1, dp(64)).apply { bottomMargin = dp(6) })

        val profiles = collaborationProfileIds()
        val collaborationRunning = collaborationRunningConversationId == conversation.id
        root.addView(TextView(this).apply {
            text = "协作 AI：" +
                (apis().firstOrNull { it.id == profiles.first }?.name ?: "AI A") +
                " ↔ " +
                (apis().firstOrNull { it.id == profiles.second }?.name ?: "AI B")
            textSize = 13f
            setTextColor(color(R.color.bridgefs_text_secondary))
            setPadding(dp(4), dp(2), dp(4), dp(2))
        })
        root.addView(TextView(this).apply {
            val directory = workspace.workspaceDirectory.orEmpty().ifBlank { "未设置" }
            text = "工作目录：$directory"
            textSize = 12f
            setTextColor(if (workspace.workspaceDirectory.isNullOrBlank()) color(R.color.bridgefs_text_secondary) else color(R.color.bridgefs_text_primary))
            setPadding(dp(4), 0, dp(4), dp(6))
        })

        root.addView(textButton("＋ 新建协作对话") {
            val input = field("协作对话名称", "协作对话 " + (workspace.conversations.size + 1))
            AlertDialog.Builder(this@V021Activity)
                .setTitle("新建协作对话")
                .setView(input)
                .setPositiveButton("创建") { _, _ ->
                    val created = BridgeConversation(
                        UUID.randomUUID().toString(),
                        input.text.toString().trim().ifBlank { "协作对话 " + (workspace.conversations.size + 1) }
                    )
                    workspace.conversations += created
                    workspace.activeConversationId = created.id
                    store.save(projects)
                    render()
                }
                .setNegativeButton("取消", null)
                .show()
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
            hint = "输入协作目标……"
            textSize = 14f
            minLines = 1
            maxLines = 4
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = colorDrawable(R.color.bridgefs_input_surface, 14)
            setTextColor(color(R.color.bridgefs_text_primary))
            setHintTextColor(color(R.color.bridgefs_text_secondary))
        }
        composer.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
        val sendButton = actionButton(if (collaborationRunning) "处理中…" else "发送") {
            if (collaborationRunningConversationId == conversation.id) {
                Toast.makeText(this@V021Activity, "本轮协作还在进行，请等待结果。", Toast.LENGTH_SHORT).show()
            } else {
                sendWorkspaceCollaboration(input, conversation)
                input.text.clear()
            }
        }
        sendButton.isEnabled = !collaborationRunning
        composer.addView(sendButton, LinearLayout.LayoutParams(dp(82), dp(52)).apply { marginStart = dp(6) })
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
        root.addView(header("新聊天", "创建一个独立对话，不加入任何工作区协作"))
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

    private fun runCollaboration(current: BridgeProject, objective: String) {
        val workspaceId = current.id
        val conversationId = current.activeConversation().id
        executor.execute {
            runCatching {
                val ids = collaborationProfileIds()
                require(ids.first.isNotBlank() && ids.second.isNotBlank()) { "请先选择两个协作 AI" }
                val workerMemberId = current.aiMembers.getOrNull(1)?.id
                val coordinator = CollaborationCoordinator(this, workspaceId, conversationId, ids.first, ids.second, workerMemberId)
                val messages = coordinator.runObjective(
                    objective = objective,
                    decisionSystemPrompt = "你是本轮协作的规划参与者。你必须只返回一个合法 JSON 对象，不要 Markdown、代码围栏或解释文字。协议版本必须为 0.1；from 只能是 decision_ai，to 只能是 worker；type 必须是 TASK。task_id 必须原样使用输入消息的 task_id。payload 必须包含 objective、scope、acceptance、autonomy、context_refs。当前阶段只负责分析、拆解与提出任务，不执行本地文件或 GitHub 操作。",
                    workerSystemPrompt = "你是本轮协作的执行参与者。你必须只返回一个合法 JSON 对象，不要 Markdown、代码围栏或解释文字。协议版本必须为 0.1；from 只能是 worker；对 Decision AI 的回复 to 必须是 decision_ai；type 只能使用 DECISION_REQUEST、PROGRESS、BLOCKED 或 FILE_CHANGE_REQUEST。COMMIT、VERIFY、COMPLETE 由 A-BridgeFS 根据真实施工与 Verify 状态产生。不要使用 executor、assistant、user 等角色名。需要实际修改文件时，必须返回 FILE_CHANGE_REQUEST，并在 payload 中提供 path、operation、content、commit_message；只能修改 TASK.scope 允许的路径和操作。不要自行调用 GitHub 或本地文件 API，实际写入由 A-BridgeFS 权限层执行。"
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
                        content = formatCollaborationMessage(message),
                        apiId = profile?.id,
                        apiName = profile?.name?.ifBlank { "未命名 API" },
                        apiAvatar = profile?.let { it.avatar.ifBlank { it.name.trim().take(1).ifBlank { "AI" } } }
                    )
                }
                runOnUiThread {
                    collaborationRunningConversationId = null
                    val targetWorkspace = projects.firstOrNull { it.id == workspaceId }
                    val target = targetWorkspace?.conversations?.firstOrNull { it.id == conversationId }
                    if (target == null) {
                        collaborationRunningConversationId = null
                        render()
                        return@runOnUiThread
                    }
                    val progressIndex = target.messages.indexOfLast { it.role == "tool" && it.content.startsWith("[协作进行中]") }
                    if (progressIndex >= 0) {
                        target.messages.removeAt(progressIndex)
                        target.messages.add(progressIndex, BridgeChatMessage("tool", "[协作完成]\nDecision AI 与 Worker 已完成本轮协议交互。"))
                    }
                    target.messages += collaborationMessages
                    store.save(projects)
                    render()
                }
            }.onFailure { e ->
                val reason = e.message ?: e::class.simpleName ?: "未知错误"
                AppLogger.log(this, AppLogger.Category.COLLABORATION, "ROUND_FAILED", reason)
                runOnUiThread {
                    collaborationRunningConversationId = null
                    val targetWorkspace = projects.firstOrNull { it.id == workspaceId }
                    val target = targetWorkspace?.conversations?.firstOrNull { it.id == conversationId }
                    if (target == null) {
                        collaborationRunningConversationId = null
                        render()
                        return@runOnUiThread
                    }
                    val progressIndex = target.messages.indexOfLast { it.role == "tool" && it.content.startsWith("[协作进行中]") }
                    if (progressIndex >= 0) {
                        target.messages.removeAt(progressIndex)
                        target.messages.add(progressIndex, BridgeChatMessage("tool", "[协作失败]\n" + reason))
                    } else {
                        target.messages += BridgeChatMessage("tool", "[协作失败]\n" + reason)
                    }
                    store.save(projects)
                    render()
                }
            }
        }
    }

    private fun formatCollaborationMessage(message: CollaborationProtocol.Message): String {
        val roleName = when (message.from) {
            CollaborationProtocol.Role.DECISION_AI -> "Decision AI"
            CollaborationProtocol.Role.WORKER -> "Worker"
            CollaborationProtocol.Role.HUMAN -> "用户"
        }
        val targetName = when (message.to) {
            CollaborationProtocol.Role.DECISION_AI -> "Decision AI"
            CollaborationProtocol.Role.WORKER -> "Worker"
            CollaborationProtocol.Role.HUMAN -> "用户"
        }
        val payload = message.payload
        val detail = when (message.type) {
            CollaborationProtocol.Type.TASK -> payload.optString("objective").ifBlank { "已生成协作任务" }
            CollaborationProtocol.Type.DECISION_REQUEST -> payload.optString("question").ifBlank { "Worker 请求 Decision AI 决策" }
            CollaborationProtocol.Type.DECISION_RESPONSE -> payload.optString("decision").ifBlank { "Decision AI 已返回决策" }
            CollaborationProtocol.Type.PROGRESS -> payload.optString("message").ifBlank { payload.optString("objective").ifBlank { "协作进度更新" } }
            CollaborationProtocol.Type.BLOCKED -> payload.optString("blocked_on").ifBlank { "Worker 暂时受阻" }
            CollaborationProtocol.Type.COMMIT -> "Commit " + payload.optString("sha").takeIf { it.isNotBlank() }?.take(10).orEmpty()
            CollaborationProtocol.Type.VERIFY -> "Verify：" + payload.optString("verdict").ifBlank { "待确认" }
            CollaborationProtocol.Type.COMPLETE -> payload.optString("summary").ifBlank { "协作任务完成" }
            CollaborationProtocol.Type.FILE_CHANGE_REQUEST -> payload.optString("path").ifBlank { "Worker 请求修改文件" }
            CollaborationProtocol.Type.ESCALATE -> "需要用户处理"
        }
        return "[协作 " + message.type.name + "] " + roleName + " → " + targetName + "\n" + detail
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
                val answer = BridgeApiClient(
                    BridgeApiConfig(normalizeBaseUrl(a.baseUrl), a.key, a.model)
                ).chat(conversation.messages, BridgeCommandSpec.aiSystemPrompt(limit))
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

    private fun sendWorkspaceCollaboration(input: EditText, conversation: BridgeConversation) {
        val text = input.text.toString().trim()
        if (text.isBlank()) return
        val current = project ?: return
        if (!AccessPolicy.isApiEnabled(this)) {
            Toast.makeText(this, "API 全局访问已关闭", Toast.LENGTH_SHORT).show()
            return
        }
        val ids = collaborationProfileIds()
        if (ids.first.isBlank() || ids.second.isBlank() || ids.first == ids.second) {
            Toast.makeText(this, "请先在工作区选择两个不同的协作 AI", Toast.LENGTH_SHORT).show()
            return
        }
        conversation.messages += BridgeChatMessage("user", text)
        conversation.messages += BridgeChatMessage("tool", "[协作进行中]\nDecision AI 正在分析并生成任务，Worker 随后接收任务。")
        collaborationRunningConversationId = conversation.id
        store.save(projects)
        render()
        runCollaboration(current, text)
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
            recordReceipt(conversation, "FAILED", "AI command", "当前工作区未设置 BridgeFS 工作目录，指令未执行。")
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
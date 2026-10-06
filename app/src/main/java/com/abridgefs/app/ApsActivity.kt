package com.abridgefs.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.graphics.Typeface
import android.view.*
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ApsActivity : Activity() {
    internal lateinit var host: FrameLayout
    private lateinit var bottomNav: LinearLayout
    private var page = 0
    internal var projectConfigOpen = false
    internal var projectChatOpen = true
    internal var projectTasksOpen = true
    internal var projectHistoryOpen = true
    internal var displayOpen = true
    internal val settingOpen = mutableSetOf<String>()
    internal var localFileAccess = false
    internal var storageAccess = false
    internal var externalAccess = false
    internal val pendingTaskMentions = linkedSetOf<String>()
    internal var projectInput: EditText? = null
    internal val projectStore by lazy { BridgeProjectStore(this) }
    internal var projects: MutableList<BridgeProject> = mutableListOf()
    internal var currentProject: BridgeProject? = null
    internal val standaloneConversationStore by lazy { BridgeConversationStore(this) }
    internal var standaloneConversations: MutableList<BridgeConversation> = mutableListOf()
    internal var currentStandaloneConversationId: String? = null
    internal var selectedApi = "未绑定"
    internal var selectedApiProfileId: String? = null
    internal val apiProfilesStore by lazy { ApiProfileStore(this) }
    internal var apiSelectorOpen = false
    internal var conversationManagementOpen = false
    internal var projectAccessOpen = false
    private val receiptReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val receiptId = intent.getStringExtra("receiptId").orEmpty()
            if (receiptId.isNotBlank()) {
                val prefs = getSharedPreferences("bridgefs", Context.MODE_PRIVATE)
                val queue = org.json.JSONArray(prefs.getString("pending_receipts", "[]") ?: "[]")
                val remaining = org.json.JSONArray()
                for (i in 0 until queue.length()) {
                    val item = queue.optJSONObject(i)
                    if (item?.optString("receiptId") != receiptId) remaining.put(item)
                }
                prefs.edit().putString("pending_receipts", remaining.toString()).apply()

                ProjectReceiptService(this@ApsActivity).record(
                    receiptId = receiptId,
                    status = intent.getStringExtra("status").orEmpty(),
                    command = intent.getStringExtra("command").orEmpty(),
                    message = intent.getStringExtra("message").orEmpty(),
                    time = intent.getLongExtra("time", System.currentTimeMillis()),
                    projectId = intent.getStringExtra("projectId"),
                    conversationId = intent.getStringExtra("conversationId"),
                    standaloneConversationId = intent.getStringExtra("standaloneConversationId")
                )
            }

            projects = projectStore.load()
            currentProject = currentProject?.id?.let { id ->
                projects.firstOrNull { it.id == id }
            } ?: projects.firstOrNull()
            render()
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        try {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            projects = projectStore.load()
            if (projects.isEmpty()) {
                val project = projectStore.newProject("默认项目")
                projects += project
                projectStore.save(projects)
            }
            currentProject = projects.firstOrNull()
            standaloneConversations = standaloneConversationStore.load()
            if (standaloneConversations.isEmpty()) {
                val created = standaloneConversationStore.newConversation("新会话 1", null)
                standaloneConversations += created
                standaloneConversationStore.save(standaloneConversations)
            }
            currentStandaloneConversationId = standaloneConversations.firstOrNull()?.id
            registerReceiver(receiptReceiver, IntentFilter("com.bridgefs.RESULT"), Context.RECEIVER_NOT_EXPORTED)
            buildShell()
            recoverPendingReceipts()
            AppLogger.log(this, "APS_STARTUP_READY")
        } catch (t: Throwable) {
            AppLogger.recordCrash(this, t)
            showStartupError(t)
        }
    }

    private fun showStartupError(throwable: Throwable) {
        val message = throwable::class.simpleName + ": " + (throwable.message ?: "unknown error")
        setContentView(TextView(this).apply {
            text = "APS 启动失败\\n\\n$message\\n\\n错误已写入 App 日志。"
            textSize = 15f
            setPadding(dp(24), dp(32), dp(24), dp(32))
            setTextIsSelectable(true)
        })
    }

    internal fun activeStandaloneConversation(): BridgeConversation? =
        currentStandaloneConversationId?.let { id -> standaloneConversations.firstOrNull { it.id == id } }
            ?: standaloneConversations.firstOrNull()

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c(R.color.bridgefs_surface))
        }
        host = FrameLayout(this)
        root.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(5), dp(10), dp(8))
        }
        listOf("项目", "对话", "设置").forEachIndexed { i, title ->
            nav.addView(TextView(this).apply {
                text = title
                textSize = 13f
                gravity = Gravity.CENTER
                typeface = if (page == i) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(c(if (page == i) R.color.bridgefs_text_primary else R.color.bridgefs_text_secondary))
                background = round(c(if (page == i) R.color.bridgefs_selected_surface else R.color.bridgefs_surface), dp(14))
                setOnClickListener { page = i; render() }
            }, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            })
        }
        nav.addView(TextView(this).apply {
            text = if (displayOpen) "↓" else "↑"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(c(R.color.bridgefs_text_primary))
            background = round(c(R.color.bridgefs_selected_surface), dp(14))
            contentDescription = if (displayOpen) "减少项目内容显示空间" else "恢复项目内容显示空间"
            setOnClickListener {
                if (page == 0) {
                    displayOpen = !displayOpen
                    render()
                }
            }
        }, LinearLayout.LayoutParams(dp(48), dp(46)).apply {
            marginStart = dp(3)
        })
        bottomNav = nav
        root.addView(nav)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(root)
        render()
    }

    private fun recoverPendingReceipts() {
        val prefs = getSharedPreferences("bridgefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("pending_receipts", "[]") ?: "[]"
        val queue = runCatching { org.json.JSONArray(raw) }.getOrElse {
            AppLogger.log(this, "PENDING_RECEIPTS_LOAD_FAILED", "invalid receipt JSON: " + (it.message ?: "unknown"))
            prefs.edit().remove("pending_receipts").apply()
            return
        }
        for (i in 0 until queue.length()) {
            runCatching {
                val item = queue.optJSONObject(i) ?: return@runCatching
                val intent = Intent("com.bridgefs.RESULT").apply {
                    putExtra("receiptId", item.optString("receiptId"))
                    putExtra("status", item.optString("status"))
                    putExtra("command", item.optString("command"))
                    putExtra("message", item.optString("message"))
                    putExtra("projectId", item.optString("projectId"))
                    putExtra("conversationId", item.optString("conversationId"))
                    putExtra("standaloneConversationId", item.optString("standaloneConversationId"))
                    putExtra("time", item.optLong("time", System.currentTimeMillis()))
                }
                receiptReceiver.onReceive(this, intent)
            }.onFailure {
                AppLogger.log(this, "PENDING_RECEIPT_RECOVERY_FAILED", "index=$i error=" + it::class.simpleName + ": " + (it.message ?: "unknown"))
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiptReceiver) }
        super.onDestroy()
    }

    private fun updateBottomNav() {
        if (!::bottomNav.isInitialized) return
        for (i in 0..2) {
            val item = bottomNav.getChildAt(i) as? TextView ?: continue
            val selected = page == i
            item.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            item.setTextColor(c(if (selected) R.color.bridgefs_text_primary else R.color.bridgefs_text_secondary))
            item.background = round(c(if (selected) R.color.bridgefs_selected_surface else R.color.bridgefs_surface), dp(14))
        }
        val display = bottomNav.getChildAt(3) as? TextView ?: return
        display.text = if (displayOpen) "↓" else "↑"
        display.contentDescription = if (displayOpen) "减少项目内容显示空间" else "恢复项目内容显示空间"
    }

    internal fun render() {
        updateBottomNav()
        host.removeAllViews()
        when (page) {
            0 -> projectPage()
            1 -> conversationPage()
            else -> settingsPage()
        }
    }
}
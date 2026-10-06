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
    private var page = 0
    internal var projectConfigOpen = false
    internal var projectChatOpen = true
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
    internal val receiptContinuationCounts = mutableMapOf<String, Int>()
    private val maxReceiptContinuations = 5
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
            }
            val projectId = intent.getStringExtra("projectId")?.takeIf { it.isNotBlank() } ?: return
            val conversationId = intent.getStringExtra("conversationId")?.takeIf { it.isNotBlank() } ?: return
            val project = projects.firstOrNull { it.id == projectId } ?: return
            val conversation = project.conversations.firstOrNull { it.id == conversationId } ?: return
            conversation.executions += BridgeReceiptRecord(
                status = intent.getStringExtra("status") ?: "UNKNOWN",
                command = intent.getStringExtra("command") ?: "",
                message = intent.getStringExtra("message") ?: "",
                time = intent.getLongExtra("time", System.currentTimeMillis()),
                receiptId = receiptId.ifBlank { java.util.UUID.randomUUID().toString() }
            )
            projectStore.save(projects)
            val receipt = conversation.executions.last()
            val continuationCount = (receiptContinuationCounts[conversation.id] ?: 0) + 1
            receiptContinuationCounts[conversation.id] = continuationCount
            if (continuationCount > maxReceiptContinuations) {
                conversation.messages += BridgeChatMessage(
                    "system",
                    "本轮连续施工已达到 $maxReceiptContinuations 次自动续接上限，已暂停。请确认当前状态后再继续。"
                )
                projectStore.save(projects)
                render()
                return
            }
            Thread {
                ProjectConversationService(this@ApsActivity).sendReceipt(project, conversation, receipt)
                runOnUiThread {
                    projectStore.save(projects)
                    render()
                }
            }.start()
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
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
        currentStandaloneConversationId =
            standaloneConversations.firstOrNull()?.id
        registerReceiver(receiptReceiver, IntentFilter("com.bridgefs.RESULT"), Context.RECEIVER_NOT_EXPORTED)
        buildShell()
        recoverPendingReceipts()
    }

    internal fun activeStandaloneConversation(): BridgeConversation? =
        currentStandaloneConversationId?.let { id ->
            standaloneConversations.firstOrNull { it.id == id }
        } ?: standaloneConversations.firstOrNull()

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c(R.color.bridgefs_surface))
        }
        host = FrameLayout(this)
        root.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        listOf("项目", "对话", "设置").forEachIndexed { i, title ->
            nav.addView(TextView(this).apply {
                text = title
                textSize = 14f
                gravity = Gravity.CENTER
                typeface = if (page == i) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(c(if (page == i) R.color.bridgefs_text_primary else R.color.bridgefs_text_secondary))
                background = if (page == i) round(c(R.color.bridgefs_input_surface), dp(12)) else null
                setOnClickListener { page = i; render() }
            }, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            })
        }
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
        val queue = org.json.JSONArray(prefs.getString("pending_receipts", "[]") ?: "[]")
        for (i in 0 until queue.length()) {
            val item = queue.optJSONObject(i) ?: continue
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
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiptReceiver) }
        super.onDestroy()
    }

    internal fun render() {
        host.removeAllViews()
        when (page) {
            0 -> projectPage()
            1 -> conversationPage()
            else -> settingsPage()
        }
    }
}
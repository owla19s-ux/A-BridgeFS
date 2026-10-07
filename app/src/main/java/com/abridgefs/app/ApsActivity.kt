package com.abridgefs.app

import android.app.Activity
import android.content.Context
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

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        AppLogger.log(this, "ACTIVITY_ON_CREATE")
        try {
            AppLogger.log(this, "STARTUP_STEP", "window")
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

            AppLogger.log(this, "STARTUP_STEP", "project_store_load")
            projects = projectStore.load()
            AppLogger.log(this, "STARTUP_STEP", "project_store_loaded count=${projects.size}")
            if (projects.isEmpty()) {
                AppLogger.log(this, "STARTUP_STEP", "create_default_project")
                val project = projectStore.newProject("默认项目")
                projects += project
                projectStore.save(projects)
            }
            currentProject = projects.firstOrNull()

            AppLogger.log(this, "STARTUP_STEP", "conversation_store_load")
            standaloneConversations = standaloneConversationStore.load()
            AppLogger.log(this, "STARTUP_STEP", "conversation_store_loaded count=${standaloneConversations.size}")
            if (standaloneConversations.isEmpty()) {
                AppLogger.log(this, "STARTUP_STEP", "create_default_conversation")
                val created = standaloneConversationStore.newConversation("新会话 1", null)
                standaloneConversations += created
                standaloneConversationStore.save(standaloneConversations)
            }
            currentStandaloneConversationId = standaloneConversations.firstOrNull()?.id

            AppLogger.log(this, "STARTUP_STEP", "build_shell")
            buildShell()

            AppLogger.log(this, "STARTUP_STEP", "recover_pending_receipts")
            recoverPendingReceipts()

            AppLogger.log(this, "APS_STARTUP_READY")
        } catch (t: Throwable) {
            AppLogger.log(this, "APS_STARTUP_FAILED", t::class.simpleName + ": " + (t.message ?: "unknown"))
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
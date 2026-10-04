package com.abridgefs.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Typeface
import android.view.*
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ApsActivity : Activity() {
    private lateinit var host: FrameLayout
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
    internal var conversationGroup = "默认分组"
    internal var currentConversation = "新会话 1"
    internal var selectedApi = "未绑定"
    internal val apiProfiles = listOf("未绑定", "API Profile（示例）", "API Profile 2（示例）")
    internal var apiSelectorOpen = false
    internal var conversationManagementOpen = false
    internal var projectAccessOpen = false
    internal val conversationGroupNames = linkedSetOf("默认分组")
    internal val conversationNames = linkedSetOf("新会话 1")

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
        buildShell()
    }

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

    private fun render() {
        host.removeAllViews()
        when (page) {
            0 -> projectPage()
            1 -> conversationPage()
            else -> settingsPage()
        }
    }
}

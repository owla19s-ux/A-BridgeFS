package com.abridgefs.app

import android.content.Context
import android.graphics.Typeface
import android.widget.*
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal fun ApsActivity.conversationPage() {
    val activity = this
    val project = currentProject
    val conversation = project?.activeConversation()

    val content = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(activity.dp(14), activity.dp(10), activity.dp(14), activity.dp(8))
    }
    content.addView(activity.title("对话"))
    content.addView(activity.section(
        "会话管理 · " + (conversation?.name ?: "未选择"),
        conversationManagementOpen
    ) {
        conversationManagementOpen = !conversationManagementOpen
        activity.render()
    })

    if (conversationManagementOpen && project != null) {
        content.addView(activity.card().apply {
            addView(activity.label("当前会话"))
            addView(activity.value(conversation?.name ?: "未命名"))
            project.conversations.forEach { item ->
                addView(Button(activity).apply {
                    text = if (item.id == project.activeConversationId) "✓  " + item.name else item.name
                    setOnClickListener {
                        project.activeConversationId = item.id
                        activity.projectStore.save(activity.projects)
                        conversationManagementOpen = false
                        apiSelectorOpen = false
                        activity.render()
                    }
                }, LinearLayout.LayoutParams(-1, activity.dp(44)).apply { topMargin = activity.dp(4) })
            }
            val actionRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            actionRow.addView(Button(activity).apply {
                text = "新建会话"
                setOnClickListener {
                    val next = "新会话 " + (project.conversations.size + 1)
                    val created = BridgeConversation(java.util.UUID.randomUUID().toString(), next)
                    project.conversations += created
                    project.activeConversationId = created.id
                    activity.projectStore.save(activity.projects)
                    conversationManagementOpen = false
                    activity.render()
                }
            }, LinearLayout.LayoutParams(0, activity.dp(44), 1f))
            actionRow.addView(Button(activity).apply {
                text = "删除当前"
                setOnClickListener {
                    if (project.conversations.size <= 1) {
                        activity.toast("默认会话不能删除")
                        return@setOnClickListener
                    }
                    project.conversations.removeAll { it.id == project.activeConversationId }
                    project.activeConversationId = project.conversations.firstOrNull()?.id
                    activity.projectStore.save(activity.projects)
                    conversationManagementOpen = false
                    activity.render()
                }
            }, LinearLayout.LayoutParams(0, activity.dp(44), 1f).apply { marginStart = activity.dp(6) })
            addView(actionRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.dp(6) })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.dp(6) })
    }

    val activeApiProfile = conversation?.apiId?.let { activity.apiProfilesStore.find(it) }
        ?: project?.defaultMemberId?.let { memberId ->
            project.aiMembers.firstOrNull { it.id == memberId }?.apiProfileId?.let { activity.apiProfilesStore.find(it) }
        }
    activity.selectedApi = activeApiProfile?.name ?: "未绑定"
    activity.selectedApiProfileId = activeApiProfile?.id

    content.addView(activity.section(
        "API · " + activity.selectedApi,
        apiSelectorOpen
    ) {
        apiSelectorOpen = !apiSelectorOpen
        activity.render()
    }, LinearLayout.LayoutParams(-1, activity.dp(52)).apply { topMargin = activity.dp(8) })

    if (apiSelectorOpen && project != null && conversation != null) {
        content.addView(activity.card().apply {
            addView(activity.label("选择 API Profile"))
            activity.apiProfilesStore.list().forEach { profile ->
                addView(TextView(activity).apply {
                    text = if (profile.id == conversation.apiId) "✓  " + profile.name else profile.name
                    textSize = 13f
                    setTextColor(activity.c(if (profile.id == conversation.apiId) R.color.bridgefs_text_primary else R.color.bridgefs_text_secondary))
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(activity.dp(8), 0, activity.dp(8), 0)
                    setOnClickListener {
                        conversation.apiId = profile.id
                        activity.selectedApi = profile.name
                        activity.selectedApiProfileId = profile.id
                        activity.projectStore.save(activity.projects)
                        apiSelectorOpen = false
                        activity.render()
                    }
                }, LinearLayout.LayoutParams(-1, activity.dp(44)))
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.dp(6) })
    }

    content.addView(activity.section("项目访问", projectAccessOpen) {
        projectAccessOpen = !projectAccessOpen
        activity.render()
    }, LinearLayout.LayoutParams(-1, activity.dp(52)).apply { topMargin = activity.dp(8) })

    if (projectAccessOpen) {
        content.addView(activity.card().apply {
            addView(activity.value("当前 Project Address：" + (project?.localAddress ?: "未设置")))
            addView(activity.value("GitHub：" + (project?.githubAddress?.repository ?: "未设置")))
            addView(activity.value("访问范围由 Project 权限决定。"))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.dp(6) })
    }

    content.addView(activity.card().apply {
        if (conversation == null || conversation.messages.isEmpty()) {
            addView(activity.messageBubble("系统", "当前会话还没有消息。"))
        } else {
            conversation.messages.forEach { message ->
                val displayName = when (message.role) {
                    "user" -> "你"
                    "assistant" -> message.apiName ?: "AI"
                    else -> "系统"
                }
                addView(activity.messageBubble(displayName, message.content))
            }
        }
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.dp(8) })

    activity.host.addView(ScrollView(activity).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
    activity.inputBar("输入消息……")
}\n
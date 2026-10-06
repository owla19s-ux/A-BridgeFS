package com.abridgefs.app

import android.widget.*
import android.view.*

internal fun ApsActivity.conversationPage() {
    val project = currentProject
    val conversation = activeStandaloneConversation()
    val constructionEnabled = AccessPolicy.isStandaloneConstructionEnabled(this)
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(10))
    }

    content.addView(title("对话"))
    content.addView(value(if (constructionEnabled) "独立对话 · 可施工当前 Project" else "独立对话 · 只读访问当前 Project 信息"))

    content.addView(section("会话 · " + (conversation?.name ?: "未选择"), conversationManagementOpen) {
        conversationManagementOpen = !conversationManagementOpen
        render()
    })
    if (conversationManagementOpen) {
        content.addView(card().apply {
            standaloneConversations.forEach { item ->
                addView(secondaryButton(if (item.id == conversation?.id) "✓  " + item.name else item.name) {
                    currentStandaloneConversationId = item.id
                    conversationManagementOpen = false
                    apiSelectorOpen = false
                    render()
                }, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(5) })
            }
            addView(primaryButton("新建会话") {
                val created = standaloneConversationStore.newConversation("新会话 " + (standaloneConversations.size + 1), null)
                standaloneConversations += created
                currentStandaloneConversationId = created.id
                standaloneConversationStore.save(standaloneConversations)
                conversationManagementOpen = false
                render()
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(3) })
            addView(secondaryButton("删除当前") {
                if (standaloneConversations.size <= 1) {
                    toast("默认会话不能删除")
                    return@secondaryButton
                }
                standaloneConversations.removeAll { it.id != conversation?.id }
                currentStandaloneConversationId = standaloneConversations.firstOrNull()?.id
                standaloneConversationStore.save(standaloneConversations)
                conversationManagementOpen = false
                render()
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(5) })
        })
    }

    val activeApi = conversation?.apiId?.let { apiProfilesStore.find(it) }
    selectedApi = activeApi?.name ?: "未绑定"
    content.addView(section("API · " + selectedApi, apiSelectorOpen) {
        apiSelectorOpen = !apiSelectorOpen
        render()
    })
    if (apiSelectorOpen && conversation != null) {
        content.addView(card().apply {
            addView(label("选择 API Profile"))
            apiProfilesStore.list().forEach { profile ->
                addView(secondaryButton(if (profile.id == conversation.apiId) "✓  " + profile.name else profile.name) {
                    conversation.apiId = profile.id
                    standaloneConversationStore.save(standaloneConversations)
                    apiSelectorOpen = false
                    render()
                }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(5) })
            }
        })
    }

    content.addView(section("Project Address · " + if (constructionEnabled) "可施工" else "只读", projectAccessOpen) {
        projectAccessOpen = !projectAccessOpen
        render()
    })
    if (projectAccessOpen) {
        content.addView(card().apply {
            addView(label("当前 Project"))
            addView(value(project?.name ?: "未选择"))
            addView(value("Local：" + (project?.localAddress ?: "未设置")))
            addView(value("GitHub：" + (project?.githubAddress?.repository ?: "未设置")))
            addView(value(if (constructionEnabled) "普通对话施工已开启。" else "普通对话施工默认关闭。"))
            addView(settingSwitch("允许普通对话施工", constructionEnabled) {
                AccessPolicy.setStandaloneConstructionEnabled(this@conversationPage, it)
                render()
            })
            addView(value("开启后，普通对话可以在用户明确要求时进入当前 Project 的施工链；仍受 Task、Project 写权限、权限策略和 ConstructionLock 限制。"))
        })
    }

    content.addView(card().apply {
        addView(label("消息"))
        if (conversation == null || conversation.messages.isEmpty()) {
            addView(messageBubble("系统", "当前独立对话还没有消息。"))
        } else {
            conversation.messages.forEach { message ->
                addView(messageBubble(
                    if (message.role == "user") "你" else if (message.role == "assistant") message.apiName ?: "AI" else "系统",
                    message.content
                ))
            }
        }
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

    host.addView(ScrollView(this).apply {
        isFillViewport = true
        addView(content)
    }, LinearLayout.LayoutParams(-1, 0, 1f))
    standaloneInputBar("输入消息……")
}

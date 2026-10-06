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
    val conversation = activeStandaloneConversation()
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(8))
    }
    content.addView(title("对话"))
    content.addView(section("会话管理 · " + (conversation?.name ?: "未选择"), conversationManagementOpen) {
        conversationManagementOpen = !conversationManagementOpen
        render()
    })
    if (conversationManagementOpen) {
        content.addView(card().apply {
            addView(label("独立对话"))
            standaloneConversations.forEach { item ->
                addView(Button(this@conversationPage).apply {
                    text = if (item.id == conversation?.id) "✓  " + item.name else item.name
                    setOnClickListener {
                        currentStandaloneConversationId = item.id
                        conversationManagementOpen = false
                        apiSelectorOpen = false
                        render()
                    }
                })
            }
            addView(Button(this@conversationPage).apply {
                text = "新建会话"
                setOnClickListener {
                    val created = standaloneConversationStore.newConversation("新会话 " + (standaloneConversations.size + 1), null)
                    standaloneConversations += created
                    currentStandaloneConversationId = created.id
                    standaloneConversationStore.save(standaloneConversations)
                    conversationManagementOpen = false
                    render()
                }
            })
            addView(Button(this@conversationPage).apply {
                text = "删除当前"
                setOnClickListener {
                    if (standaloneConversations.size <= 1) {
                        toast("默认会话不能删除")
                        return@setOnClickListener
                    }
                    standaloneConversations.removeAll { it.id == conversation?.id }
                    currentStandaloneConversationId = standaloneConversations.firstOrNull()?.id
                    standaloneConversationStore.save(standaloneConversations)
                    conversationManagementOpen = false
                    render()
                }
            })
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
                addView(TextView(this@conversationPage).apply {
                    text = if (profile.id == conversation.apiId) "✓  " + profile.name else profile.name
                    setOnClickListener {
                        conversation.apiId = profile.id
                        standaloneConversationStore.save(standaloneConversations)
                        apiSelectorOpen = false
                        render()
                    }
                })
            }
        })
    }
    content.addView(section("Project Address 只读访问", projectAccessOpen) {
        projectAccessOpen = !projectAccessOpen
        render()
    })
    if (projectAccessOpen) {
        val p = currentProject
        content.addView(card().apply {
            addView(value("当前 Project：" + (p?.name ?: "未选择")))
            addView(value("Local：" + (p?.localAddress ?: "未设置")))
            addView(value("GitHub：" + (p?.githubAddress?.repository ?: "未设置")))
            addView(value("这里只读，不继承 Project 施工权限。"))
        })
    }
    content.addView(card().apply {
        if (conversation == null || conversation.messages.isEmpty()) {
            addView(messageBubble("系统", "当前独立对话还没有消息。"))
        } else {
            conversation.messages.forEach { message ->
                addView(messageBubble(if (message.role == "user") "你" else if (message.role == "assistant") message.apiName ?: "AI" else "系统", message.content))
            }
        }
    })
    host.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
    standaloneInputBar("输入消息……")
}
package com.abridgefs.app.ui

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun conversationPage(activity: Activity, host: FrameLayout) {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
        }
        content.addView(title("对话"))

        content.addView(section("会话管理", conversationManagementOpen) {
            conversationManagementOpen = !conversationManagementOpen
            render()
        })

        if (conversationManagementOpen) {
            content.addView(card().apply {
                addView(label("当前会话"))
                addView(value(currentConversation))
                addView(label("当前分组"))
                val groupRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                groupRow.addView(value(conversationGroupNames.joinToString("  ·  ")),
                    LinearLayout.LayoutParams(0, dp(40), 1f))
                groupRow.addView(Button(activity).apply {
                    text = "新建分组"
                    setOnClickListener {
                        val next = "分组 " + (conversationGroupNames.size + 1)
                        conversationGroupNames.add(next)
                        currentConversation = next
                        render()
                    }
                }, LinearLayout.LayoutParams(dp(94), dp(44)))
                addView(groupRow)
                val actionRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                actionRow.addView(Button(activity).apply {
                    text = "新建会话"
                    setOnClickListener {
                        val next = "新会话 " + (conversationNames.size + 1)
                        conversationNames.add(next)
                        conversationGroup = next
                        render()
                    }
                }, LinearLayout.LayoutParams(0, dp(44), 1f))
                actionRow.addView(Button(activity).apply {
                    text = "删除当前"
                    setOnClickListener {
                        if (conversationNames.size > 1) {
                            conversationNames.remove(currentConversation)
                            currentConversation = conversationNames.first()
                            render()
                        } else {
                            toast("默认会话不能删除")
                        }
                    }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(6) })
                addView(actionRow)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }

        content.addView(section("API · $selectedApi", apiSelectorOpen) {
            apiSelectorOpen = !apiSelectorOpen
            render()
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })

        if (apiSelectorOpen) {
            content.addView(card().apply {
                addView(label("选择 API Profile"))
                apiProfiles.forEach { profile ->
                    addView(TextView(activity).apply {
                        text = if (profile == selectedApi) "✓  $profile" else profile
                        textSize = 13f
                        setTextColor(c(if (profile == selectedApi) R.color.bridgefs_text_primary else R.color.bridgefs_text_secondary))
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(8), 0, dp(8), 0)
                        setOnClickListener {
                            selectedApi = profile
                            apiSelectorOpen = false
                            render()
                        }
                    }, LinearLayout.LayoutParams(-1, dp(44)))
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }

        content.addView(section("项目访问", projectAccessOpen) {
            projectAccessOpen = !projectAccessOpen
            render()
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })

        if (projectAccessOpen) {
            content.addView(card().apply {
                addView(value("当前 Project Address：未设置"))
                addView(value("访问范围由 Project 权限决定。"))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }

        content.addView(card().apply {
            addView(messageBubble("AI", "这里是普通 AI 对话区域。"))
            addView(messageBubble("AI", "当前会话可以访问 Project 的授权资源。"))
            addView(messageBubble("系统", "消息复制、真实历史记录将在接线阶段加入。"))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        host.addView(ScrollView(activity).apply { addView(content) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        inputBar("输入消息……")
    }

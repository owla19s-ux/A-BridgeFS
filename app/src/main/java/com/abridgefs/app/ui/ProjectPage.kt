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

fun projectPage(activity: Activity, host: FrameLayout) {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
        }
        content.addView(title("默认项目"))
        content.addView(TextView(activity).apply {
            text = "Project Address
Local：未设置
GitHub：未设置"
            textSize = 12f
            setTextColor(c(R.color.bridgefs_text_secondary))
            setPadding(0, dp(3), 0, dp(6))
        })
        content.addView(TextView(activity).apply {
            text = if (displayOpen) "↓  收起内容区" else "↑  展开内容区"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(c(R.color.bridgefs_text_secondary))
            background = round(c(R.color.bridgefs_input_surface), dp(10))
            setPadding(0, dp(9), 0, dp(9))
            setOnClickListener { displayOpen = !displayOpen; render() }
        }, LinearLayout.LayoutParams(-1, dp(38)).apply { bottomMargin = dp(6) })
        content.addView(section("项目配置", projectConfigOpen) {
            projectConfigOpen = !projectConfigOpen
            render()
        })
        if (projectConfigOpen) {
            content.addView(card().apply {
                addView(label("项目名称"))
                addView(EditText(activity).apply {
                    hint = "默认项目"
                    maxLines = 1
                })
                addView(label("Project Address"))
                addView(value("Local Project Address：未设置"))
                addView(value("GitHub Project Address：未设置"))
                addView(Button(activity).apply {
                    text = "配置 Project Address"
                    setOnClickListener { toast("Project Address 接线将在下一阶段接入") }
                })
                addView(label("API"))
                addView(value("Default API：未绑定"))
                addView(label("项目级配置仅在这里维护"))
                addView(Button(activity).apply {
                    text = "选择 API Profile"
                    setOnClickListener { toast("API Profile 接线将在下一阶段接入") }
                })
            })
        }
        if (displayOpen) content.addView(card().apply {
            addView(label("待处理任务"))
            listOf("UI 输入框问题", "构建问题", "签名冲突").forEach { task ->
                addView(CheckBox(activity).apply {
                    text = task
                    textSize = 13f
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) {
                            pendingTaskMentions.add("@" + task)
                            syncProjectInput()
                            toast("@" + task + " 已加入当前输入目标")
                        } else {
                            pendingTaskMentions.remove("@" + task)
                            syncProjectInput()
                        }
                    }
                })
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        if (displayOpen) content.addView(section("项目主要对话", projectChatOpen) {
            projectChatOpen = !projectChatOpen
            render()
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        if (displayOpen && projectChatOpen) {
            content.addView(card().apply {
                addView(value("这里是项目历史对话区域。当前为 UI 壳，尚未连接真实消息数据。"))
            })
        }
        if (displayOpen) {
            content.addView(Button(activity).apply {
                text = "Request AI Assistance"
                setOnClickListener { toast("协助链将在 Project UI 壳稳定后接入") }
            }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
        }
        host.addView(ScrollView(activity).apply {
            isFillViewport = true
            addView(content)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        inputBar("输入工作目标……")
    }

package com.abridgefs.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal fun ApsActivity.settingSwitch(name: String, checked: Boolean, onChanged: (Boolean) -> Unit) =
    Switch(this).apply {
        text = name
        isChecked = checked
        textSize = 13f
        setTextColor(c(R.color.bridgefs_text_primary))
        setPadding(0, dp(2), 0, dp(2))
        setOnCheckedChangeListener { _, value -> onChanged(value) }
    }

internal fun ApsActivity.messageBubble(name: String, text: String): LinearLayout {
    val activity = this
    return LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = round(c(R.color.bridgefs_surface), dp(12))
        addView(TextView(activity).apply {
            this.text = name
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(c(R.color.bridgefs_text_secondary))
        })
        addView(TextView(activity).apply {
            this.text = text
            textSize = 13f
            setTextIsSelectable(true)
            setTextColor(c(R.color.bridgefs_text_primary))
            setPadding(0, dp(3), 0, 0)
        })
        addView(TextView(activity).apply {
            this.text = "复制"
            textSize = 11f
            setTextIsSelectable(false)
            setTextColor(c(R.color.bridgefs_text_secondary))
            setPadding(0, dp(5), 0, 0)
            setOnClickListener {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("APS message", text))
                toast("已复制")
            }
        })
    }
}

internal fun ApsActivity.requestProjectAssistance() {
    val project = currentProject ?: run {
        toast("当前没有可用 Project")
        return
    }
    val conversation = project.activeConversation()
    receiptContinuationCounts.remove(conversation.id)
    val targets = pendingTaskMentions.toList()
    val request = if (targets.isEmpty()) {
        "请协助检查并推进当前 Project 的工作。先根据当前项目资料给出下一步可执行方案。"
    } else {
        targets.joinToString(" ") + " 请协助处理这些任务。先读取当前 Project 的相关资料，判断需要修改什么，并给出下一步可执行方案。"
    }

    conversation.messages += BridgeChatMessage("user", request)
    projectStore.save(projects)
    toast("正在请求当前 Project AI")
    Thread {
        val result = ProjectConversationService(this).send(project, conversation, request)
        runOnUiThread {
            if (result.answer != null) {
                conversation.messages += BridgeChatMessage("assistant", result.answer)
            } else {
                conversation.messages += BridgeChatMessage("system", result.error ?: "协助请求失败")
            }
            projectStore.save(projects)
            pendingTaskMentions.clear()
            render()
        }
    }.start()
}

internal fun ApsActivity.inputBar(hint: String) {
    val bar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(10), dp(5), dp(10), dp(5))
    }
    val input = EditText(this).apply {
        this.hint = hint
        if (hint.contains("工作目标")) projectInput = this
        maxLines = 4
        if (pendingTaskMentions.isNotEmpty() && hint.contains("工作目标")) {
            setText(pendingTaskMentions.joinToString(" ") + " ")
            setSelection(text.length)
        }
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = round(c(R.color.bridgefs_input_surface), dp(16))
    }
    bar.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
    bar.addView(Button(this).apply {
        text = "发送"
        minHeight = 0
        minimumHeight = 0
        setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isBlank()) return@setOnClickListener

            val project = currentProject
            if (project == null) {
                toast("当前没有可用 Project")
                return@setOnClickListener
            }
            val conversation = project.activeConversation()
            receiptContinuationCounts.remove(conversation.id)
            conversation.messages += BridgeChatMessage("user", text)
            projectStore.save(projects)
            input.isEnabled = false
            toast("正在请求当前 Project AI")
            Thread {
                val result = ProjectConversationService(this@inputBar).send(project, conversation, text)
                runOnUiThread {
                    input.isEnabled = true
                    if (result.answer != null) {
                        val profile = conversation.apiId?.let { apiProfilesStore.find(it) }
                            ?: project.defaultMemberId?.let { memberId ->
                                project.aiMembers.firstOrNull { it.id == memberId }?.apiProfileId?.let { apiProfilesStore.find(it) }
                            }
                        conversation.messages += BridgeChatMessage(
                            "assistant",
                            result.answer,
                            apiId = profile?.id,
                            apiName = profile?.name
                        )
                    } else {
                        conversation.messages += BridgeChatMessage("system", result.error ?: "请求失败")
                    }
                    projectStore.save(projects)
                    input.setText("")
                    pendingTaskMentions.clear()
                    input.clearFocus()
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(input.windowToken, 0)
                    render()
                }
            }.start()
    }.start()
        }
    }
}

internal fun ApsActivity.standaloneInputBar(hint: String) {
    val bar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(10), dp(5), dp(10), dp(5))
    }
    val input = EditText(this).apply {
        this.hint = hint
        maxLines = 4
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = round(c(R.color.bridgefs_input_surface), dp(16))
    }
    bar.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
    bar.addView(Button(this).apply {
        text = "发送"
        minHeight = 0
        minimumHeight = 0
        setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isBlank()) return@setOnClickListener
            val conversation = activeStandaloneConversation() ?: return@setOnClickListener
            val profile = conversation.apiId?.let { apiProfilesStore.find(it) }
            if (profile == null) {
                toast("请先选择 API Profile")
                return@setOnClickListener
            }
            conversation.messages += BridgeChatMessage("user", text)
            standaloneConversationStore.save(standaloneConversations)
            input.isEnabled = false
            Thread {
                val result = StandaloneConversationService(this@standaloneInputBar).send(
                    conversation, text, currentProject
                )
                runOnUiThread {
                    input.isEnabled = true
                    if (result.answer != null) {
                        conversation.messages += BridgeChatMessage(
                            "assistant", result.answer,
                            apiId = profile.id, apiName = profile.name
                        )
                    } else {
                        conversation.messages += BridgeChatMessage("system", result.error ?: "请求失败")
                    }
                    standaloneConversationStore.save(standaloneConversations)
                    input.setText("")
                    input.clearFocus()
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(input.windowToken, 0)
                    render()
                }
            }.start()
        }
    }, LinearLayout.LayoutParams(dp(78), dp(52)).apply { marginStart = dp(6) })

    ViewCompat.setOnApplyWindowInsetsListener(bar) { v, insets ->
        v.translationY = -insets.getInsets(WindowInsetsCompat.Type.ime()).bottom.toFloat()
        insets
    }
    host.addView(bar, LinearLayout.LayoutParams(-1, dp(62)))
}

internal fun ApsActivity.title(text: String) = TextView(this).apply {
    this.text = text
    textSize = 21f
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(c(R.color.bridgefs_text_primary))
    setPadding(0, 0, 0, dp(8))
}

internal fun ApsActivity.section(text: String, open: Boolean, click: () -> Unit) =
    TextView(this).apply {
        this.text = text + "    " + if (open) "▴" else "▾"
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), 0, dp(14), 0)
        background = round(c(R.color.bridgefs_input_surface), dp(14))
        setOnClickListener { click() }
    }

internal fun ApsActivity.info(name: String, value: String) = card().apply {
    addView(label(name))
    addView(value(value))
}

internal fun ApsActivity.card() = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(12), dp(10), dp(12), dp(10))
    background = round(c(R.color.bridgefs_input_surface), dp(14))
}

internal fun ApsActivity.label(text: String) = TextView(this).apply {
    this.text = text
    textSize = 11f
    setTextColor(c(R.color.bridgefs_text_secondary))
}

internal fun ApsActivity.value(text: String) = TextView(this).apply {
    this.text = text
    textSize = 13f
    setTextColor(c(R.color.bridgefs_text_secondary))
    setPadding(0, dp(4), 0, dp(4))
}

internal fun ApsActivity.syncProjectInput() {
    val input = projectInput ?: return
    val prefix = pendingTaskMentions.joinToString(" ")
    val current = input.text.toString()
    val cleaned = current.replace(Regex("""(@[^ ]+\s*)+"""), "").trimStart()
    input.setText(if (prefix.isBlank()) cleaned else prefix + " " + cleaned)
    input.setSelection(input.text.length)
}

internal fun ApsActivity.toast(text: String) =
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

internal fun ApsActivity.dp(v: Int) =
    (v * resources.displayMetrics.density).toInt()

internal fun ApsActivity.c(id: Int) =
    resources.getColor(id, theme)

internal fun ApsActivity.round(color: Int, radius: Int) =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

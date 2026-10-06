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
        setPadding(0, dp(3), 0, dp(3))
        setOnCheckedChangeListener { _, value -> onChanged(value) }
    }

internal fun ApsActivity.messageBubble(name: String, text: String): LinearLayout {
    val activity = this
    return LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(11), dp(14), dp(10))
        background = round(c(R.color.bridgefs_surface), dp(14))
        elevation = dp(1).toFloat()
        addView(TextView(activity).apply {
            this.text = name
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(c(R.color.bridgefs_text_secondary))
        })
        addView(TextView(activity).apply {
            this.text = text
            textSize = 14f
            setTextIsSelectable(true)
            setTextColor(c(R.color.bridgefs_text_primary))
            setPadding(0, dp(4), 0, 0)
        })
        addView(TextView(activity).apply {
            this.text = "复制"
            textSize = 11f
            setTextColor(c(R.color.bridgefs_text_secondary))
            setPadding(0, dp(6), 0, 0)
            setOnClickListener {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("APS message", text))
                toast("已复制")
            }
        })
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) }
    }
}

internal fun ApsActivity.requestProjectAssistance() {
    val project = currentProject ?: run {
        toast("当前没有可用 Project")
        return
    }
    val conversation = project.activeConversation()
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
            if (result.answer != null) conversation.messages += BridgeChatMessage("assistant", result.answer)
            else conversation.messages += BridgeChatMessage("system", result.error ?: "协助请求失败")
            projectStore.save(projects)
            pendingTaskMentions.clear()
            render()
        }
    }.start()
}

internal fun ApsActivity.inputBar(hint: String) {
    val bar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(5), dp(10), dp(6))
    }
    val input = EditText(this).apply {
        this.hint = hint
        if (hint.contains("工作目标")) projectInput = this
        maxLines = 4
        if (pendingTaskMentions.isNotEmpty() && hint.contains("工作目标")) {
            setText(pendingTaskMentions.joinToString(" ") + " ")
            setSelection(text.length)
        }
        textSize = 14f
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = round(c(R.color.bridgefs_input_surface), dp(16))
    }
    bar.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
    bar.addView(primaryButton("发送") {
        val text = input.text.toString().trim()
        if (text.isBlank()) return@primaryButton
        val project = currentProject ?: run { toast("当前没有可用 Project"); return@primaryButton }
        val conversation = project.activeConversation()
        receiptContinuationCounts.remove(conversation.id)
        conversation.messages += BridgeChatMessage("user", text)
        projectStore.save(projects)
        input.isEnabled = false
        Thread {
            val result = ProjectConversationService(this@inputBar).send(project, conversation, text)
            runOnUiThread {
                input.isEnabled = true
                if (result.answer != null) {
                    val profile = conversation.apiId?.let { apiProfilesStore.find(it) }
                        ?: project.defaultMemberId?.let { memberId -> project.aiMembers.firstOrNull { it.id == memberId }?.apiProfileId?.let { apiProfilesStore.find(it) } }
                    conversation.messages += BridgeChatMessage("assistant", result.answer, apiId = profile?.id, apiName = profile?.name)
                    if (result.error != null) conversation.messages += BridgeChatMessage("system", result.error)
                } else conversation.messages += BridgeChatMessage("system", result.error ?: "请求失败")
                projectStore.save(projects)
                input.setText("")
                pendingTaskMentions.clear()
                input.clearFocus()
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
                render()
            }
        }.start()
    }, dp(78))
    ViewCompat.setOnApplyWindowInsetsListener(bar) { v, insets ->
        v.translationY = -insets.getInsets(WindowInsetsCompat.Type.ime()).bottom.toFloat()
        insets
    }
    host.addView(bar, LinearLayout.LayoutParams(-1, dp(64)))
}

internal fun ApsActivity.standaloneInputBar(hint: String) {
    val bar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(5), dp(10), dp(6))
    }
    val input = EditText(this).apply {
        this.hint = hint
        maxLines = 4
        textSize = 14f
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = round(c(R.color.bridgefs_input_surface), dp(16))
    }
    bar.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
    bar.addView(primaryButton("发送") {
        val text = input.text.toString().trim()
        if (text.isBlank()) return@primaryButton
        val conversation = activeStandaloneConversation() ?: return@primaryButton
        val profile = conversation.apiId?.let { apiProfilesStore.find(it) }
        if (profile == null) {
            toast("请先选择 API Profile")
            return@primaryButton
        }
        conversation.messages += BridgeChatMessage("user", text)
        standaloneConversationStore.save(standaloneConversations)
        input.isEnabled = false
        Thread {
            val result = StandaloneConversationService(this@standaloneInputBar).send(conversation, text, currentProject)
            runOnUiThread {
                input.isEnabled = true
                if (result.answer != null) conversation.messages += BridgeChatMessage("assistant", result.answer, apiId = profile.id, apiName = profile.name)
                else conversation.messages += BridgeChatMessage("system", result.error ?: "请求失败")
                standaloneConversationStore.save(standaloneConversations)
                input.setText("")
                input.clearFocus()
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
                render()
            }
        }.start()
    }, dp(78))
    ViewCompat.setOnApplyWindowInsetsListener(bar) { v, insets ->
        v.translationY = -insets.getInsets(WindowInsetsCompat.Type.ime()).bottom.toFloat()
        insets
    }
    host.addView(bar, LinearLayout.LayoutParams(-1, dp(64)))
}

internal fun ApsActivity.primaryButton(text: String, click: () -> Unit) = TextView(this).apply {
    this.text = text
    textSize = 13f
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER
    setTextColor(c(R.color.bridgefs_surface))
    background = round(c(R.color.bridgefs_orange), dp(14))
    setPadding(dp(8), 0, dp(8), 0)
    setOnClickListener { click() }
}

internal fun ApsActivity.secondaryButton(text: String, click: () -> Unit) = TextView(this).apply {
    this.text = text
    textSize = 13f
    gravity = Gravity.CENTER
    setTextColor(c(R.color.bridgefs_button_text))
    background = round(c(R.color.bridgefs_button_bg), dp(12))
    setPadding(dp(10), 0, dp(10), 0)
    setOnClickListener { click() }
}

internal fun ApsActivity.title(text: String) = TextView(this).apply {
    this.text = text
    textSize = 22f
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(c(R.color.bridgefs_text_primary))
    setPadding(0, 0, 0, dp(10))
}

internal fun ApsActivity.section(text: String, open: Boolean, click: () -> Unit) =
    TextView(this).apply {
        this.text = text + "    " + if (open) "▴" else "▾"
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), 0, dp(14), 0)
        setTextColor(c(R.color.bridgefs_text_primary))
        background = round(c(R.color.bridgefs_input_surface), dp(14))
        setOnClickListener { click() }
    }

internal fun ApsActivity.info(name: String, value: String) = card().apply {
    addView(label(name))
    addView(value(value))
}

internal fun ApsActivity.card() = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(13), dp(11), dp(13), dp(11))
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
    var cleaned = input.text.toString()
    pendingTaskMentions.forEach { mention -> cleaned = cleaned.replace(mention, "") }
    cleaned = cleaned.trim()
    input.setText(if (prefix.isBlank()) cleaned else if (cleaned.isBlank()) prefix + " " else prefix + " " + cleaned)
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
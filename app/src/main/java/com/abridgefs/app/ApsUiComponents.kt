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

internal fun ApsActivity.messageBubble(name: String, text: String) =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = round(c(R.color.bridgefs_surface), dp(12))
        addView(TextView(this@messageBubble).apply {
            this.text = name
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(c(R.color.bridgefs_text_secondary))
        })
        addView(TextView(this@ApsActivity).apply {
            this.text = text
            textSize = 13f
            setTextColor(c(R.color.bridgefs_text_primary))
            setPadding(0, dp(3), 0, 0)
        })
        addView(TextView(this@ApsActivity).apply {
            this.text = "复制"
            textSize = 11f
            setTextColor(c(R.color.bridgefs_text_secondary))
            setPadding(0, dp(5), 0, 0)
            setOnClickListener { toast("复制将在消息接线阶段启用") }
        })
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

            if (hint.contains("工作目标")) {
                val project = currentProject
                if (project == null) {
                    toast("当前没有可用 Project")
                    return@setOnClickListener
                }
                val conversation = project.activeConversation()
                conversation.messages += BridgeChatMessage("user", text)
                projectStore.save(projects)
                input.isEnabled = false
                toast("正在请求当前 Project AI")
                Thread {
                    val result = ProjectConversationService(this@inputBar).send(project, conversation, text)
                    runOnUiThread {
                        input.isEnabled = true
                        if (result.answer != null) {
                            conversation.messages += BridgeChatMessage("assistant", result.answer)
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
            } else {
                toast("独立对话尚未接入真实会话")
            }
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

package com.abridgefs.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.content.Context
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

    internal fun settingSwitch(name: String, checked: Boolean, onChanged: (Boolean) -> Unit) =
        Switch(this).apply {
            text = name
            isChecked = checked
            textSize = 13f
            setTextColor(c(R.color.bridgefs_text_primary))
            setPadding(0, dp(2), 0, dp(2))
            setOnCheckedChangeListener { _, value ->
                onChanged(value)
            }
        }

    internal fun messageBubble(name: String, text: String) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = round(c(R.color.bridgefs_surface), dp(12))
            addView(TextView(this@ApsActivity).apply {
                this.text = name
                textSize = 11f
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
                text = "复制"
                textSize = 11f
                setTextColor(c(R.color.bridgefs_text_secondary))
                setPadding(0, dp(5), 0, 0)
                setOnClickListener { toast("复制将在消息接线阶段启用") }
            })
        }

    internal fun inputBar(hint: String) {
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
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = round(c(R.color.bridgefs_input_surface), dp(14))
        }
        bar.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
        bar.addView(Button(this).apply {
            text = "发送"
            setOnClickListener {
                if (input.text.toString().trim().isNotBlank()) {
                    toast("输入已进入当前工作流（UI 壳）")
                    pendingTaskMentions.clear()
                    input.setText("")
                    input.clearFocus()
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(input.windowToken, 0)
                }
            }
        }, LinearLayout.LayoutParams(dp(78), dp(52)).apply { marginStart = dp(6) })
        ViewCompat.setOnApplyWindowInsetsListener(bar) { v, insets ->
            v.translationY = -insets.getInsets(WindowInsetsCompat.Type.ime()).bottom.toFloat()
            insets
        }
        host.addView(bar, LinearLayout.LayoutParams(-1, dp(62)))
    }

    internal fun title(text: String) = TextView(this).apply {
        this.text = text
        textSize = 21f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(c(R.color.bridgefs_text_primary))
        setPadding(0, 0, 0, dp(8))
    }

    internal fun section(text: String, open: Boolean, click: () -> Unit) =
        TextView(this).apply {
            this.text = text + "    " + if (open) "▴" else "▾"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = round(c(R.color.bridgefs_input_surface), dp(12))
            setOnClickListener { click() }
        }

    internal fun info(name: String, value: String) = card().apply {
        addView(label(name))
        addView(value(value))
    }

    internal fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = round(c(R.color.bridgefs_input_surface), dp(14))
    }

    internal fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTextColor(c(R.color.bridgefs_text_secondary))
    }

    internal fun value(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(c(R.color.bridgefs_text_secondary))
        setPadding(0, dp(4), 0, dp(4))
    }

    internal fun syncProjectInput() {
        val input = projectInput ?: return
        val prefix = pendingTaskMentions.joinToString(" ")
        val current = input.text.toString()
        val cleaned = current.replace(Regex("""(@[^ ]+\s*)+"""), "").trimStart()
        input.setText(if (prefix.isBlank()) cleaned else prefix + " " + cleaned)
        input.setSelection(input.text.length)
    }

    internal fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    internal fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    internal fun c(id: Int) = resources.getColor(id, theme)
    internal fun round(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }
}
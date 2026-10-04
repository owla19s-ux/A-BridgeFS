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
    private var projectConfigOpen = false
    private var projectChatOpen = true
    private var displayOpen = true
    private val settingOpen = mutableSetOf<String>()
    private var localFileAccess = false
    private var storageAccess = false
    private var externalAccess = false
    private val pendingTaskMentions = linkedSetOf<String>()
    private var projectInput: EditText? = null
    private var conversationGroup = "默认分组"
    private var currentConversation = "新会话 1"
    private var selectedApi = "未绑定"
    private val apiProfiles = listOf("未绑定", "API Profile（示例）", "API Profile 2（示例）")
    private var apiSelectorOpen = false
    private var conversationManagementOpen = false
    private var projectAccessOpen = false
    private val conversationGroupNames = linkedSetOf("默认分组")
    private val conversationNames = linkedSetOf("新会话 1")

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

    private fun projectPage() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
        }
        content.addView(title("默认项目"))
        content.addView(TextView(this).apply {
            text = "Project Address
Local：未设置
GitHub：未设置"
            textSize = 12f
            setTextColor(c(R.color.bridgefs_text_secondary))
            setPadding(0, dp(3), 0, dp(6))
        })
        content.addView(TextView(this).apply {
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
                addView(EditText(this@ApsActivity).apply {
                    hint = "默认项目"
                    maxLines = 1
                })
                addView(label("Project Address"))
                addView(value("Local Project Address：未设置"))
                addView(value("GitHub Project Address：未设置"))
                addView(Button(this@ApsActivity).apply {
                    text = "配置 Project Address"
                    setOnClickListener { toast("Project Address 接线将在下一阶段接入") }
                })
                addView(label("API"))
                addView(value("Default API：未绑定"))
                addView(label("项目级配置仅在这里维护"))
                addView(Button(this@ApsActivity).apply {
                    text = "选择 API Profile"
                    setOnClickListener { toast("API Profile 接线将在下一阶段接入") }
                })
            })
        }
        if (displayOpen) content.addView(card().apply {
            addView(label("待处理任务"))
            listOf("UI 输入框问题", "构建问题", "签名冲突").forEach { task ->
                addView(CheckBox(this@ApsActivity).apply {
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
            content.addView(Button(this).apply {
                text = "Request AI Assistance"
                setOnClickListener { toast("协助链将在 Project UI 壳稳定后接入") }
            }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
        }
        host.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        inputBar("输入工作目标……")
    }

    private fun conversationPage() {
        val content = LinearLayout(this).apply {
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
                val groupRow = LinearLayout(this@ApsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                groupRow.addView(value(conversationGroupNames.joinToString("  ·  ")),
                    LinearLayout.LayoutParams(0, dp(40), 1f))
                groupRow.addView(Button(this@ApsActivity).apply {
                    text = "新建分组"
                    setOnClickListener {
                        val next = "分组 " + (conversationGroupNames.size + 1)
                        conversationGroupNames.add(next)
                        currentConversation = next
                        render()
                    }
                }, LinearLayout.LayoutParams(dp(94), dp(44)))
                addView(groupRow)
                val actionRow = LinearLayout(this@ApsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                actionRow.addView(Button(this@ApsActivity).apply {
                    text = "新建会话"
                    setOnClickListener {
                        val next = "新会话 " + (conversationNames.size + 1)
                        conversationNames.add(next)
                        conversationGroup = next
                        render()
                    }
                }, LinearLayout.LayoutParams(0, dp(44), 1f))
                actionRow.addView(Button(this@ApsActivity).apply {
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
                    addView(TextView(this@ApsActivity).apply {
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

        host.addView(ScrollView(this).apply { addView(content) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        inputBar("输入消息……")
    }
    private fun settingsPage() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(14))
        }
        content.addView(title("设置"))
        listOf("连接", "权限", "文件", "执行", "外观", "通知", "日志", "系统").forEach { name ->
            val open = settingOpen.contains(name)
            content.addView(section(name, open) {
                if (open) settingOpen.remove(name) else settingOpen.add(name)
                render()
            }, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(4) })
            if (open) {
                content.addView(card().apply {
                    addView(value(
                        if (name == "连接") "全局连接资源"
                        else if (name == "权限") "仅管理没有明确归属到具体模块的 APS 系统级权限"
                        else "系统级 " + name + " 设置"
                    ))
                    if (name == "连接") {
                        addView(Button(this@ApsActivity).apply {
                            text = "API Profiles"
                            setOnClickListener { toast("API 连接及其内部权限将在接线阶段恢复") }
                        })
                        addView(Button(this@ApsActivity).apply {
                            text = "GitHub"
                            setOnClickListener { toast("GitHub 连接及其内部权限将在接线阶段恢复") }
                        })
                    }
                    if (name == "权限") {
                        addView(settingSwitch("本地文件访问", localFileAccess) {
                            localFileAccess = it
                        })
                        addView(settingSwitch("存储访问", storageAccess) {
                            storageAccess = it
                        })
                        addView(settingSwitch("其他 APS 系统访问", externalAccess) {
                            externalAccess = it
                        })
                    }
                })
            }
        }
        host.addView(ScrollView(this).apply { addView(content) },
            LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun settingSwitch(name: String, checked: Boolean, onChanged: (Boolean) -> Unit) =
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

    private fun messageBubble(name: String, text: String) =
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

    private fun inputBar(hint: String) {
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

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        textSize = 21f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(c(R.color.bridgefs_text_primary))
        setPadding(0, 0, 0, dp(8))
    }

    private fun section(text: String, open: Boolean, click: () -> Unit) =
        TextView(this).apply {
            this.text = text + "    " + if (open) "▴" else "▾"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = round(c(R.color.bridgefs_input_surface), dp(12))
            setOnClickListener { click() }
        }

    private fun info(name: String, value: String) = card().apply {
        addView(label(name))
        addView(value(value))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = round(c(R.color.bridgefs_input_surface), dp(14))
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTextColor(c(R.color.bridgefs_text_secondary))
    }

    private fun value(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(c(R.color.bridgefs_text_secondary))
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun syncProjectInput() {
        val input = projectInput ?: return
        val prefix = pendingTaskMentions.joinToString(" ")
        val current = input.text.toString()
        val cleaned = current.replace(Regex("""(@[^ ]+\s*)+"""), "").trimStart()
        input.setText(if (prefix.isBlank()) cleaned else prefix + " " + cleaned)
        input.setSelection(input.text.length)
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun c(id: Int) = resources.getColor(id, theme)
    private fun round(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }
}
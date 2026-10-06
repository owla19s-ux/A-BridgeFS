package com.abridgefs.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.drawable.GradientDrawable
import java.io.File

class SettingsCategoryActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("bridgefs", 0) }
    private val category by lazy { intent.getStringExtra("category") ?: "系统" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = resources.getColor(R.color.bridgefs_surface)
        window.navigationBarColor = resources.getColor(R.color.bridgefs_surface)
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        setContentView(buildPage())
    }

    private fun buildPage(): View {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
            setBackgroundColor(resources.getColor(R.color.bridgefs_surface))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "‹"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(52)))
        header.addView(TextView(this).apply {
            text = category
            textSize = 21f
            setTypeface(null, 1)
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        box.addView(header)

        when (category) {
            "执行与权限" -> buildExecution(box)
            "指令" -> buildInstruction(box)
            "文件与目录" -> buildFiles(box)
            "通知" -> buildNotice(box)
            "外观" -> buildAppearance(box)
            "系统" -> buildSystem(box)
            "日志与诊断" -> buildLogs(box)
            else -> buildSystem(box)
        }
        scroll.addView(box)
        return scroll
    }

    private fun buildExecution(box: LinearLayout) {
        val autoExecute = CheckBox(this).apply {
            text = "允许 AI 回复自动触发 BridgeFS"
            isChecked = prefs.getBoolean("ai_auto_bridgefs_enabled", true)
        }
        box.addView(autoExecute, LinearLayout.LayoutParams(-1, dp(48)))
        box.addView(TextView(this).apply {
            text = "关闭后，AI 回复中的 [bridgefs] 指令不会自动执行；仍可正常对话。"
            textSize = 12f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(dp(4), 0, dp(4), dp(10))
        })

        label(box, "AI 单次执行上限")
        val limit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("command_limit", 3).toString())
            hint = "1–20"
        }
        box.addView(limit, fieldParams())
        val labels = mapOf(\n    FileAction.LIST to "查看目录",\n    FileAction.READ to "读取文件",\n    FileAction.WRITE to "创建/写入文件",\n    FileAction.EDIT to "修改文件",\n    FileAction.COMMIT to "提交到 GitHub"\n)
        FileAction.values().forEachIndexed { index, action ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this@SettingsCategoryActivity).apply {
                text = labels[action] ?: action.name
                textSize = 14f
                setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            }, LinearLayout.LayoutParams(0, dp(50), 1f))
            val spinner = Spinner(this@SettingsCategoryActivity)
            spinner.adapter = ArrayAdapter(this@SettingsCategoryActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("允许", "需确认", "禁止"))
            spinner.setSelection(when (prefs.getString("perm_" + action.name, if (action == FileAction.LIST || action == FileAction.READ) "allow" else "confirm")) {
                "confirm" -> 1
                "deny" -> 2
                else -> 0
            })
            row.addView(spinner, LinearLayout.LayoutParams(dp(104), dp(50)))
            row.tag = action
            box.addView(row)
        }
        box.addView(actionButton("保存执行权限") {
            prefs.edit()
                .putBoolean("ai_auto_bridgefs_enabled", autoExecute.isChecked)
                .putInt("command_limit", limit.text.toString().toIntOrNull()?.coerceIn(1, 20) ?: 3)
                .apply()
            for (i in 0 until box.childCount) {
                val row = box.getChildAt(i) as? LinearLayout ?: continue
                val action = row.tag as? FileAction ?: continue
                val spinner = row.getChildAt(1) as? Spinner ?: continue
                val value = when (spinner.selectedItemPosition) { 1 -> "confirm"; 2 -> "deny"; else -> "allow" }
                prefs.edit().putString("perm_" + action.name, value).apply()
            }
            Toast.makeText(this, "执行权限已保存", Toast.LENGTH_SHORT).show()
        })
    }

    private fun buildInstruction(box: LinearLayout) {
        label(box, "A-BridgeFS 指令规范 " + BridgeCommandSpec.version)
        val text = TextView(this).apply {
            text = BridgeCommandSpec.documentation
            textSize = 13f
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(R.color.bridgefs_input_surface, 12)
        }
        box.addView(text, LinearLayout.LayoutParams(-1, -2))
        box.addView(actionButton("复制全部指令规范") {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("A-BridgeFS指令规范", text.text))
            Toast.makeText(this, "指令规范已复制", Toast.LENGTH_SHORT).show()
        })
    }

    private fun buildFiles(box: LinearLayout) {
        label(box, "工作区目录")
        box.addView(TextView(this).apply {
            text = "现在的工作目录属于具体工作区，不再由这里设置。请返回“工作区”，在“工作目录”卡片中选择目录。"
            textSize = 14f
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(R.color.bridgefs_input_surface, 12)
        })
        box.addView(actionButton("返回工作区") { finish() })
        box.addView(TextView(this).apply {
            text = "旧版 root_path 仍保留兼容读取，但新功能以工作区目录为准。"
            textSize = 12f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(dp(4), dp(10), dp(4), dp(10))
        })
    }

    private fun buildNotice(box: LinearLayout) {
        val pending = CheckBox(this).apply {
            text = "执行完成后保留最新回执通知"
            isChecked = prefs.getBoolean("receipt_notification", true)
        }
        box.addView(pending, LinearLayout.LayoutParams(-1, dp(48)))
        box.addView(actionButton("保存通知设置") {
            prefs.edit().putBoolean("receipt_notification", pending.isChecked).apply()
            Toast.makeText(this, "通知设置已保存", Toast.LENGTH_SHORT).show()
        })
    }

    private fun buildAppearance(box: LinearLayout) {
        label(box, "外观")
        box.addView(TextView(this).apply {
            text = "当前 V0.1 使用系统默认浅色界面。主题扩展预留在这里。"
            textSize = 14f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(dp(4), dp(8), dp(4), dp(16))
        })
    }

    private fun buildSystem(box: LinearLayout) {
        box.addView(actionButton("悬浮窗权限") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            } else Toast.makeText(this, "悬浮窗权限已开启", Toast.LENGTH_SHORT).show()
        })
        val auto = CheckBox(this).apply {
            text = "自动显示悬浮球"
            isChecked = prefs.getBoolean("auto_show_overlay", true)
        }
        box.addView(auto, LinearLayout.LayoutParams(-1, dp(48)))
        box.addView(actionButton("保存系统设置") {
            prefs.edit().putBoolean("auto_show_overlay", auto.isChecked).apply()
            Toast.makeText(this, "系统设置已保存", Toast.LENGTH_SHORT).show()
        })
        box.addView(TextView(this).apply {
            text = "ColorOS 可能还需要允许后台运行和自启动。"
            textSize = 12f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(dp(4), dp(10), dp(4), dp(10))
        })
    }

    private fun buildLogs(box: LinearLayout) {
        label(box, "日志保留")
        box.addView(TextView(this).apply {
            text = "运行日志：runtime / collaboration / execution / api / github\n" +
                "日志目录：" + File(filesDir, "logs").absolutePath + "\n" +
                "崩溃日志：" + File(filesDir, "crash").absolutePath + "\n" +
                "日志按日期保存，不自动发送给 AI。\n协作日志与执行日志已分离。"
            textSize = 13f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(dp(4), dp(8), dp(4), dp(18))
        })
    }

    private fun label(box: LinearLayout, text: String) {
        box.addView(TextView(this).apply {
            this.text = text
            textSize = 13f
            setTypeface(null, 1)
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(dp(4), dp(14), dp(4), dp(6))
        })
    }

    private fun fieldParams() = LinearLayout.LayoutParams(-1, dp(50))

    private fun actionButton(text: String, action: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(resources.getColor(R.color.bridgefs_button_text))
        background = GradientDrawable().apply {
            setColor(resources.getColor(R.color.bridgefs_button_bg))
            cornerRadius = dp(10).toFloat()
        }
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) }
    }

    private fun rounded(colorRes: Int, radius: Int) = GradientDrawable().apply {
        setColor(resources.getColor(colorRes))
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

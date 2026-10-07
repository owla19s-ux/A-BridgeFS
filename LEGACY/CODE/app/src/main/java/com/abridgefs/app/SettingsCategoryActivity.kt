package com.abridgefs.app

import android.app.Activity
import android.os.Bundle
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
            "通知" -> buildNotice(box)
            "外观" -> buildAppearance(box)
            "日志与诊断" -> buildLogs(box)
            else -> buildRemovedNotice(box)
        }
        scroll.addView(box)
        return scroll
    }

    private fun buildRemovedNotice(box: LinearLayout) {
        box.addView(TextView(this).apply {
            text = "本地文件执行、BridgeFS 指令、悬浮窗和相关权限已从当前 APS 架构移除。"
            textSize = 14f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(dp(4), dp(12), dp(4), dp(16))
        })
    }

    private fun buildNotice(box: LinearLayout) {
        val pending = CheckBox(this).apply {
            text = "保留通知设置"
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
            text = "当前 V0.1 使用系统默认浅色界面。"
            textSize = 14f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(dp(4), dp(8), dp(4), dp(16))
        })
    }

    private fun buildLogs(box: LinearLayout) {
        label(box, "日志保留")
        box.addView(TextView(this).apply {
            text = "运行日志目录：" + File(filesDir, "logs").absolutePath +
                "\n崩溃日志目录：" + File(filesDir, "crash").absolutePath +
                "\n日志按日期保存，不自动发送给 AI。"
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

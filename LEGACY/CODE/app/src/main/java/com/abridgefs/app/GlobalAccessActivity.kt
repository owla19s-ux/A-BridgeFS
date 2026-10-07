package com.abridgefs.app

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.drawable.GradientDrawable

class GlobalAccessActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = resources.getColor(R.color.bridgefs_surface)
        window.navigationBarColor = resources.getColor(R.color.bridgefs_surface)
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
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
            text = "连接与访问"
            textSize = 21f
            setTypeface(null, 1)
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        box.addView(header)

        box.addView(section("全局访问"))
        box.addView(accessRow(
            "API",
            "允许 A-BridgeFS 使用已配置的外部 AI API。关闭后，对话、API 测试和协作 API 都不会发起请求。",
            AccessPolicy.isApiEnabled(this)
        ) { enabled ->
            AccessPolicy.setApiEnabled(this, enabled)
        })

        box.addView(accessRow(
            "GitHub",
            "允许 A-BridgeFS 使用已连接的 GitHub。关闭后，普通对话和工作区都不会读取或修改 GitHub。",
            AccessPolicy.isGithubEnabled(this)
        ) { enabled ->
            AccessPolicy.setGithubEnabled(this, enabled)
        })

        box.addView(TextView(this).apply {
            text = "规则：全局开关只决定“能不能访问”。GitHub 账号连接、普通对话 Repository、工作区 Repository / Branch 分别独立管理。"
            textSize = 13f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(dp(4), dp(18), dp(4), dp(8))
        })

        box.addView(section("当前状态"))
        box.addView(statusText())

        scroll.addView(box)
        return scroll
    }

    private fun section(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTypeface(null, 1)
        setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
        setPadding(dp(4), dp(14), dp(4), dp(6))
    }

    private fun accessRow(
        title: String,
        summary: String,
        enabled: Boolean,
        onChanged: (Boolean) -> Unit
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(8), dp(8), dp(8))
        background = rounded(resources.getColor(R.color.bridgefs_input_surface), dp(12))

        val texts = LinearLayout(this@GlobalAccessActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@GlobalAccessActivity).apply {
                text = title
                textSize = 15f
                setTypeface(null, 1)
                setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            })
            addView(TextView(this@GlobalAccessActivity).apply {
                text = summary
                textSize = 12f
                setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
                setPadding(0, dp(4), 0, 0)
            })
        }
        addView(texts, LinearLayout.LayoutParams(0, dp(72), 1f))

        val toggle = Switch(this@GlobalAccessActivity).apply {
            isChecked = enabled
            setOnCheckedChangeListener { _, checked -> onChanged(checked) }
        }
        addView(toggle, LinearLayout.LayoutParams(dp(64), dp(52)))
        layoutParams = LinearLayout.LayoutParams(-1, dp(88)).apply { bottomMargin = dp(10) }
    }

    private fun statusText() = TextView(this).apply {
        val apiEnabled = AccessPolicy.isApiEnabled(this@GlobalAccessActivity)
        val githubEnabled = AccessPolicy.isGithubEnabled(this@GlobalAccessActivity)
        val githubAuth = GitHubTokenStore(this@GlobalAccessActivity).state()
        val githubConfig = GitHubConversationConfigStore(this@GlobalAccessActivity).state()
        text = "API：" + (if (apiEnabled) "允许访问" else "已关闭") +
            "\nGitHub：" + (if (githubEnabled) "允许访问" else "已关闭") +
            "\nGitHub 账号：" + (if (!githubAuth.accessToken.isNullOrBlank()) "已连接" else "未连接") +
            "\n普通对话 Repository：" + (githubConfig.repository ?: "未选择") +
            "\n普通对话 Branch：" + (githubConfig.branch ?: "默认分支")
        textSize = 13f
        setTextColor(resources.getColor(R.color.bridgefs_text_primary))
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = rounded(resources.getColor(R.color.bridgefs_input_surface), dp(12))
    }

    private fun rounded(fill: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius.toFloat()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

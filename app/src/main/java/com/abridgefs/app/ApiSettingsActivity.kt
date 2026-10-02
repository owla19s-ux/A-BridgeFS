package com.abridgefs.app

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/** Dedicated API configuration page. MainActivity should only provide the entry point. */
class ApiSettingsActivity : Activity() {
    private val secrets by lazy { ApiSecretStore(this) }
    private val prefs by lazy { getSharedPreferences("bridgefs", 0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = resources.getColor(R.color.bridgefs_surface)
        window.navigationBarColor = resources.getColor(R.color.bridgefs_surface)
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.bridgefs_surface))
            setPadding(dp(16), dp(12), dp(16), dp(20))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(52)))
        header.addView(TextView(this).apply {
            text = "API 与模型"
            textSize = 21f
            setTypeface(null, 1)
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(header)

        root.addView(sectionLabel("当前 API"))
        val provider = EditText(this).apply {
            hint = "API 名称（例如 DeepSeek / OpenAI）"
            setText(prefs.getString("api_provider", ""))
            textSize = 14f
        }
        root.addView(provider, fieldParams())

        root.addView(sectionLabel("API 地址"))
        val baseUrl = EditText(this).apply {
            hint = "https://.../v1"
            setText(prefs.getString("api_base_url", ""))
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(baseUrl, fieldParams())

        root.addView(sectionLabel("API Key"))
        val key = EditText(this).apply {
            hint = "输入 API Key"
            setText(secrets.getNamed("legacy") ?: prefs.getString("api_key", "").orEmpty())
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(key, fieldParams())

        root.addView(sectionLabel("模型"))
        val model = EditText(this).apply {
            hint = "模型名称"
            setText(prefs.getString("api_model", ""))
            textSize = 14f
        }
        root.addView(model, fieldParams())

        val status = TextView(this).apply {
            text = "连接状态：未测试"
            textSize = 13f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(dp(4), dp(10), dp(4), dp(10))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(42)))

        val test = actionButton("测试连接") {
            if (!AccessPolicy.isApiEnabled(this)) {
                status.text = "连接状态：API 全局访问已关闭"
                Toast.makeText(this, "请先在「连接与访问」中开启 API", Toast.LENGTH_SHORT).show()
                return@actionButton
            }
            val url = baseUrl.text.toString().trim()
            val modelName = model.text.toString().trim()
            if (url.isBlank() || modelName.isBlank()) {
                status.text = "连接状态：请先填写 API 地址和模型"
                return@actionButton
            }
            status.text = "连接状态：测试中…"
            Thread {
                runCatching {
                    AppLogger.log(this, "API_TEST_START", "baseUrl=$url model=$modelName")
                    val result = BridgeApiClient(BridgeApiConfig(url, key.text.toString(), modelName)).testConnection()
                    runOnUiThread { status.text = "连接状态：成功（$result）" }
                    AppLogger.log(this, "API_TEST_RESULT", "success=$result")
                }.onFailure { e ->
                    val reason = e.message ?: e::class.simpleName ?: "未知错误"
                    runOnUiThread { status.text = "连接状态：失败\n$reason" }
                    AppLogger.log(this, "API_TEST_RESULT", "failure=$reason")
                }
            }.start()
        }
        root.addView(test, LinearLayout.LayoutParams(-1, dp(46)))

        root.addView(actionButton("保存 API 设置") {
            prefs.edit()
                .putString("api_provider", provider.text.toString().trim())
                .putString("api_base_url", baseUrl.text.toString().trim())
                
                .putString("api_model", model.text.toString().trim())
                .apply()
            secrets.putNamed("legacy", key.text.toString())
            prefs.edit().remove("api_key").apply()
            Toast.makeText(this, "API 设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })


        root.addView(sectionLabel("AI 协作"))
        root.addView(TextView(this).apply {
            text = "API Profile 只负责连接资源。AI 协作参与者请回到「工作区」，从已保存的 API Profile 中选择 AI A 与 AI B。这里不再保存固定的 Decision AI / Worker API。"
            textSize = 13f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(dp(4), dp(6), dp(4), dp(10))
        })
        root.addView(actionButton("返回工作区选择协作 AI") {
            finish()
        }, LinearLayout.LayoutParams(-1, dp(46)))

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTypeface(null, 1)
        setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
        setPadding(dp(4), dp(14), dp(4), dp(6))
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
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

package com.abridgefs.app

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.drawable.GradientDrawable

/** Dedicated dual-AI API configuration page. Both endpoints are OpenAI-compatible. */
class ApiSettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("bridgefs", 0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = resources.getColor(R.color.bridgefs_surface)
        window.navigationBarColor = resources.getColor(R.color.bridgefs_surface)
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        migrateLegacyApiIfNeeded()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.bridgefs_surface))
            setPadding(dp(16), dp(12), dp(16), dp(20))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "‹"; textSize = 32f; gravity = Gravity.CENTER
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(52)))
        header.addView(TextView(this).apply {
            text = "AI API"; textSize = 21f; setTypeface(null, 1)
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(header)

        root.addView(sectionLabel("Decision AI"))
        val decision = addConfigFields(root, "decision")
        root.addView(sectionLabel("Worker AI"))
        val worker = addConfigFields(root, "worker")

        root.addView(actionButton("保存 API 设置") {
            saveConfig("decision", decision)
            saveConfig("worker", worker)
            Toast.makeText(this, "双 AI API 设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun migrateLegacyApiIfNeeded() {
        if (prefs.getString("decision_api_base_url", "").orEmpty().isNotBlank()) return
        val legacyBase = prefs.getString("api_base_url", "").orEmpty()
        val legacyModel = prefs.getString("api_model", "").orEmpty()
        if (legacyBase.isBlank() && legacyModel.isBlank()) return
        prefs.edit()
            .putString("decision_api_provider", prefs.getString("api_provider", "").orEmpty())
            .putString("decision_api_base_url", legacyBase)
            .putString("decision_api_key", prefs.getString("api_key", "").orEmpty())
            .putString("decision_api_model", legacyModel)
            .apply()
    }

    private data class ConfigFields(
        val provider: EditText, val baseUrl: EditText, val key: EditText,
        val model: EditText, val status: TextView
    )

    private fun addConfigFields(root: LinearLayout, prefix: String): ConfigFields {
        val provider = EditText(this).apply {
            hint = "Provider 名称"
            setText(prefs.getString("${prefix}_api_provider", ""))
            textSize = 14f
        }
        root.addView(provider, fieldParams())

        val baseUrl = EditText(this).apply {
            hint = "Base URL（OpenAI-compatible）"
            setText(prefs.getString("${prefix}_api_base_url", ""))
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(baseUrl, fieldParams())

        val key = EditText(this).apply {
            hint = "API Key"
            setText(prefs.getString("${prefix}_api_key", ""))
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(key, fieldParams())

        val model = EditText(this).apply {
            hint = "Model"
            setText(prefs.getString("${prefix}_api_model", ""))
            textSize = 14f
        }
        root.addView(model, fieldParams())

        val status = TextView(this).apply {
            text = "连接状态：未测试"; textSize = 13f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(38)))

        root.addView(actionButton("测试连接") {
            val url = baseUrl.text.toString().trim()
            val modelName = model.text.toString().trim()
            if (url.isBlank() || modelName.isBlank()) {
                status.text = "连接状态：请先填写 Base URL 和 Model"
                return@actionButton
            }
            status.text = "连接状态：测试中…"
            Thread {
                runCatching {
                    AppLogger.log(this, "API_TEST_START", "role=${prefix} baseUrl=$url model=$modelName")
                    val result = BridgeApiClient(BridgeApiConfig(url, key.text.toString(), modelName)).testConnection()
                    runOnUiThread { status.text = "连接状态：成功（$result）" }
                    AppLogger.log(this, "API_TEST_RESULT", "role=${prefix} success=$result")
                }.onFailure { e ->
                    val reason = e.message ?: e::class.simpleName ?: "未知错误"
                    runOnUiThread { status.text = "连接状态：失败\n$reason" }
                    AppLogger.log(this, "API_TEST_RESULT", "role=${prefix} failure=$reason")
                }
            }.start()
        }, LinearLayout.LayoutParams(-1, dp(42)))

        return ConfigFields(provider, baseUrl, key, model, status)
    }

    private fun saveConfig(prefix: String, fields: ConfigFields) {
        prefs.edit()
            .putString("${prefix}_api_provider", fields.provider.text.toString().trim())
            .putString("${prefix}_api_base_url", fields.baseUrl.text.toString().trim())
            .putString("${prefix}_api_key", fields.key.text.toString())
            .putString("${prefix}_api_model", fields.model.text.toString().trim())
            .apply()
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text; textSize = 15f; setTypeface(null, 1)
        setTextColor(resources.getColor(R.color.bridgefs_text_primary))
        setPadding(dp(4), dp(14), dp(4), dp(6))
    }
    private fun fieldParams() = LinearLayout.LayoutParams(-1, dp(50))
    private fun actionButton(text: String, action: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 14f; gravity = Gravity.CENTER
        setTextColor(resources.getColor(R.color.bridgefs_button_text))
        background = GradientDrawable().apply {
            setColor(resources.getColor(R.color.bridgefs_button_bg)); cornerRadius = dp(10).toFloat()
        }
        setOnClickListener { action() }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

package com.abridgefs.app

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.abridgefs.app.ai.AIProfile
import com.abridgefs.app.ai.AIProfileStore
import com.abridgefs.app.ai.OpenAICompatibleConnector
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

/** Global AI/API profile settings. Model catalog retrieval is explicit and never silently changes selection. */
class AISettingsActivity : AppCompatActivity() {
    private lateinit var profileStore: AIProfileStore
    private lateinit var nameField: EditText
    private lateinit var baseUrlField: EditText
    private lateinit var keyField: EditText
    private lateinit var modelField: AutoCompleteTextView
    private lateinit var status: TextView
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileStore = AIProfileStore(this)
        setContentView(buildContent())
    }

    private fun buildContent(): ScrollView {
        val saved = profileStore.load()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }
        root.addView(TextView(this).apply {
            text = "AI / API 连接"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "支持 OpenAI-compatible API。API Key 使用 Android Keystore 加密保存。"
            textSize = 14f
            setPadding(0, dp(6), 0, dp(12))
        })

        nameField = field("连接名称", saved?.name ?: "Default API")
        root.addView(label("连接名称"))
        root.addView(nameField, fieldParams())

        baseUrlField = field("API Base URL，例如 https://api.example.com/v1", saved?.baseUrl.orEmpty()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(label("API 地址"))
        root.addView(baseUrlField, fieldParams())

        keyField = field("API Key", saved?.apiKey.orEmpty()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(label("API Key"))
        root.addView(keyField, fieldParams())

        modelField = AutoCompleteTextView(this).apply {
            hint = "模型 ID（可手动输入或获取列表）"
            setText(saved?.model.orEmpty(), false)
            threshold = 1
        }
        root.addView(label("模型"))
        root.addView(modelField, fieldParams())

        root.addView(Button(this).apply {
            text = "获取模型列表 / 测试 API"
            setOnClickListener { fetchModels() }
        }, buttonParams())
        status = TextView(this).apply {
            text = if (saved == null) "状态：尚未配置" else "状态：已保存配置；尚未测试连接"
            textSize = 14f
            setPadding(0, dp(10), 0, dp(10))
        }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "保存连接"
            setOnClickListener { saveProfile() }
        }, buttonParams())
        root.addView(Button(this).apply {
            text = "删除 API 配置"
            setOnClickListener {
                AlertDialog.Builder(this@AISettingsActivity)
                    .setTitle("删除 API 配置")
                    .setMessage("删除后需要重新输入 API 地址、密钥和模型。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除") { _, _ ->
                        profileStore.clear()
                        Toast.makeText(this@AISettingsActivity, "API 配置已删除", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .show()
            }
        }, buttonParams())
        root.addView(Button(this).apply {
            text = "返回对话"
            setOnClickListener { finish() }
        }, buttonParams())
        return ScrollView(this).apply { addView(root) }
    }

    private fun fetchModels() {
        val profile = buildProfileOrNull() ?: return
        status.text = "状态：正在请求模型列表……"
        executor.execute {
            try {
                val models = runBlocking { OpenAICompatibleConnector(profile).listModels() }
                runOnUiThread {
                    if (models.isEmpty()) {
                        status.text = "连接成功，但服务没有返回模型目录；可以手动填写模型 ID。"
                    } else {
                        modelField.setAdapter(ArrayAdapter(
                            this,
                            android.R.layout.simple_dropdown_item_1line,
                            models
                        ))
                        status.text = "连接成功，获取到 ${models.size} 个模型。请选择模型后保存。"
                        modelField.showDropDown()
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    status.text = "连接失败：${error.message ?: error::class.simpleName ?: "未知错误"}"
                }
            }
        }
    }

    private fun saveProfile() {
        val profile = buildProfileOrNull() ?: return
        try {
            profileStore.save(profile)
            status.text = "状态：配置已加密保存；模型：${profile.model}"
            Toast.makeText(this, "API 配置已保存", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            status.text = "保存失败：${error.message ?: "未知错误"}"
        }
    }

    private fun buildProfileOrNull(): AIProfile? {
        val name = nameField.text.toString().trim()
        val baseUrl = baseUrlField.text.toString().trim()
        val key = keyField.text.toString()
        val model = modelField.text.toString().trim()
        return try {
            AIProfile(name = name, baseUrl = baseUrl, model = model, apiKey = key)
        } catch (error: IllegalArgumentException) {
            status.text = "请检查配置：${error.message}"
            null
        }
    }

    private fun field(hintText: String, value: String) = EditText(this).apply {
        hint = hintText
        setText(value)
        textSize = 15f
        maxLines = 1
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun fieldParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
    )

    private fun buttonParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
    ).apply { topMargin = dp(8) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}

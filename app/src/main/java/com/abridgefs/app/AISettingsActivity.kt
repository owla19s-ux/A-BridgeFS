package com.abridgefs.app

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
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
import java.util.UUID
import java.util.concurrent.Executors

/** Global AI/API profile settings. Model catalog retrieval is explicit and never silently changes selection. */
class AISettingsActivity : AppCompatActivity() {
    private lateinit var profileStore: AIProfileStore
    private var selectedProfileId: String? = null
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
        selectedProfileId = saved?.id
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
            text = "获取模型列表"
            setOnClickListener { fetchModels() }
        }, buttonParams())
        root.addView(Button(this).apply {
            text = "测试聊天请求"
            setOnClickListener { testChatConnection() }
        }, buttonParams())
        status = TextView(this).apply {
            text = if (saved == null) "状态：尚未配置" else "状态：已保存配置；尚未测试连接"
            textSize = 14f
            setPadding(0, dp(10), 0, dp(10))
        }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "查看运行诊断日志"
            setOnClickListener { showDiagnostics() }
        }, buttonParams())

        root.addView(Button(this).apply {
            text = "连接列表 / 切换连接"
            setOnClickListener { showProfiles() }
        }, buttonParams())
        root.addView(Button(this).apply {
            text = "新建连接"
            setOnClickListener { startNewProfile() }
        }, buttonParams())
        root.addView(Button(this).apply {
            text = "设为默认连接"
            setOnClickListener { setCurrentAsDefault() }
        }, buttonParams())
        root.addView(Button(this).apply {
            text = "保存连接"
            setOnClickListener { saveProfile() }
        }, buttonParams())
        root.addView(Button(this).apply {
            text = "删除当前连接"
            setOnClickListener { confirmDeleteCurrentProfile() }
        }, buttonParams())
        root.addView(Button(this).apply {
            text = "返回对话"
            setOnClickListener { finish() }
        }, buttonParams())
        return ScrollView(this).apply { addView(root) }
    }

    private fun showDiagnostics() {
        val logs = RuntimeDiagnostics.readRecentLogs(this)
        val text = TextView(this).apply {
            text = logs
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val scroll = ScrollView(this).apply {
            addView(text)
        }
        AlertDialog.Builder(this)
            .setTitle("运行诊断日志")
            .setView(scroll)
            .setNeutralButton("复制日志") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("APS 诊断日志", logs))
                Toast.makeText(this, "诊断日志已复制", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun testChatConnection() {
        val profile = buildProfileOrNull() ?: return
        status.text = "状态：正在发送实际聊天测试请求……"
        executor.execute {
            try {
                val reply = runBlocking { OpenAICompatibleConnector(profile).testChatCompletion() }
                runOnUiThread {
                    status.text = "聊天测试成功：模型已返回响应（${reply.take(80)}）"
                }
            } catch (error: Exception) {
                runOnUiThread {
                    status.text = "聊天测试失败：${error.message ?: error::class.simpleName ?: "未知错误"}"
                }
            }
        }
    }
    private fun fetchModels() {
        val profile = buildProfileOrNull(requireModel = false) ?: return
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
        val id = selectedProfileId ?: UUID.randomUUID().toString()
        val profile = buildProfileOrNull(id = id) ?: return
        try {
            val makeDefault = profileStore.loadAll().isEmpty()
            profileStore.save(profile, makeDefault = makeDefault)
            selectedProfileId = profile.id
            status.text = "状态：连接已加密保存；模型：${profile.model}"
            Toast.makeText(this, "API 连接已保存", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            status.text = "保存失败：${error.message ?: "未知错误"}"
        }
    }

    private fun showProfiles() {
        val profiles = profileStore.loadAll()
        if (profiles.isEmpty()) {
            status.text = "当前没有已保存的 API 连接。"
            return
        }
        val defaultId = profileStore.defaultProfileId()
        val labels = profiles.map { profile ->
            (if (profile.id == defaultId) "★ " else "") +
                profile.name + " · " + profile.model
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择 API 连接")
            .setItems(labels) { _, index ->
                val profile = profiles[index]
                selectedProfileId = profile.id
                populateFields(profile)
                status.text = if (profile.id == defaultId) {
                    "当前连接：默认连接"
                } else {
                    "已选择连接；保存修改不会自动更改默认连接"
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun populateFields(profile: AIProfile) {
        nameField.setText(profile.name)
        baseUrlField.setText(profile.baseUrl)
        keyField.setText(profile.apiKey)
        modelField.setText(profile.model, false)
    }

    private fun startNewProfile() {
        selectedProfileId = UUID.randomUUID().toString()
        nameField.setText("新 API 连接")
        baseUrlField.setText("")
        keyField.setText("")
        modelField.setText("", false)
        status.text = "新建连接尚未保存。填写地址、密钥和模型后保存。"
    }

    private fun setCurrentAsDefault() {
        val id = selectedProfileId
        if (id == null || profileStore.load(id) == null) {
            status.text = "请先选择并保存一个 API 连接。"
            return
        }
        try {
            profileStore.setDefault(id)
            status.text = "已设为默认 API 连接。普通对话将使用该连接。"
        } catch (error: Exception) {
            status.text = "设置默认连接失败：${error.message ?: "未知错误"}"
        }
    }

    private fun confirmDeleteCurrentProfile() {
        val id = selectedProfileId
        if (id == null || profileStore.load(id) == null) {
            status.text = "当前连接尚未保存，无需删除。"
            return
        }
        AlertDialog.Builder(this)
            .setTitle("删除当前连接")
            .setMessage("删除后，该连接的加密密钥和配置将从本机移除。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                try {
                    profileStore.delete(id)
                    val next = profileStore.load()
                    selectedProfileId = next?.id
                    if (next != null) populateFields(next) else {
                        nameField.setText("")
                        baseUrlField.setText("")
                        keyField.setText("")
                        modelField.setText("", false)
                    }
                    status.text = if (next == null) "连接已删除；当前没有剩余连接。" else
                        "连接已删除。已加载默认连接：${next.name}"
                } catch (error: Exception) {
                    status.text = "删除失败：${error.message ?: "未知错误"}"
                }
            }
            .show()
    }

    private fun buildProfileOrNull(
        requireModel: Boolean = true,
        id: String = selectedProfileId ?: AIProfile.DEFAULT_ID
    ): AIProfile? {
        val name = nameField.text.toString().trim()
        val baseUrl = baseUrlField.text.toString().trim()
        val key = keyField.text.toString()
        val enteredModel = modelField.text.toString().trim()
        val model = enteredModel.ifBlank { if (requireModel) "" else "model-discovery" }
        return try {
            AIProfile(id = id, name = name, baseUrl = baseUrl, model = model, apiKey = key)
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

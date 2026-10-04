package com.abridgefs.app

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.drawable.GradientDrawable
import java.util.UUID

/** API profiles and the global API access switch. */
class ApiSettingsActivity : Activity() {
    private val store by lazy { ApiProfileStore(this) }
    private lateinit var rootView: LinearLayout
    private lateinit var profileBox: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = resources.getColor(R.color.bridgefs_surface)
        window.navigationBarColor = resources.getColor(R.color.bridgefs_surface)
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        setContentView(buildPage())
    }

    override fun onResume() {
        super.onResume()
        if (::profileBox.isInitialized) rebuildProfiles()
    }

    private fun buildPage(): View {
        rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            setBackgroundColor(resources.getColor(R.color.bridgefs_surface))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "‹"; textSize = 32f; gravity = Gravity.CENTER
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(52)))
        header.addView(TextView(this).apply {
            text = "AI 与 API"; textSize = 21f; setTypeface(null, 1)
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        rootView.addView(header)

        rootView.addView(Switch(this).apply {
            text = "允许 A-BridgeFS 访问外部 API"
            isChecked = AccessPolicy.isApiEnabled(this@ApiSettingsActivity)
            setOnCheckedChangeListener { _, checked -> AccessPolicy.setApiEnabled(this@ApiSettingsActivity, checked) }
        })
        rootView.addView(sectionLabel("API Profiles"))
        profileBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rootView.addView(profileBox)
        rootView.addView(actionButton("＋ 添加 API") { editProfile(null) }, LinearLayout.LayoutParams(-1, dp(44)))
        status = TextView(this).apply {
            textSize = 12f; setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(dp(4), dp(10), dp(4), dp(4))
        }
        rootView.addView(status)
        rootView.addView(TextView(this).apply {
            text = "API Profile 是普通对话和 AI 协作共用的连接资源。"
            textSize = 12f; setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(dp(4), dp(8), dp(4), dp(10))
        })
        rebuildProfiles()
        return ScrollView(this).apply { addView(rootView) }
    }

    private fun rebuildProfiles() {
        profileBox.removeAllViews()
        val profiles = store.list()
        if (profiles.isEmpty()) {
            profileBox.addView(TextView(this).apply {
                text = "还没有 API Profile"; textSize = 13f
                setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
                setPadding(dp(4), dp(6), dp(4), dp(8))
            })
            status.text = "添加后即可在普通对话或工作区协作中选择。"
            return
        }
        profiles.forEach { profile ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(10), dp(8))
                background = rounded(R.color.bridgefs_input_surface, 12)
            }
            row.addView(TextView(this).apply {
                text = profile.name.ifBlank { "未命名 API" }; textSize = 15f; setTypeface(null, 1)
                setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            })
            row.addView(TextView(this).apply {
                text = profile.model.ifBlank { "未设置模型" } + " · " + profile.baseUrl
                textSize = 12f; setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
                setPadding(0, dp(3), 0, dp(4))
            })
            val actions = LinearLayout(this).apply { gravity = Gravity.END }
            actions.addView(textButton("测试") { testProfile(profile) }, LinearLayout.LayoutParams(dp(64), dp(38)))
            actions.addView(textButton("编辑") { editProfile(profile) }, LinearLayout.LayoutParams(dp(64), dp(38)))
            actions.addView(textButton("删除") { removeProfile(profile) }, LinearLayout.LayoutParams(dp(64), dp(38)))
            row.addView(actions)
            profileBox.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        status.text = "共 " + profiles.size + " 个 API Profile"
    }

    private fun editProfile(old: ApiProfile?) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), 0, dp(4), 0) }
        val name = field("名称", old?.name)
        val avatar = field("头像（文字 / Emoji）", old?.avatar)
        val url = field("API 地址", old?.baseUrl)
        val key = field("API Key", old?.key).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val model = field("模型", old?.model)
        listOf(name, avatar, url, key, model).forEach { box.addView(it) }
        AlertDialog.Builder(this).setTitle(if (old == null) "添加 API" else "编辑 API").setView(box)
            .setPositiveButton("保存") { _, _ ->
                val profile = ApiProfile(old?.id ?: UUID.randomUUID().toString(), name.text.toString().trim(), url.text.toString().trim(), key.text.toString(), model.text.toString().trim(), avatar.text.toString().trim())
                if (profile.name.isBlank() || profile.baseUrl.isBlank() || profile.model.isBlank()) {
                    Toast.makeText(this, "名称、API 地址和模型不能为空", Toast.LENGTH_SHORT).show()
                } else { store.save(profile); rebuildProfiles() }
            }.setNegativeButton("取消", null).show()
    }

    private fun testProfile(profile: ApiProfile) {
        if (!AccessPolicy.isApiEnabled(this)) { Toast.makeText(this, "请先开启 API 全局访问", Toast.LENGTH_SHORT).show(); return }
        status.text = "正在测试：" + profile.name + "…"
        Thread {
            runCatching {
                AppLogger.log(this, AppLogger.Category.API, "API_TEST_START", "profile=" + profile.id + " model=" + profile.model)
                val result = BridgeApiClient(BridgeApiConfig(profile.baseUrl, profile.key, profile.model)).testConnection()
                runOnUiThread { status.text = profile.name + "：连接成功（" + result + "）" }
                AppLogger.log(this, AppLogger.Category.API, "API_TEST_RESULT", "profile=" + profile.id + " success=" + result)
            }.onFailure { e ->
                val reason = e.message ?: e::class.simpleName ?: "未知错误"
                runOnUiThread { status.text = profile.name + "：连接失败\n" + reason }
                AppLogger.log(this, AppLogger.Category.API, "API_TEST_RESULT", "profile=" + profile.id + " failure=" + reason)
            }
        }.start()
    }

    private fun removeProfile(profile: ApiProfile) {
        AlertDialog.Builder(this).setTitle("移除 API")
            .setMessage("确定移除「" + profile.name + "」？使用它的对话会保留，但将失去对应 API 配置。")
            .setPositiveButton("移除") { _, _ -> store.remove(profile.id); rebuildProfiles() }
            .setNegativeButton("取消", null).show()
    }

    private fun field(label: String, value: String?) = EditText(this).apply {
        hint = label; setText(value.orEmpty()); textSize = 14f; setSingleLine(true)
        setPadding(dp(8), dp(8), dp(8), dp(8)); layoutParams = LinearLayout.LayoutParams(-1, dp(50))
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f; setTypeface(null, 1)
        setTextColor(resources.getColor(R.color.bridgefs_text_secondary)); setPadding(dp(4), dp(12), dp(4), dp(6))
    }

    private fun actionButton(text: String, action: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 14f; gravity = Gravity.CENTER
        setTextColor(resources.getColor(R.color.bridgefs_button_text)); background = rounded(R.color.bridgefs_button_bg, 10)
        setOnClickListener { action() }
    }

    private fun textButton(text: String, action: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 12f; gravity = Gravity.CENTER
        setTextColor(resources.getColor(R.color.bridgefs_accent)); setOnClickListener { action() }
    }

    private fun rounded(colorRes: Int, radius: Int) = GradientDrawable().apply {
        setColor(resources.getColor(colorRes)); cornerRadius = dp(radius).toFloat()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
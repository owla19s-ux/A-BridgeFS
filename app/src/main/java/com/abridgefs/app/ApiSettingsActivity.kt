package com.abridgefs.app

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import android.graphics.drawable.GradientDrawable

/** Dedicated API configuration page. MainActivity should only provide the entry point. */
class ApiSettingsActivity : Activity() {
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
        val baseTop = dp(12)

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
            setText(prefs.getString("api_key", ""))
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
                .putString("api_key", key.text.toString())
                .putString("api_model", model.text.toString().trim())
                .apply()
            Toast.makeText(this, "API 设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })

        root.addView(sectionLabel("AI 协作（Decision AI / Worker）"))
        val decisionUrl = EditText(this).apply {
            hint = "Decision AI API 地址"
            setText(prefs.getString("collab_decision_base_url", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(decisionUrl, fieldParams())

        val decisionKey = EditText(this).apply {
            hint = "Decision AI API Key"
            setText(prefs.getString("collab_decision_api_key", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(decisionKey, fieldParams())

        val decisionModel = EditText(this).apply {
            hint = "Decision AI 模型"
            setText(prefs.getString("collab_decision_model", ""))
        }
        root.addView(decisionModel, fieldParams())

        val workerUrl = EditText(this).apply {
            hint = "Worker API 地址"
            setText(prefs.getString("collab_worker_base_url", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(workerUrl, fieldParams())

        val workerKey = EditText(this).apply {
            hint = "Worker API Key"
            setText(prefs.getString("collab_worker_api_key", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(workerKey, fieldParams())

        val workerModel = EditText(this).apply {
            hint = "Worker 模型"
            setText(prefs.getString("collab_worker_model", ""))
        }
        root.addView(workerModel, fieldParams())

        val objective = EditText(this).apply {
            hint = "单轮协作目标，例如：检查项目当前状态并给出下一步施工建议"
            minLines = 2
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        root.addView(objective, LinearLayout.LayoutParams(-1, dp(80)))

        root.addView(actionButton("保存协作 API 设置") {
            prefs.edit()
                .putString("collab_decision_base_url", decisionUrl.text.toString().trim())
                .putString("collab_decision_api_key", decisionKey.text.toString())
                .putString("collab_decision_model", decisionModel.text.toString().trim())
                .putString("collab_worker_base_url", workerUrl.text.toString().trim())
                .putString("collab_worker_api_key", workerKey.text.toString())
                .putString("collab_worker_model", workerModel.text.toString().trim())
                .apply()
            Toast.makeText(this, "协作 API 设置已保存", Toast.LENGTH_SHORT).show()
        })

        val collaborationStatus = TextView(this).apply {
            text = "单轮协作状态：未运行"
            textSize = 13f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(dp(4), dp(10), dp(4), dp(10))
        }
        root.addView(collaborationStatus, LinearLayout.LayoutParams(-1, dp(72)))

        root.addView(actionButton("运行单轮协作") {
            val taskText = objective.text.toString().trim()
            val dUrl = decisionUrl.text.toString().trim()
            val dKey = decisionKey.text.toString()
            val dModel = decisionModel.text.toString().trim()
            val wUrl = workerUrl.text.toString().trim()
            val wKey = workerKey.text.toString()
            val wModel = workerModel.text.toString().trim()

            val missing = buildList {
                if (taskText.isBlank()) add("协作目标")
                if (dUrl.isBlank()) add("Decision AI 地址")
                if (dKey.isBlank()) add("Decision AI API Key")
                if (dModel.isBlank()) add("Decision AI 模型")
                if (wUrl.isBlank()) add("Worker 地址")
                if (wKey.isBlank()) add("Worker API Key")
                if (wModel.isBlank()) add("Worker 模型")
            }
            if (missing.isNotEmpty()) {
                collaborationStatus.text = "单轮协作状态：请填写\n" + missing.joinToString("、")
                return@actionButton
            }

            prefs.edit()
                .putString("collab_decision_base_url", dUrl)
                .putString("collab_decision_api_key", dKey)
                .putString("collab_decision_model", dModel)
                .putString("collab_worker_base_url", wUrl)
                .putString("collab_worker_api_key", wKey)
                .putString("collab_worker_model", wModel)
                .apply()

            collaborationStatus.text = "单轮协作状态：运行中…"
            Thread {
                runCatching {
                    val coordinator = CollaborationCoordinator(this)
                    val taskId = CollaborationProtocol.newTaskId()
                    val task = CollaborationProtocol.task(
                        taskId = taskId,
                        objective = taskText,
                        allowPaths = emptyList(),
                        allowOperations = listOf("read", "analyze"),
                        acceptance = listOf("Worker 返回一条合法协议消息，并由 Decision AI 接收"),
                        selfResolve = listOf("普通分析与格式问题"),
                        mustAsk = listOf("超出当前任务范围的修改")
                    )
                    coordinator.submitTask(task)
                    val messages = coordinator.dispatchOneWorkerRound(
                        workerSystemPrompt = """
                        你是 A-BridgeFS Worker。
                        当前只做一次 Decision AI ↔ Worker v0.1 单轮协作测试，不执行 GitHub 或本地文件修改。
                        你不能自行做最终决定，必须把需要选择的问题交给 Decision AI。
                        你的回复必须且只能是一个合法的 v=0.1 协议 JSON 对象，禁止 Markdown、代码围栏和任何额外文字。
                        本轮 Worker 回复的 type 必须严格为 DECISION_REQUEST；禁止使用 PROPOSAL 或任何协议未定义的 type。
                        from 必须是 worker，to 必须是 decision_ai，task_id 必须与收到的 TASK 完全一致。
                        DECISION_REQUEST 的 payload 必须包含 kind、question、options、recommendation、reason、evidence、blocked_on；options 必须至少包含两个不同候选项，每项包含 id 和 summary。
                        如果任务要求你提出候选答案，就把候选答案放进 options，不要直接给出最终决定。
                        """.trimIndent(),
                        decisionSystemPrompt = "你是 A-BridgeFS Decision AI。严格返回一个合法的 Decision AI ↔ Worker v0.1 协议 JSON。根据 Worker 消息给出当前任务所需的正式决策或状态处理。"
                    )
                    val summary = messages.joinToString("\n\n") { it.type.name + " / " + it.from.name + " → " + it.to.name + "\n" + it.toJson().toString() }
                    runOnUiThread { collaborationStatus.text = "单轮协作状态：成功\n" + summary.take(5000) }
                }.onFailure { e ->
                    val reason = e.message ?: e::class.simpleName ?: "未知错误"
                    runOnUiThread { collaborationStatus.text = "单轮协作状态：失败\n$reason" }
                }
            }.start()
        })

        val scroll = ScrollView(this).apply { addView(root) }

        // Android 15/16 with target SDK 35 uses edge-to-edge by default.
        // Reserve system-bar and IME space without changing the global theme.
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            root.setPadding(dp(16), baseTop + bars.top, dp(16), dp(20))
            insets
        }
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            scroll.setPadding(0, 0, 0, maxOf(bars.bottom, ime.bottom))
            insets
        }
        setContentView(scroll)
        scroll.requestApplyInsets()
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

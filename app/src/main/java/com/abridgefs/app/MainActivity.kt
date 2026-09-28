package com.abridgefs.app

import android.content.*
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var apiInput: EditText
    private lateinit var keyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var rootInput: EditText
    private lateinit var chatInput: EditText
    private lateinit var chatView: TextView
    private val executor = Executors.newSingleThreadExecutor()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val result = intent.getStringExtra("result") ?: ""
            runOnUiThread { chatView.append("\n\nBridgeFS：\n" + result) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }

        apiInput = EditText(this).apply { hint = "API 地址，例如 https://.../v1" }
        keyInput = EditText(this).apply { hint = "API Key"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        modelInput = EditText(this).apply { hint = "模型名称" }
        rootInput = EditText(this).apply { hint = "授权目录"; setText("/storage/emulated/0/A3/A0项目") }
        chatView = TextView(this).apply { setPadding(0,16,0,16) }
        chatInput = EditText(this).apply { hint = "输入消息"; minLines = 3 }

        val save = Button(this).apply {
            text = "保存 API 与授权"
            setOnClickListener {
                getPreferences(MODE_PRIVATE).edit()
                    .putString("baseUrl", apiInput.text.toString().trim())
                    .putString("apiKey", keyInput.text.toString())
                    .putString("model", modelInput.text.toString().trim())
                    .putString("root", rootInput.text.toString().trim())
                    .apply()
                Toast.makeText(this@MainActivity, "已保存", Toast.LENGTH_SHORT).show()
            }
        }
        val send = Button(this).apply { text = "发送"; setOnClickListener { sendChat() } }

        page.addView(apiInput); page.addView(keyInput); page.addView(modelInput)
        page.addView(rootInput); page.addView(save); page.addView(chatView); page.addView(chatInput); page.addView(send)
        setContentView(ScrollView(this).apply { addView(page) })
        loadSaved()
        registerReceiver(receiver, IntentFilter("com.abridgefs.RESULT"), Context.RECEIVER_NOT_EXPORTED)
    }

    private fun loadSaved() {
        val p = getPreferences(MODE_PRIVATE)
        apiInput.setText(p.getString("baseUrl", ""))
        keyInput.setText(p.getString("apiKey", ""))
        modelInput.setText(p.getString("model", ""))
        rootInput.setText(p.getString("root", "/storage/emulated/0/A3/A0项目"))
    }

    private fun sendChat() {
        val message = chatInput.text.toString().trim()
        if (message.isBlank()) return
        val config = ApiConfig(apiInput.text.toString().trim(), keyInput.text.toString(), modelInput.text.toString().trim())
        chatView.append("\n\n你：\n" + message)
        chatInput.text.clear()
        executor.execute {
            try {
                val system = "你是 A-BridgeFS 本地助手。需要操作授权目录时，只能输出 [bridgefs] ... [/bridgefs] 操作块；不要声称已经执行，必须等待真实执行结果。支持 [list]、[read: 文件]、[write: 文件]内容[/write]、[edit: 文件]旧内容====新内容[/edit]。"
                val answer = ApiClient(config).chat(message, system)
                runOnUiThread {
                    chatView.append("\n\nAI：\n" + answer)
                    BridgeRequest.extract(answer)?.let { executeCommands(it) }
                }
            } catch (e: Exception) {
                runOnUiThread { chatView.append("\n\nAPI错误：\n" + (e.message ?: "未知错误")) }
            }
        }
    }

    private fun executeCommands(text: String) {
        val root = rootInput.text.toString().trim()
        val commands = CommandParser.parse(text)
        if (commands.isEmpty()) return
        val auth = Authorization(root, setOf(FileAction.LIST, FileAction.READ, FileAction.WRITE, FileAction.EDIT))
        val blocked = commands.firstOrNull { PermissionPolicy.check(it, auth) != Decision.ALLOW }
        if (blocked != null) {
            chatView.append("\n\nBridgeFS：权限规则未允许，未执行。")
            return
        }
        startForegroundService(Intent(this, BridgeService::class.java)
            .putExtra("root", root).putExtra("command", text))
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        executor.shutdownNow()
        super.onDestroy()
    }
}

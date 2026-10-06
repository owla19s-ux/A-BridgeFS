package com.abridgefs.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("bridgefs", 0) }
    private val store by lazy { BridgeProjectStore(this) }
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private enum class Screen { CHAT, FILES, RECEIPTS, ONBOARDING }
    private var screen = Screen.CHAT
    private var currentPath = File("/storage/emulated/0")
    private var firstResume = true

    private lateinit var drawer: DrawerLayout
    private lateinit var contentHost: FrameLayout
    private lateinit var navChat: TextView
    private lateinit var navFiles: TextView
    private lateinit var navReceipts: TextView
    private lateinit var receiptBadge: TextView

    private var projects = mutableListOf<BridgeProject>()
    private var currentProject: BridgeProject? = null
    private var pendingReceipt: String? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val receipt = org.json.JSONObject().apply {
                put("receiptId", intent.getStringExtra("receiptId").orEmpty())
                put("status", intent.getStringExtra("status") ?: "UNKNOWN")
                put("command", intent.getStringExtra("command") ?: "")
                put("message", intent.getStringExtra("message") ?: "")
                put("time", intent.getLongExtra("time", System.currentTimeMillis()))
                put("projectId", intent.getStringExtra("projectId").orEmpty())
                put("conversationId", intent.getStringExtra("conversationId").orEmpty())
            }
            processPendingReceipt(receipt)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.statusBarColor = resources.getColor(R.color.bridgefs_surface)
        window.navigationBarColor = resources.getColor(R.color.bridgefs_surface)
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        projects = store.load()
        if (projects.isEmpty()) projects += store.newProject("默认项目")
        currentProject = projects.first()
        currentPath = File(prefs.getString("root_path", "/storage/emulated/0") ?: "/storage/emulated/0")
        restorePendingReceipt()

        registerReceiver(receiver, IntentFilter("com.bridgefs.RESULT"), Context.RECEIVER_NOT_EXPORTED)

        screen = if (prefs.getBoolean("first_run_completed", false)) Screen.CHAT else Screen.ONBOARDING
        buildShell()
        render()
        autoStartIfNeeded(screen == Screen.CHAT)
    }

    override fun onResume() {
        super.onResume()
        if (!firstResume) {
            if (screen != Screen.ONBOARDING) render()
            autoStartIfNeeded(false)
        }
        firstResume = false
    }

    override fun onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END)
            return
        }
        if (screen == Screen.FILES) {
            val storage = File("/storage/emulated/0")
            if (normalizedPath(currentPath.absolutePath) != normalizedPath(storage.absolutePath)) {
                currentPath.parentFile?.let { currentPath = it; render() }
                return
            }
        }
        if (screen != Screen.CHAT) {
            screen = Screen.CHAT
            render()
            return
        }
        super.onBackPressed()
    }

    private fun buildShell() {
        drawer = DrawerLayout(this)

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.bridgefs_surface))
        }

        contentHost = FrameLayout(this)
        ViewCompat.setOnApplyWindowInsetsListener(contentHost) { view, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, ime)
            insets
        }
        main.addView(contentHost, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = rounded(resources.getColor(R.color.bridgefs_surface), dp(0))
        }

        navChat = navItem("对话") { screen = Screen.CHAT; render() }
        navFiles = navItem("文件") { screen = Screen.FILES; render() }
        navReceipts = navItem("回执") { screen = Screen.RECEIPTS; render() }

        val receiptBox = FrameLayout(this)
        receiptBox.addView(navReceipts, FrameLayout.LayoutParams(-1, dp(54)))
        receiptBadge = TextView(this).apply {
            text = "!"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(resources.getColor(R.color.bridgefs_accent))
                shape = GradientDrawable.OVAL
            }
            visibility = View.GONE
        }
        receiptBox.addView(receiptBadge, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP or Gravity.RIGHT).apply {
            topMargin = dp(2)
            rightMargin = dp(10)
        })

        nav.addView(navChat, LinearLayout.LayoutParams(0, dp(54), 1f))
        nav.addView(navFiles, LinearLayout.LayoutParams(0, dp(54), 1f))
        nav.addView(receiptBox, LinearLayout.LayoutParams(0, dp(54), 1f))
        main.addView(nav)

        ViewCompat.setOnApplyWindowInsetsListener(main) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        drawer.addView(main, DrawerLayout.LayoutParams(-1, -1))

        val drawerView = buildSettingsDrawer()
        drawer.addView(drawerView, DrawerLayout.LayoutParams(dp(340), -1, GravityCompat.END))

        setContentView(drawer)
    }

    private fun render() {
        if (!::contentHost.isInitialized) return
        contentHost.removeAllViews()
        when (screen) {
            Screen.CHAT -> renderChat()
            Screen.FILES -> renderFiles()
            Screen.RECEIPTS -> renderReceipts()
            Screen.ONBOARDING -> renderOnboarding()
        }
        updateNav()
    }

    private fun restorePendingReceipt() {
        val queue = org.json.JSONArray(prefs.getString("pending_receipts", "[]") ?: "[]")
        for (i in 0 until queue.length()) {
            queue.optJSONObject(i)?.let { processPendingReceipt(it) }
        }
        val legacy = prefs.getString("pending_receipt", null)
        if (!legacy.isNullOrBlank()) {
            runCatching { processPendingReceipt(org.json.JSONObject(legacy)) }
        }
    }

    private fun processPendingReceipt(receipt: org.json.JSONObject) {
        runCatching {
            ProjectContinuationCoordinator.onReceipt(
                context = this,
                projectId = receipt.optString("projectId").ifBlank { null },
                conversationId = receipt.optString("conversationId").ifBlank { null },
                receiptId = receipt.optString("receiptId").ifBlank { null },
                status = receipt.optString("status", "UNKNOWN"),
                command = receipt.optString("command", ""),
                message = receipt.optString("message", ""),
                time = receipt.optLong("time", System.currentTimeMillis())
            )

            val displayReceipt = BridgeReceiptRecord(
                receipt.optString("status", "UNKNOWN"),
                receipt.optString("command", ""),
                receipt.optString("message", ""),
                receipt.optLong("time", System.currentTimeMillis()),
                receipt.optString("receiptId").ifBlank { java.util.UUID.randomUUID().toString() }
            )
            pendingReceipt = formatReceipt(displayReceipt)

            val id = receipt.optString("receiptId")
            val queued = org.json.JSONArray(prefs.getString("pending_receipts", "[]") ?: "[]")
            val remaining = org.json.JSONArray()
            for (i in 0 until queued.length()) {
                val item = queued.optJSONObject(i)
                if (item?.optString("receiptId") != id) remaining.put(item)
            }
            prefs.edit()
                .putString("pending_receipts", remaining.toString())
                .remove("pending_receipt")
                .apply()

            projects = store.load()
            currentProject = currentProject?.id?.let { projectId ->
                projects.firstOrNull { it.id == projectId }
            } ?: projects.firstOrNull()
            render()
        }.onFailure {
            AppLogger.log(this, "EXECUTION", "PENDING_RECEIPT_RECOVERY_FAILED " + (it.message ?: "unknown"))
        }
    }

    private fun updateNav() {
        navChat.setTextColor(if (screen == Screen.CHAT) resources.getColor(R.color.bridgefs_accent) else resources.getColor(R.color.bridgefs_text_secondary))
        navFiles.setTextColor(if (screen == Screen.FILES) resources.getColor(R.color.bridgefs_accent) else resources.getColor(R.color.bridgefs_text_secondary))
        navReceipts.setTextColor(if (screen == Screen.RECEIPTS) resources.getColor(R.color.bridgefs_accent) else resources.getColor(R.color.bridgefs_text_secondary))
        val hasUnread = pendingReceipt != null
        receiptBadge.visibility = if (hasUnread) View.VISIBLE else View.GONE
    }

    private fun renderChat() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(6))
        }

        val apiRow = TextView(this).apply {
            val base = apiLabel()
            text = base
            textSize = 14f
            setTypeface(null, 1)
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(resources.getColor(R.color.bridgefs_input_surface), dp(12))
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ApiSettingsActivity::class.java))
            }
        }
        root.addView(apiRow, LinearLayout.LayoutParams(-1, dp(46)))

        val chatScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val chatColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(10), dp(2), dp(10))
        }

        val project = currentProject
        if (project != null && project.messages.isEmpty()) {
            chatColumn.addView(TextView(this).apply {
                text = "和 AI 直接对话。需要本地操作时，让 AI 使用新版 BridgeFS 指令格式。"
                textSize = 13f
                setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
                setPadding(dp(8), dp(20), dp(8), dp(20))
            })
        }

        project?.messages?.forEach { message ->
            val bubbleBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(8), dp(8), dp(6))
                background = rounded(
                    if (message.role == "user") resources.getColor(R.color.bridgefs_button_bg)
                    else resources.getColor(R.color.bridgefs_input_surface),
                    dp(14)
                )
            }
            val bubble = TextView(this).apply {
                text = message.content
                textSize = 14f
                setTextColor(resources.getColor(R.color.bridgefs_text_primary))
                setPadding(dp(2), dp(2), dp(2), dp(4))
                setTextIsSelectable(true)
            }
            val copy = smallAction("复制") {
                copyToClipboard(if (message.role == "user") "用户消息" else "AI 回复", message.content)
                Toast.makeText(this@MainActivity, "消息已复制", Toast.LENGTH_SHORT).show()
            }
            bubbleBox.addView(bubble, LinearLayout.LayoutParams(-1, -2))
            bubbleBox.addView(copy, LinearLayout.LayoutParams(dp(58), dp(30)).apply {
                gravity = if (message.role == "user") Gravity.END else Gravity.START
            })
            val wrap = FrameLayout(this)
            wrap.addView(bubbleBox, FrameLayout.LayoutParams(-2, -2).apply {
                width = (resources.displayMetrics.widthPixels * 0.82f).toInt()
                gravity = if (message.role == "user") Gravity.END else Gravity.START
            })
            chatColumn.addView(wrap, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(5)
                bottomMargin = dp(5)
            })
        }

        chatScroll.addView(chatColumn)
        root.addView(chatScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }

        val paste = smallAction("粘贴回执") {
            val receipt = pendingReceipt
            if (receipt.isNullOrBlank()) {
                Toast.makeText(this, "当前没有待粘贴回执", Toast.LENGTH_SHORT).show()
            } else {
                chatInputField?.setText(receipt)
                chatInputField?.setSelection(chatInputField?.text?.length ?: 0)
                pendingReceipt = null
                prefs.edit().remove("pending_receipt").apply()
                saveProjects()
                updateNav()
            }
        }

        val expand = smallAction("⛶") { showExpandedEditor() }

        val input = EditText(this).apply {
            hint = "输入消息……"
            textSize = 14f
            minLines = 1
            maxLines = 3
            setSingleLine(false)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(resources.getColor(R.color.bridgefs_input_surface), dp(12))
        }
        chatInputField = input
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                input.postDelayed({
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                }, 120)
            }
        }

        val send = smallAction("发送") { sendChat() }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(paste, LinearLayout.LayoutParams(dp(82), dp(38)))
            addView(smallAction("指令说明") { showInstructionDialog() }, LinearLayout.LayoutParams(dp(82), dp(38)).apply { topMargin = dp(4) })
            addView(expand, LinearLayout.LayoutParams(dp(82), dp(38)).apply { topMargin = dp(4) })
        }

        inputRow.addView(input, LinearLayout.LayoutParams(0, dp(86), 1f))
        inputRow.addView(actions, LinearLayout.LayoutParams(dp(82), dp(82)).apply { marginStart = dp(6) })
        inputRow.addView(send, LinearLayout.LayoutParams(dp(58), dp(82)).apply { marginStart = dp(6) })

        root.addView(inputRow, LinearLayout.LayoutParams(-1, dp(92)).apply { topMargin = dp(6) })

        if (pendingReceipt != null) {
            val latest = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(6), dp(8), dp(6))
                background = rounded(resources.getColor(R.color.bridgefs_input_surface), dp(10))
            }
            latest.addView(TextView(this).apply {
                val status = project?.executions?.lastOrNull()?.status ?: "UNKNOWN"
                text = "最新回执：$status，点击「粘贴回执」放入输入框"
                textSize = 12f
                setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            }, LinearLayout.LayoutParams(0, dp(40), 1f))
            latest.setOnClickListener {
                chatInputField?.setText(pendingReceipt.orEmpty())
                chatInputField?.setSelection(chatInputField?.text?.length ?: 0)
                pendingReceipt = null
                prefs.edit().remove("pending_receipt").apply()
                saveProjects()
                updateNav()
            }
            root.addView(latest, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(4) })
        }

        root.viewTreeObserver.addOnGlobalLayoutListener {
            val visible = android.graphics.Rect()
            root.getWindowVisibleDisplayFrame(visible)
            val keyboardHeight = root.rootView.height - visible.bottom
            if (keyboardHeight > dp(160)) {
                chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
        contentHost.addView(root)

        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private var chatInputField: EditText? = null

    private fun showExpandedEditor() {
        val source = chatInputField?.text?.toString().orEmpty()
        val dialog = Dialog(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(resources.getColor(R.color.bridgefs_surface), dp(0))
        }
        val title = TextView(this).apply {
            text = "大输入框"
            textSize = 18f
            setTypeface(null, 1)
        }
        val edit = EditText(this).apply {
            setText(source)
            setSelection(text.length)
            gravity = Gravity.TOP
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(resources.getColor(R.color.bridgefs_input_surface), dp(12))
        }
        val actions = LinearLayout(this).apply { gravity = Gravity.END }
        val close = smallAction("返回") { dialog.dismiss() }
        val apply = smallAction("使用内容") {
            chatInputField?.setText(edit.text.toString())
            chatInputField?.setSelection(chatInputField?.text?.length ?: 0)
            dialog.dismiss()
        }
        actions.addView(close, LinearLayout.LayoutParams(dp(82), dp(42)))
        actions.addView(apply, LinearLayout.LayoutParams(dp(96), dp(42)).apply { marginStart = dp(8) })
        box.addView(title, LinearLayout.LayoutParams(-1, dp(42)))
        box.addView(edit, LinearLayout.LayoutParams(-1, 0, 1f))
        box.addView(actions, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        dialog.setContentView(box)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.window?.setLayout(-1, -1)
        dialog.show()
        dialog.window?.setLayout(-1, -1)
        edit.requestFocus()
        dialog.window?.decorView?.post {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun showInstructionDialog() {
        val protocol = BridgeCommandSpec.documentation
        AlertDialog.Builder(this)
            .setTitle("A-BridgeFS 指令规范 " + BridgeCommandSpec.version)
            .setMessage(protocol)
            .setPositiveButton("复制全部") { _, _ ->
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("A-BridgeFS指令规范", protocol))
                Toast.makeText(this, "指令规范已复制", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun sendChat() {
        val input = chatInputField ?: return
        val message = input.text.toString().trim()
        if (message.isBlank()) return
        val project = currentProject ?: return
        if (!AccessPolicy.isApiEnabled(this)) {
            Toast.makeText(this, "API 全局访问已关闭，请在「连接与访问」中开启", Toast.LENGTH_SHORT).show()
            drawer.openDrawer(GravityCompat.END)
            return
        }
        val baseUrl = prefs.getString("api_base_url", "").orEmpty().trim()
        val model = prefs.getString("api_model", "").orEmpty().trim()
        if (baseUrl.isBlank() || model.isBlank()) {
            Toast.makeText(this, "请先设置 API 地址和模型", Toast.LENGTH_SHORT).show()
            drawer.openDrawer(GravityCompat.END)
            return
        }

        project.messages += BridgeChatMessage("user", message)
        input.text.clear()
        saveProjects()
        render()

        executor.execute {
            val startedAt = System.currentTimeMillis()
            AppLogger.log(this, "API_CHAT_START", "projectId=${project.id} model=$model")
            try {
                val limit = prefs.getInt("command_limit", 3).coerceIn(1, 20)
                val config = BridgeApiConfig(
                    baseUrl,
                    prefs.getString("api_key", "").orEmpty(),
                    model
                )
                val system = buildSystemPrompt(limit)
                val answer = BridgeApiClient(config).chat(project.messages, system)
                val elapsed = System.currentTimeMillis() - startedAt
                AppLogger.log(this, "API_CHAT_RESULT", "success=true projectId=${project.id} model=$model elapsedMs=$elapsed")
                runOnUiThread {
                    project.messages += BridgeChatMessage("assistant", answer)
                    saveProjects()
                    render()
                    executeAiCommands(answer, project, limit)
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startedAt
                val reason = e.message ?: e::class.simpleName ?: "未知错误"
                AppLogger.log(this, "API_CHAT_RESULT", "success=false projectId=${project.id} model=$model elapsedMs=$elapsed reason=${reason.take(300)}")
                runOnUiThread {
                    project.messages += BridgeChatMessage("tool", "[API 错误]\n" + reason)
                    saveProjects()
                    render()
                }
            }
        }
    }

    private fun buildSystemPrompt(limit: Int): String {
        return BridgeCommandSpec.aiSystemPrompt(limit)
    }

    private fun executeAiCommands(answer: String, project: BridgeProject, limit: Int) {
        val blocks = BridgeRequest.extractAll(answer)
        if (blocks.isEmpty()) {
            val receipt = BridgeReceiptRecord("NOT_TRIGGERED", "AI command", "AI 回复未包含 [bridgefs]...[/bridgefs] 指令区块，本轮未执行本地操作。")
            project.executions += receipt
            setPendingReceipt(receipt, project.id)
            saveProjects()
            render()
            return
        }

        val parsedBlocks = blocks.map { CommandParser.parse(it) }
        val parseError = parsedBlocks.firstOrNull { it.error != null }?.error
        val commands = parsedBlocks.flatMap { it.commands }
        if (commands.isEmpty()) {
            val error = parseError ?: "未识别到 BridgeFS 指令"
            val receipt = BridgeReceiptRecord("FAILED", "AI command", error)
            project.executions += receipt
            setPendingReceipt(receipt, project.id)
            saveProjects()
            render()
            return
        }

        if (parseError != null) {
            val receipt = BridgeReceiptRecord("FAILED", "AI command", parseError)
            project.executions += receipt
            setPendingReceipt(receipt, project.id)
            saveProjects()
            render()
            return
        }

        if (commands.size > limit) {
            val receipt = BridgeReceiptRecord("DENIED", "AI command batch", "本轮指令数量 " + commands.size + " 超过限制 " + limit + "，未执行。")
            project.executions += receipt
            setPendingReceipt(receipt, project.id)
            saveProjects()
            render()
            return
        }

        val auth = authorization()
        val denied = commands.firstOrNull { PermissionPolicy.check(it, auth) == Decision.DENY }
        if (denied != null) {
            val receipt = BridgeReceiptRecord("DENIED", denied.toString(), "当前权限设置禁止该操作")
            project.executions += receipt
            setPendingReceipt(receipt, project.id)
            saveProjects()
            render()
            return
        }

        val confirm = commands.firstOrNull { PermissionPolicy.check(it, auth) == Decision.CONFIRM }
        if (confirm != null) {
            AlertDialogCompat(this, "需要确认", blocks.joinToString("\n\n")) {
                dispatchToBridge(blocks.joinToString("\n\n"), project)
            }
        } else {
            dispatchToBridge(blocks.joinToString("\n\n"), project)
        }
    }

    private fun dispatchToBridge(command: String, project: BridgeProject) {
        val root = prefs.getString("root_path", "").orEmpty().trim()
        if (root.isBlank()) {
            val receipt = BridgeReceiptRecord("FAILED", "AI command", "未设置 BridgeFS 工作目录，指令未执行。")
            project.executions += receipt
            setPendingReceipt(receipt, project.id)
            saveProjects()
            render()
            Toast.makeText(this, "请先设置 BridgeFS 工作目录", Toast.LENGTH_SHORT).show()
            drawer.openDrawer(GravityCompat.END)
            return
        }
        val intent = Intent(this, FileBridgeService::class.java)
            .putExtra("bridgefs_external_command", command)
            .putExtra("bridgefs_root", root)
            .putExtra("projectId", project.id)
        runCatching {
            startForegroundService(intent)
        }.onFailure {
            val receipt = BridgeReceiptRecord("FAILED", "AI command", "启动 BridgeFS 执行服务失败：" + (it.message ?: "未知错误"))
            project.executions += receipt
            setPendingReceipt(receipt, project.id)
            saveProjects()
            render()
            Toast.makeText(this, "启动 BridgeFS 执行服务失败：" + it.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun authorization(): Authorization {
        val allowed = mutableSetOf<FileAction>()
        val confirm = mutableSetOf<FileAction>()
        FileAction.values().forEach { action ->
            when (prefs.getString("perm_" + action.name, if (action == FileAction.LIST || action == FileAction.READ) "allow" else "confirm")) {
                "allow" -> allowed += action
                "confirm" -> {
                    allowed += action
                    confirm += action
                }
            }
        }
        return Authorization(
            prefs.getString("root_path", "").orEmpty(),
            allowed,
            confirm
        )
    }

    private fun renderFiles() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(6))
        }

        val title = TextView(this).apply {
            text = "📁 " + currentPath.name.ifBlank { currentPath.absolutePath }
            textSize = 18f
            setTypeface(null, 1)
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
        }
        root.addView(title, LinearLayout.LayoutParams(-1, dp(44)))

        val path = TextView(this).apply {
            text = currentPath.absolutePath
            textSize = 12f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
        }
        root.addView(path, LinearLayout.LayoutParams(-1, dp(34)))

        val actionRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val up = smallAction("← 返回") {
            currentPath.parentFile?.let { currentPath = it; render() }
        }
        val home = smallAction("⌂ 目录") {
            currentPath = File(prefs.getString("root_path", "/storage/emulated/0") ?: "/storage/emulated/0")
            render()
        }
        actionRow.addView(up, LinearLayout.LayoutParams(0, dp(40), 1f))
        actionRow.addView(home, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) })
        root.addView(actionRow, LinearLayout.LayoutParams(-1, dp(44)))

        val files = currentPath.listFiles()
            ?.filter { !it.isHidden && (!it.isDirectory || !isProtectedWorkspace(it.absolutePath)) }
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.getDefault()) })
            .orEmpty()

        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = FileAdapter(files)
        }
        root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(4) })

        contentHost.addView(root)
    }

    private inner class FileAdapter(private val files: List<File>) :
        RecyclerView.Adapter<FileAdapter.Holder>() {
        inner class Holder(val row: LinearLayout, val name: TextView, val check: CheckBox) :
            RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), 0, 0, 0)
                background = rounded(resources.getColor(R.color.bridgefs_surface), dp(8))
            }
            val name = TextView(this@MainActivity).apply {
                textSize = 14f
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            }
            val check = CheckBox(this@MainActivity).apply {
                isFocusable = false
                gravity = Gravity.CENTER
                includeFontPadding = false
            }
            row.addView(name, LinearLayout.LayoutParams(0, dp(50), 1f))
            row.addView(check, LinearLayout.LayoutParams(dp(44), dp(50)))
            return Holder(row, name, check)
        }

        override fun getItemCount() = files.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val file = files[position]
            holder.name.text = (if (file.isDirectory) "📁 " else "📄 ") + file.name
            holder.check.visibility = if (file.isDirectory) View.VISIBLE else View.INVISIBLE
            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = isRootAdded(file.absolutePath)
            holder.check.setOnCheckedChangeListener { _, checked ->
                if (checked != isRootAdded(file.absolutePath)) toggleRoot(file.absolutePath)
            }
            holder.row.setOnClickListener {
                if (file.isDirectory) {
                    currentPath = file
                    render()
                }
            }
        }
    }

    private fun renderReceipts() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(6))
        }
        val title = TextView(this).apply {
            text = "📋 执行回执"
            textSize = 18f
            setTypeface(null, 1)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, dp(44)))

        val project = currentProject
        if (project?.executions.isNullOrEmpty()) {
            root.addView(TextView(this).apply {
                text = "还没有执行回执。"
                textSize = 13f
                setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
                setPadding(dp(8), dp(20), dp(8), dp(20))
            })
        } else {
            val scroll = ScrollView(this)
            val list = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            project?.executions?.asReversed()?.forEach { receipt ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(8), dp(8))
                    background = rounded(resources.getColor(R.color.bridgefs_input_surface), dp(12))
                    setOnClickListener { showReceiptDetail(receipt) }
                }
                card.addView(TextView(this).apply {
                    text = receiptSummary(receipt)
                    textSize = 13f
                    setTextColor(resources.getColor(R.color.bridgefs_text_primary))
                    setPadding(0, 0, 0, dp(6))
                }, LinearLayout.LayoutParams(-1, -2))
                val actions = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                }
                actions.addView(smallAction("复制回执") {
                    copyToClipboard("BridgeFS回执", formatReceipt(receipt))
                    Toast.makeText(this@MainActivity, "回执已复制", Toast.LENGTH_SHORT).show()
                }, LinearLayout.LayoutParams(dp(82), dp(32)))
                card.addView(actions, LinearLayout.LayoutParams(-1, dp(34)))
                list.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(5)
                    bottomMargin = dp(5)
                })
            }
            scroll.addView(list)
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        contentHost.addView(root)
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    }

    private fun setPendingReceipt(receipt: BridgeReceiptRecord, projectId: String? = currentProject?.id) {
        pendingReceipt = formatReceipt(receipt)
        val obj = org.json.JSONObject().apply {
            put("status", receipt.status)
            put("command", receipt.command)
            put("message", receipt.message)
            put("time", receipt.time)
            put("projectId", projectId.orEmpty())
        }
        prefs.edit().putString("pending_receipt", obj.toString()).apply()
    }

    private fun showReceiptDetail(receipt: BridgeReceiptRecord) {
        val text = formatReceipt(receipt)
        AlertDialog.Builder(this)
            .setTitle("执行回执")
            .setMessage(text)
            .setPositiveButton("复制") { _, _ ->
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("BridgeFS回执", text))
                Toast.makeText(this, "回执已复制", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun renderOnboarding() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(22))
        }
        root.addView(TextView(this).apply {
            text = "BridgeFS 快速验证版"
            textSize = 26f
            setTypeface(null, 1)
        }, LinearLayout.LayoutParams(-1, dp(54)))
        root.addView(TextView(this).apply {
            text = "这一版重点验证：对话 → AI 指令 → BridgeFS 执行 → 回执 → 人工粘贴回 AI。"
            textSize = 14f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(smallAction("开始使用") {
            prefs.edit().putBoolean("first_run_completed", true).apply()
            screen = Screen.CHAT
            render()
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(20) })
        contentHost.addView(root)
    }

    private fun buildSettingsDrawer(): View {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(22), dp(16), dp(24))
            background = rounded(resources.getColor(R.color.bridgefs_surface), dp(0))
        }

        box.addView(TextView(this).apply {
            text = "设置"
            textSize = 22f
            setTypeface(null, 1)
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
        }, LinearLayout.LayoutParams(-1, dp(52)))

        val categories = listOf(
            "AI 与 API" to "API 地址、Key、Model、连接测试",
            "连接与访问" to "API / GitHub 全局访问开关",
            "执行与权限" to "AI 指令执行范围与确认策略",
            "指令" to "BridgeFS 指令协议与说明",
            "文件与目录" to "工作目录与文件相关设置",
            "通知" to "回执与执行通知",
            "外观" to "界面与主题",
            "系统" to "悬浮窗、后台运行等",
            "日志与诊断" to "运行日志与崩溃日志"
        )

        categories.forEach { (title, summary) ->
            box.addView(settingCategory(title, summary) {
                if (title == "AI 与 API") {
                    startActivity(Intent(this, ApiSettingsActivity::class.java))
                } else if (title == "连接与访问") {
                    startActivity(Intent(this, GlobalAccessActivity::class.java))
                } else {
                    startActivity(Intent(this, SettingsCategoryActivity::class.java).putExtra("category", title))
                }
            })
        }

        box.addView(smallAction("关闭设置") {
            drawer.closeDrawer(GravityCompat.END)
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })

        scroll.addView(box)
        return scroll
    }

    private fun settingCategory(title: String, summary: String, action: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), 0, dp(10), 0)
        background = rounded(resources.getColor(R.color.bridgefs_input_surface), dp(12))
        setOnClickListener { action() }
        addView(TextView(this@MainActivity).apply {
            text = title + "  ›"
            textSize = 15f
            setTypeface(null, 1)
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
        }, LinearLayout.LayoutParams(-1, dp(28)))
        addView(TextView(this@MainActivity).apply {
            text = summary
            textSize = 12f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
        }, LinearLayout.LayoutParams(-1, dp(24)))
    }.also { it.layoutParams = LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(12) } }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTypeface(null, 1)
        setTextColor(resources.getColor(R.color.bridgefs_text_primary))
        setPadding(0, dp(12), 0, dp(6))
    }

    private fun apiLabel(): String {
        val base = prefs.getString("api_base_url", "").orEmpty()
        val model = prefs.getString("api_model", "").orEmpty()
        return if (base.isBlank() && model.isBlank()) "未设置 API  ·  点击这里设置"
        else (model.ifBlank { "未命名模型" } + "  ·  " + base.ifBlank { "未设置地址" } + "  ›")
    }

    private fun buildSystemPromptUnused() = Unit

    private fun receiptSummary(r: BridgeReceiptRecord): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(r.time))
        val icon = when (r.status) {
            "SUCCEEDED" -> "✓"
            "FAILED" -> "✕"
            "DENIED" -> "!"
            else -> "•"
        }
        return icon + "  " + r.status + "\n" + r.command.take(160) + "\n" + time
    }

    private fun formatReceipt(r: BridgeReceiptRecord): String {
        return "[BridgeFS Receipt]\n" +
            "status=" + r.status + "\n" +
            "command=" + r.command + "\n" +
            "time=" + r.time + "\n" +
            r.message
    }

    private fun showReceiptDetailUnused() = Unit

    private fun normalizedPath(path: String): String =
        runCatching { File(path).canonicalPath.trimEnd('/') }
            .getOrElse { File(path).absolutePath.trimEnd('/') }

    private fun roots(): MutableList<String> {
        val saved = prefs.getStringSet("root_paths", null)?.toMutableList() ?: mutableListOf()
        val current = prefs.getString("root_path", null)
        if (!current.isNullOrBlank() && !saved.contains(current)) saved.add(current)
        return saved.sorted().toMutableList()
    }

    private fun saveRoots(list: List<String>) {
        prefs.edit().putStringSet("root_paths", list.toSet()).apply()
    }

    private fun isRootAdded(path: String): Boolean =
        roots().any { normalizedPath(it).equals(normalizedPath(path), ignoreCase = true) }

    private fun toggleRoot(path: String) {
        val normalized = normalizedPath(path)
        val list = roots()
        val existing = list.firstOrNull { normalizedPath(it).equals(normalized, ignoreCase = true) }
        if (existing != null) {
            list.remove(existing)
            saveRoots(list)
            if (normalizedPath(prefs.getString("root_path", "").orEmpty()) == normalized) {
                val next = list.firstOrNull()
                if (next == null) prefs.edit().remove("root_path").apply()
                else prefs.edit().putString("root_path", next).apply()
            }
        } else {
            addRoot(normalized)
        }
    }

    private fun addRoot(path: String) {
        val normalized = normalizedPath(path)
        if (isProtectedWorkspace(normalized)) {
            Toast.makeText(this, "此目录属于系统受保护区域，无法作为工作区", Toast.LENGTH_LONG).show()
            return
        }
        val list = roots()
        if (list.none { normalizedPath(it).equals(normalized, ignoreCase = true) }) {
            list.add(normalized)
            saveRoots(list)
        }
        prefs.edit().putString("root_path", normalized).apply()
        Toast.makeText(this, "已设为工作区", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun isProtectedWorkspace(path: String): Boolean {
        val candidate = normalizedPath(path).replace('\\', '/').lowercase(Locale.ROOT)
        val storageRoot = normalizedPath(Environment.getExternalStorageDirectory().absolutePath)
            .replace('\\', '/').lowercase(Locale.ROOT)
        if (candidate == storageRoot) return true
        val segments = candidate.split('/').filter { it.isNotEmpty() }
        return segments.any { it == "android" } ||
            listOf("/android/data", "/android/obb", "/android/media").any { candidate.contains(it) }
    }

    private fun autoStartIfNeeded(showHint: Boolean) {
        if (!prefs.getBoolean("auto_show_overlay", true) || FileBridgeService.running) return
        if (!Settings.canDrawOverlays(this)) {
            if (showHint) Toast.makeText(this, "请在设置中开启悬浮窗权限。", Toast.LENGTH_LONG).show()
            return
        }
        val workspace = prefs.getString("root_path", "").orEmpty()
        if (workspace.isBlank() || !File(workspace).isDirectory || isProtectedWorkspace(workspace)) {
            if (showHint) Toast.makeText(this, "请先设置工作目录。", Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            ContextCompatCompat.startService(this, Intent(this, FileBridgeService::class.java))
        }
    }

    private fun saveProjects() = store.save(projects)

    private fun smallAction(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(resources.getColor(R.color.bridgefs_button_text))
        background = GradientDrawable().apply {
            setColor(resources.getColor(R.color.bridgefs_button_bg))
            cornerRadius = dp(10).toFloat()
        }
        setOnClickListener { action() }
    }

    private fun navItem(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setOnClickListener { action() }
    }

    private fun rounded(fill: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius.toFloat()
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun AlertDialogCompat(
        activity: Activity,
        title: String,
        message: String,
        confirmAction: () -> Unit
    ) {
        android.app.AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("执行") { _, _ -> confirmAction() }
            .setNegativeButton("拒绝", null)
            .show()
    }
}

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
            val status = intent.getStringExtra("status") ?: "UNKNOWN"
            val command = intent.getStringExtra("command") ?: ""
            val message = intent.getStringExtra("message") ?: ""
            val projectId = intent.getStringExtra("projectId")
            val project = projects.firstOrNull { it.id == projectId } ?: currentProject
            val receipt = BridgeReceiptRecord(status, command, message)
            project?.executions?.add(receipt)
            pendingReceipt = formatReceipt(receipt)
            prefs.edit().remove("pending_receipt").apply()
            saveProjects()
            render()
            Toast.makeText(this@MainActivity, "🔔 收到新的执行回执", Toast.LENGTH_SHORT).show()
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
        val raw = prefs.getString("pending_receipt", null) ?: return
        runCatching {
            val obj = org.json.JSONObject(raw)
            val receipt = BridgeReceiptRecord(
                obj.optString("status", "UNKNOWN"),
                obj.optString("command", ""),
                obj.optString("message", ""),
                obj.optLong("time", System.currentTimeMillis())
            )
            val projectId = obj.optString("projectId", "")
            val project = projects.firstOrNull { it.id == projectId } ?: currentProject
            if (project != null) {
                val duplicate = project.executions.any {
                    it.time == receipt.time && it.status == receipt.status &&
                        it.command == receipt.command && it.message == receipt.message
                }
                if (!duplicate) project.executions += receipt
            }
            pendingReceipt = formatReceipt(receipt)
            saveProjects()
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
            val bubble = TextView(this).apply {
                text = message.content
                textSize = 14f
                setTextColor(resources.getColor(R.color.bridgefs_text_primary))
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(
                    if (message.role == "user") resources.getColor(R.color.bridgefs_button_bg)
                    else resources.getColor(R.color.bridgefs_input_surface),
                    dp(14)
                )
            }
            val wrap = FrameLayout(this)
            wrap.addView(bubble, FrameLayout.LayoutParams(-2, -2).apply {
                width = (resources.displayMetrics.widthPixels * 0.82f).toInt()
                gravity = if (message.role == "user") Gravity.END else Gravity.START
            })
            chatColumn.addView(wrap, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(5)
                bottomMargin = dp(5)
            })
        }

        if (pendingReceipt != null) {
            val banner = TextView(this).apply {
                text = "🔔 新执行回执\n已自动准备，点击「粘贴回执」即可放入输入框。"
                textSize = 13f
                setTextColor(resources.getColor(R.color.bridgefs_text_primary))
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(resources.getColor(R.color.bridgefs_input_surface), dp(12))
                setOnClickListener { screen = Screen.RECEIPTS; render() }
            }
            chatColumn.addView(banner, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
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
            addView(expand, LinearLayout.LayoutParams(dp(82), dp(38)).apply { topMargin = dp(4) })
        }

        inputRow.addView(input, LinearLayout.LayoutParams(0, dp(86), 1f))
        inputRow.addView(actions, LinearLayout.LayoutParams(dp(82), dp(82)).apply { marginStart = dp(6) })
        inputRow.addView(send, LinearLayout.LayoutParams(dp(58), dp(82)).apply { marginStart = dp(6) })

        root.addView(inputRow, LinearLayout.LayoutParams(-1, dp(92)).apply { topMargin = dp(6) })
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

    private fun sendChat() {
        val input = chatInputField ?: return
        val message = input.text.toString().trim()
        if (message.isBlank()) return
        val project = currentProject ?: return
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
        return "你是 BridgeFS 的 AI 协作助手。正常情况下使用自然语言对话。" +
            "需要让手机执行本地文件操作时，必须把可执行内容放进 [bridgefs] ... [/bridgefs] 区块。" +
            "区块外的内容绝不会直接执行。" +
            "支持 [list]、[read: 文件]、[write: 文件] 内容 [/write]、[edit: 文件] 旧内容====新内容 [/edit]。" +
            "每次回复最多输出 " + limit + " 条本地指令。" +
            "不要输出 delete、shell、move 等未开放操作。" +
            "不要声称自己已经执行；执行结果必须等待 BridgeFS Receipt。" +
            "当前版本不会自动把 Receipt 发回给你。" +
            "用户会在收到回执通知后决定是否把回执粘贴到聊天框。" +
            "如果没有用户提供新的 Receipt，不要假设文件操作已经成功。"
    }

    private fun executeAiCommands(answer: String, project: BridgeProject, limit: Int) {
        val blocks = BridgeRequest.extractAll(answer)
        if (blocks.isEmpty()) return

        val commands = blocks.flatMap { CommandParser.parse(it) }
        if (commands.isEmpty()) {
            val error = CommandParser.lastError ?: "未识别到 BridgeFS 指令"
            project.executions += BridgeReceiptRecord("FAILED", "AI command", error)
            saveProjects()
            return
        }

        if (commands.size > limit) {
            project.executions += BridgeReceiptRecord(
                "DENIED",
                "AI command batch",
                "本轮指令数量 " + commands.size + " 超过限制 " + limit + "，未执行。"
            )
            saveProjects()
            return
        }

        val auth = authorization()
        val denied = commands.firstOrNull { PermissionPolicy.check(it, auth) == Decision.DENY }
        if (denied != null) {
            project.executions += BridgeReceiptRecord("DENIED", denied.toString(), "当前权限设置禁止该操作")
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
                setPadding(dp(8), 0, dp(4), 0)
                background = rounded(resources.getColor(R.color.bridgefs_surface), dp(8))
            }
            val name = TextView(this@MainActivity).apply {
                textSize = 14f
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            }
            val check = CheckBox(this@MainActivity).apply {
                isFocusable = false
            }
            row.addView(name, LinearLayout.LayoutParams(0, dp(50), 1f))
            row.addView(check, LinearLayout.LayoutParams(dp(48), dp(50)))
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
                val card = TextView(this).apply {
                    text = receiptSummary(receipt)
                    textSize = 13f
                    setTextColor(resources.getColor(R.color.bridgefs_text_primary))
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    background = rounded(resources.getColor(R.color.bridgefs_input_surface), dp(12))
                    setOnClickListener {
                        showReceiptDetail(receipt)
                    }
                }
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
        }, LinearLayout.LayoutParams(-1, dp(48)))

        box.addView(settingCategory("AI 与 API", "API、Model、连接测试") {
            startActivity(Intent(this, ApiSettingsActivity::class.java))
        })

        box.addView(sectionTitle("执行与权限"))
        val limitInput = EditText(this).apply {
            hint = "每次 AI 回复最多执行几条指令"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("command_limit", 3).toString())
        }
        box.addView(limitInput, LinearLayout.LayoutParams(-1, dp(50)))

        val labels = arrayOf("查看目录", "读取文件", "创建文件", "修改文件")
        val actions = FileAction.values()
        val spinners = mutableListOf<Spinner>()
        actions.forEachIndexed { index, action ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this@MainActivity).apply {
                text = labels[index]
                textSize = 13f
                setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            }, LinearLayout.LayoutParams(0, dp(46), 1f))
            val spinner = Spinner(this@MainActivity)
            spinner.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("允许", "需确认", "禁止"))
            spinner.setSelection(when (prefs.getString("perm_" + action.name, if (action == FileAction.LIST || action == FileAction.READ) "allow" else "confirm")) {
                "confirm" -> 1
                "deny" -> 2
                else -> 0
            })
            spinners += spinner
            row.addView(spinner, LinearLayout.LayoutParams(dp(100), dp(46)))
            box.addView(row)
        }

        box.addView(sectionTitle("文件与目录"))
        val rootInput = EditText(this).apply {
            hint = "当前工作目录"
            setText(prefs.getString("root_path", ""))
        }
        box.addView(rootInput, LinearLayout.LayoutParams(-1, dp(50)))

        box.addView(sectionTitle("系统"))
        box.addView(smallAction("悬浮窗权限") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            }
        }, LinearLayout.LayoutParams(-1, dp(42)))

        val autoShow = CheckBox(this).apply {
            text = "自动显示悬浮球"
            textSize = 13f
            isChecked = prefs.getBoolean("auto_show_overlay", true)
        }
        box.addView(autoShow, LinearLayout.LayoutParams(-1, dp(44)))
        box.addView(TextView(this).apply {
            text = "ColorOS 可能还需要允许后台运行、自启动。"
            textSize = 12f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(0, dp(4), 0, dp(6))
        })

        box.addView(smallAction("保存设置") {
            prefs.edit()
                .putInt("command_limit", limitInput.text.toString().toIntOrNull()?.coerceIn(1, 20) ?: 3)
                .putBoolean("auto_show_overlay", autoShow.isChecked)
                .putString("root_path", rootInput.text.toString().trim())
                .apply()
            actions.forEachIndexed { index, action ->
                val value = when (spinners[index].selectedItemPosition) { 1 -> "confirm"; 2 -> "deny"; else -> "allow" }
                prefs.edit().putString("perm_" + action.name, value).apply()
            }
            currentPath = File(rootInput.text.toString().trim().ifBlank { "/storage/emulated/0" })
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            drawer.closeDrawer(GravityCompat.END)
            render()
            autoStartIfNeeded(false)
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(12) })

        box.addView(smallAction("关闭设置") { drawer.closeDrawer(GravityCompat.END) }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
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

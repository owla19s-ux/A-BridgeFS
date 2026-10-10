package com.abridgefs.app

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.abridgefs.app.ai.AIConnection
import com.abridgefs.app.ai.AIConnector
import com.abridgefs.app.ai.AIRequest
import com.abridgefs.app.ai.AIResponse
import com.abridgefs.app.conversation.Conversation
import com.abridgefs.app.conversation.ConversationGroup
import com.abridgefs.app.conversation.ConversationManager
import com.abridgefs.app.conversation.ConversationService
import com.abridgefs.app.conversation.ConversationStore

class ConversationActivity : AppCompatActivity() {
    private lateinit var store: ConversationStore
    private lateinit var manager: ConversationManager
    private lateinit var conversationList: LinearLayout
    private lateinit var groupList: LinearLayout
    private lateinit var messageList: LinearLayout
    private lateinit var input: EditText
    private lateinit var title: TextView
    private lateinit var sendButton: Button
    private lateinit var cancelButton: Button
    private lateinit var requestStatus: TextView
    @Volatile private var requestInFlight = false
    @Volatile private var requestCancelled = false
    private var current: Conversation? = null
    private var selectedGroupFilter: String? = null

    private lateinit var aiConnection: AIConnection
    private lateinit var connector: AIConnector
    private lateinit var profileStore: com.abridgefs.app.ai.AIProfileStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ConversationStore(this)
        manager = ConversationManager(store)
        configureAI()
        setContentView(buildUi())
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized && ::manager.isInitialized && ::title.isInitialized) {
            configureAI()
            refresh()
        }
    }

    private fun configureAI() {
        profileStore = com.abridgefs.app.ai.AIProfileStore(this)
        val selectedId = current?.aiConnectionId
        val profile = selectedId?.let(profileStore::load) ?: profileStore.load()
        val registry = com.abridgefs.app.ai.AIConnectorRegistry(
            profileProvider = profileStore::load
        )
        aiConnection = if (profile != null) {
            registry.connection(profile)
        } else {
            AIConnection(com.abridgefs.app.ai.AIProfile.DEFAULT_ID, "AI")
        }
        connector = if (profile != null) {
            registry.resolve(aiConnection)
        } else {
            object : AIConnector {
                override suspend fun send(request: AIRequest): AIResponse =
                    AIResponse("尚未配置真实 AI API。请先打开「API 设置」填写地址、密钥和模型。")
            }
        }
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        title = TextView(this).apply {
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)
        header.addView(Button(this).apply {
            text = "连接 / 模型"
            setOnClickListener { showConversationAiSettings() }
        })
        header.addView(Button(this).apply {
            text = "API 设置"
            setOnClickListener {
                startActivity(android.content.Intent(this@ConversationActivity, AISettingsActivity::class.java))
            }
        })
        header.addView(Button(this).apply {
            text = "新对话"
            setOnClickListener { createConversation() }
        })
        root.addView(header)

        val groupHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        groupHeader.addView(TextView(this).apply {
            text = "分组"
            textSize = 14f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        groupHeader.addView(Button(this).apply {
            text = "新建分组"
            setOnClickListener { promptCreateGroup() }
        })
        root.addView(groupHeader)

        val groupScroll = HorizontalScrollView(this).apply {
            isFillViewport = false
            isHorizontalScrollBarEnabled = false
        }
        groupList = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        groupScroll.addView(groupList)
        root.addView(groupScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val conversationScroll = ScrollView(this)
        conversationList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        conversationScroll.addView(conversationList)
        root.addView(conversationScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        messageList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 12)
        }
        root.addView(messageList, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f
        ))

        requestStatus = TextView(this).apply {
            text = "就绪"
            textSize = 13f
            setPadding(0, 8, 0, 4)
        }
        root.addView(requestStatus)

        val composer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        input = EditText(this).apply {
            hint = "输入消息"
            minLines = 1
        }
        composer.addView(input, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        sendButton = Button(this).apply {
            text = "发送"
            setOnClickListener { sendMessage() }
        }
        composer.addView(sendButton)
        cancelButton = Button(this).apply {
            text = "取消请求"
            visibility = View.GONE
            isEnabled = false
            setOnClickListener { cancelCurrentRequest() }
        }
        root.addView(cancelButton)
        root.addView(composer)
        return root
    }

    private fun refresh() {
        val conversations = store.allConversations()
        if (current == null || conversations.none { it.id == current?.id }) {
            current = conversations.firstOrNull()
        }
        if (current == null) current = manager.createConversation(defaultAIConnectionId())
        configureAI()
        renderGroups()
        renderConversationList(store.allConversations())
        renderCurrent()
    }

    private fun createConversation() {
        current = manager.createConversation(
            aiConnectionId = defaultAIConnectionId(),
            groupId = selectedGroupFilter?.takeUnless { it == UNGROUPED_FILTER }
        )
        refresh()
    }

    private fun defaultAIConnectionId(): String =
        profileStore.load()?.id ?: aiConnection.id

    private fun renderGroups() {
        groupList.removeAllViews()
        addGroupFilterButton("全部", null)
        addGroupFilterButton("未分组", UNGROUPED_FILTER)
        store.allGroups().forEach { group ->
            groupList.addView(Button(this).apply {
                text = group.name
                setOnClickListener {
                    selectedGroupFilter = group.id
                    renderGroups()
                    renderConversationList(store.allConversations())
                }
                setOnLongClickListener {
                    showGroupActions(group)
                    true
                }
            })
        }
    }

    private fun addGroupFilterButton(label: String, filter: String?) {
        groupList.addView(Button(this).apply {
            text = label
            setOnClickListener {
                selectedGroupFilter = filter
                renderGroups()
                renderConversationList(store.allConversations())
            }
        })
    }

    private fun renderConversationList(conversations: List<Conversation>) {
        conversationList.removeAllViews()
        val visible = when (val filter = selectedGroupFilter) {
            null -> conversations
            UNGROUPED_FILTER -> conversations.filter { it.groupId == null }
            else -> conversations.filter { it.groupId == filter }
        }
        if (visible.isEmpty()) {
            conversationList.addView(TextView(this).apply {
                text = "此分组暂无对话"
                setPadding(8, 12, 8, 12)
            })
            return
        }
        if (selectedGroupFilter != null) {
            visible.forEach(::addConversationButton)
            return
        }

        val groupsById = store.allGroups().associateBy { it.id }
        visible.groupBy { it.groupId }.forEach { (groupId, groupedConversations) ->
            conversationList.addView(TextView(this).apply {
                text = groupId?.let { groupsById[it]?.name } ?: "未分组"
                textSize = 14f
                setPadding(8, 10, 8, 4)
            })
            groupedConversations.forEach(::addConversationButton)
        }
    }

    private fun addConversationButton(conversation: Conversation) {
        conversationList.addView(Button(this).apply {
            text = if (conversation.id == current?.id) "● ${conversation.name}" else conversation.name
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener {
                current = store.getConversation(conversation.id)
                configureAI()
                renderConversationList(store.allConversations())
                renderCurrent()
            }
            setOnLongClickListener {
                showConversationActions(conversation)
                true
            }
        })
    }

    private fun renderCurrent() {
        val conversation = current ?: return
        title.text = aiConnection.name + " · " + conversation.name
        messageList.removeAllViews()
        conversation.messages.forEach { message ->
            messageList.addView(TextView(this).apply {
                text = message.role.name + ": " + message.text
                setPadding(8, 8, 8, 8)
            })
        }
    }

    private fun showConversationAiSettings() {
        val conversation = current ?: return
        AlertDialog.Builder(this)
            .setTitle("当前对话的 AI 设置")
            .setItems(arrayOf("切换 API 连接", "设置当前对话模型")) { _, which ->
                when (which) {
                    0 -> chooseConversationConnection(conversation)
                    1 -> editConversationModel(conversation)
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun chooseConversationConnection(conversation: Conversation) {
        val profiles = profileStore.loadAll()
        if (profiles.isEmpty()) {
            toast("请先在 API 设置中保存连接")
            return
        }
        val defaultId = profileStore.defaultProfileId()
        val labels = profiles.map { profile ->
            (if (profile.id == conversation.aiConnectionId) "● " else "") +
                (if (profile.id == defaultId) "默认 · " else "") +
                profile.name + " · " + profile.model
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择当前对话的 API 连接")
            .setItems(labels) { _, index ->
                val selected = profiles[index]
                current = conversation.copy(
                    aiConnectionId = selected.id,
                    aiModelId = null,
                    updatedAt = System.currentTimeMillis()
                )
                store.saveConversation(current!!)
                configureAI()
                refresh()
                toast("当前对话已切换连接；模型使用该连接的默认模型")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun editConversationModel(conversation: Conversation) {
        val profile = profileStore.load(conversation.aiConnectionId)
        if (profile == null) {
            toast("当前对话的 API 连接不可用，请先切换连接")
            return
        }
        val field = EditText(this).apply {
            hint = "模型 ID"
            setSingleLine(true)
            setText(conversation.aiModelId ?: profile.model)
            setSelection(text.length)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
            addView(field, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(Button(this@ConversationActivity).apply {
                text = "获取模型列表"
                setOnClickListener {
                    text = "正在获取……"
                    isEnabled = false
                    Thread {
                        val result = runCatching {
                            kotlinx.coroutines.runBlocking {
                                com.abridgefs.app.ai.OpenAICompatibleConnector(profile).listModels()
                            }
                        }
                        runOnUiThread {
                            text = "获取模型列表"
                            isEnabled = true
                            result.onSuccess { models ->
                                if (models.isEmpty()) {
                                    toast("服务未返回模型目录，请手动输入模型 ID")
                                } else {
                                    AlertDialog.Builder(this@ConversationActivity)
                                        .setTitle("选择模型")
                                        .setItems(models.toTypedArray()) { _, index ->
                                            field.setText(models[index])
                                        }
                                        .setNegativeButton("取消", null)
                                        .show()
                                }
                            }.onFailure { error ->
                                toast("获取模型列表失败：" + (error.message ?: "未知错误"))
                            }
                        }
                    }.start()
                }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        AlertDialog.Builder(this)
            .setTitle("设置当前对话模型")
            .setMessage("仅影响当前对话；留空将使用连接默认模型。可从模型目录选择，也可手动输入模型 ID。")
            .setView(content)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val model = field.text.toString().trim().ifBlank { null }
                current = conversation.copy(
                    aiModelId = model,
                    updatedAt = System.currentTimeMillis()
                )
                store.saveConversation(current!!)
                refresh()
                toast(if (model == null) "已恢复连接默认模型" else "当前对话模型已保存")
            }
            .show()
    }

    private fun promptCreateGroup() {
        val field = EditText(this).apply { hint = "分组名称" }
        AlertDialog.Builder(this)
            .setTitle("新建对话分组")
            .setView(field)
            .setNegativeButton("取消", null)
            .setPositiveButton("创建") { _, _ ->
                val name = field.text.toString().trim()
                if (name.isBlank()) toast("分组名称不能为空")
                else {
                    manager.createGroup(name)
                    refresh()
                }
            }
            .show()
    }

    private fun showGroupActions(group: ConversationGroup) {
        AlertDialog.Builder(this)
            .setTitle(group.name)
            .setItems(arrayOf("重命名", "删除分组")) { _, which ->
                when (which) {
                    0 -> promptRenameGroup(group)
                    1 -> AlertDialog.Builder(this)
                        .setTitle("删除分组")
                        .setMessage("删除分组不会删除其中的对话，对话将变为未分组。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除") { _, _ ->
                            manager.deleteGroup(group.id)
                            if (selectedGroupFilter == group.id) selectedGroupFilter = null
                            refresh()
                        }
                        .show()
                }
            }
            .show()
    }

    private fun promptRenameGroup(group: ConversationGroup) {
        val field = EditText(this).apply {
            setText(group.name)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("重命名分组")
            .setView(field)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val name = field.text.toString().trim()
                if (name.isBlank()) toast("分组名称不能为空")
                else {
                    manager.renameGroup(group.id, name)
                    refresh()
                }
            }
            .show()
    }

    private fun showConversationActions(conversation: Conversation) {
        AlertDialog.Builder(this)
            .setTitle(conversation.name)
            .setItems(arrayOf("重命名", "移动到分组", "删除对话")) { _, which ->
                when (which) {
                    0 -> promptRenameConversation(conversation)
                    1 -> promptMoveConversation(conversation)
                    2 -> AlertDialog.Builder(this)
                        .setTitle("删除对话")
                        .setMessage("删除后无法恢复此对话记录。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除") { _, _ ->
                            manager.deleteConversation(conversation.id)
                            if (current?.id == conversation.id) current = null
                            refresh()
                        }
                        .show()
                }
            }
            .show()
    }

    private fun promptRenameConversation(conversation: Conversation) {
        val field = EditText(this).apply {
            setText(conversation.name)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("重命名对话")
            .setView(field)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val name = field.text.toString().trim()
                if (name.isBlank()) toast("对话名称不能为空")
                else {
                    manager.renameConversation(conversation.id, name)
                    refresh()
                }
            }
            .show()
    }

    private fun promptMoveConversation(conversation: Conversation) {
        val groups = store.allGroups()
        val labels = arrayOf("未分组") + groups.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("移动对话")
            .setItems(labels) { _, which ->
                val groupId = if (which == 0) null else groups[which - 1].id
                manager.moveConversation(conversation.id, groupId)
                refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun sendMessage() {
        if (requestInFlight) return
        val conversation = current ?: return
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        requestInFlight = true
        requestCancelled = false
        sendButton.isEnabled = false
        cancelButton.visibility = View.VISIBLE
        cancelButton.isEnabled = true
        requestStatus.text = "正在请求 AI……"
        input.text.clear()
        Thread {
            try {
                val updated = kotlinx.coroutines.runBlocking {
                    ConversationService(connector).sendAndSave(conversation, text, store)
                }
                runOnUiThread {
                    requestInFlight = false
                    sendButton.isEnabled = true
                    cancelButton.isEnabled = false
                    cancelButton.visibility = View.GONE
                    requestStatus.text = "回复已收到并保存"
                    current = updated
                    refresh()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    requestInFlight = false
                    sendButton.isEnabled = true
                    cancelButton.isEnabled = false
                    cancelButton.visibility = View.GONE
                    requestStatus.text = if (requestCancelled) "请求已取消；用户消息已保留" else "请求失败；用户消息已保留"
                    // sendAndSave 在请求前已保存用户消息；失败时重新加载并显示该记录。
                    current = store.getConversation(conversation.id)
                    refresh()
                    toast("发送失败：" + (error.message ?: "未知错误"))
                }
            }
        }.start()
    }

    private fun cancelCurrentRequest() {
        if (!requestInFlight) return
        cancelButton.isEnabled = false
        requestCancelled = connector.cancelCurrentRequest()
        requestStatus.text = if (requestCancelled) {
            "正在取消请求……"
        } else {
            "当前连接未能中断请求，等待请求结果……"
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val UNGROUPED_FILTER = "__aps_ungrouped__"
    }
}

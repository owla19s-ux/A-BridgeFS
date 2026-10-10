package com.abridgefs.app

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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
    private var current: Conversation? = null
    private var selectedGroupFilter: String? = null

    private val aiConnection = AIConnection("default-ai", "AI")
    private val connector = object : AIConnector {
        override suspend fun send(request: AIRequest): AIResponse =
            AIResponse("当前已连接对话运行层，等待接入实际 AI Connector。")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ConversationStore(this)
        manager = ConversationManager(store)
        setContentView(buildUi())
        refresh()
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

        val groupScroll = ScrollView(this).apply {
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

        val composer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        input = EditText(this).apply {
            hint = "输入消息"
            minLines = 1
        }
        composer.addView(input, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        composer.addView(Button(this).apply {
            text = "发送"
            setOnClickListener { sendMessage() }
        })
        root.addView(composer)
        return root
    }

    private fun refresh() {
        val conversations = store.allConversations()
        if (current == null || conversations.none { it.id == current?.id }) {
            current = conversations.firstOrNull()
        }
        if (current == null) current = manager.createConversation(aiConnection.id)
        renderGroups()
        renderConversationList(store.allConversations())
        renderCurrent()
    }

    private fun createConversation() {
        current = manager.createConversation(
            aiConnectionId = aiConnection.id,
            groupId = selectedGroupFilter?.takeUnless { it == UNGROUPED_FILTER }
        )
        refresh()
    }

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
        val conversation = current ?: return
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.text.clear()
        Thread {
            try {
                val updated = kotlinx.coroutines.runBlocking {
                    ConversationService(connector).sendAndSave(conversation, text, store)
                }
                runOnUiThread {
                    current = updated
                    refresh()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    toast("发送失败：" + (error.message ?: "未知错误"))
                }
            }
        }.start()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val UNGROUPED_FILTER = "__aps_ungrouped__"
    }
}

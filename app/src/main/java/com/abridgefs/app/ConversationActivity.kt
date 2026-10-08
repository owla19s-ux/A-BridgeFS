package com.abridgefs.app

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.abridgefs.app.ai.AIConnection
import com.abridgefs.app.ai.AIConnector
import com.abridgefs.app.ai.AIRequest
import com.abridgefs.app.ai.AIResponse
import com.abridgefs.app.conversation.Conversation
import com.abridgefs.app.conversation.ConversationManager
import com.abridgefs.app.conversation.ConversationService
import com.abridgefs.app.conversation.ConversationStore

class ConversationActivity : AppCompatActivity() {
    private lateinit var store: ConversationStore
    private lateinit var manager: ConversationManager
    private lateinit var conversationList: LinearLayout
    private lateinit var messageList: LinearLayout
    private lateinit var input: EditText
    private lateinit var title: TextView
    private var current: Conversation? = null

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
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 16, 20, 16) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        title = TextView(this).apply { textSize = 20f; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        header.addView(title)
        header.addView(Button(this).apply { text = "新对话"; setOnClickListener { createConversation() } })
        root.addView(header)
        conversationList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(conversationList)
        messageList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 16) }
        root.addView(messageList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val composer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        input = EditText(this).apply { hint = "输入消息"; minLines = 1 }
        composer.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        composer.addView(Button(this).apply { text = "发送"; setOnClickListener { sendMessage() } })
        root.addView(composer)
        return root
    }

    private fun refresh() {
        val conversations = store.allConversations()
        if (current == null || conversations.none { it.id == current?.id }) current = conversations.firstOrNull()
        if (current == null) { createConversation(); return }
        renderConversationList(conversations)
        renderCurrent()
    }

    private fun createConversation() { current = manager.createConversation(aiConnection.id); refresh() }

    private fun renderConversationList(conversations: List<Conversation>) {
        conversationList.removeAllViews()
        conversations.forEach { conversation ->
            conversationList.addView(Button(this).apply { text = conversation.name; setOnClickListener { current = store.getConversation(conversation.id); renderCurrent() } })
        }
    }

    private fun renderCurrent() {
        val conversation = current ?: return
        title.text = aiConnection.name + " · " + conversation.name
        messageList.removeAllViews()
        conversation.messages.forEach { message ->
            messageList.addView(TextView(this).apply { text = message.role.name + ": " + message.text; setPadding(8, 8, 8, 8) })
        }
    }

    private fun sendMessage() {
        val conversation = current ?: return
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.text.clear()
        Thread {
            val updated = kotlinx.coroutines.runBlocking { ConversationService(connector).sendAndSave(conversation, text, store) }
            runOnUiThread { current = updated; refresh() }
        }.start()
    }
}
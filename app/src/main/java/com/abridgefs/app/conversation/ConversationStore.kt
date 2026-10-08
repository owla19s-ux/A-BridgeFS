package com.abridgefs.app.conversation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 普通对话与对话分组的本地持久化边界。
 *
 * 普通对话记录属于对话本身，不写入 ContextStore。
 * 分组删除只解除分组关系，不删除其中的对话。
 */
class ConversationStore(context: Context) : ConversationStoreApi {
    private val prefs =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun allConversations(): List<Conversation> =
        readConversations().sortedByDescending { it.updatedAt }

    override fun getConversation(id: String): Conversation? =
        readConversations().firstOrNull { it.id == id }

    override fun saveConversation(conversation: Conversation) {
        val conversations = readConversations()
            .filterNot { it.id == conversation.id } + conversation
        writeConversations(conversations)
    }

    override fun deleteConversation(id: String) {
        writeConversations(readConversations().filterNot { it.id == id })
    }

    override fun allGroups(): List<ConversationGroup> =
        readGroups().sortedWith(compareBy<ConversationGroup> { it.updatedAt }.thenBy { it.name })

    override fun getGroup(id: String): ConversationGroup? =
        readGroups().firstOrNull { it.id == id }

    override fun saveGroup(group: ConversationGroup) {
        val groups = readGroups().filterNot { it.id == group.id } + group
        writeGroups(groups)
    }

    override fun deleteGroup(id: String) {
        writeGroups(readGroups().filterNot { it.id == id })
        writeConversations(
            readConversations().map {
                if (it.groupId == id) it.copy(groupId = null, updatedAt = System.currentTimeMillis())
                else it
            }
        )
    }

    override fun moveConversation(id: String, groupId: String?) {
        require(groupId == null || getGroup(groupId) != null) {
            "目标对话分组不存在"
        }
        val conversation = getConversation(id) ?: return
        saveConversation(
            conversation.copy(
                groupId = groupId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun readConversations(): List<Conversation> {
        val raw = prefs.getString(KEY_CONVERSATIONS, null) ?: return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) add(conversationFromJson(array.getJSONObject(i)))
        }
    }

    private fun writeConversations(conversations: List<Conversation>) {
        val array = JSONArray()
        conversations.forEach { array.put(conversationToJson(it)) }
        prefs.edit().putString(KEY_CONVERSATIONS, array.toString()).apply()
    }

    private fun readGroups(): List<ConversationGroup> {
        val raw = prefs.getString(KEY_GROUPS, null) ?: return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) add(groupFromJson(array.getJSONObject(i)))
        }
    }

    private fun writeGroups(groups: List<ConversationGroup>) {
        val array = JSONArray()
        groups.forEach { array.put(groupToJson(it)) }
        prefs.edit().putString(KEY_GROUPS, array.toString()).apply()
    }

    private fun conversationToJson(value: Conversation) = JSONObject().apply {
        put("id", value.id)
        put("name", value.name)
        value.contextId?.let { put("contextId", it) }
        put("aiConnectionId", value.aiConnectionId)
        value.groupId?.let { put("groupId", it) }
        put("createdAt", value.createdAt)
        put("updatedAt", value.updatedAt)
        put("messages", JSONArray().apply {
            value.messages.forEach { put(messageToJson(it)) }
        })
    }

    private fun conversationFromJson(json: JSONObject): Conversation {
        val messages = json.optJSONArray("messages") ?: JSONArray()
        return Conversation(
            id = json.getString("id"),
            name = json.optString("name", "新对话"),
            contextId = json.optString("contextId").ifBlank { null },
            aiConnectionId = json.getString("aiConnectionId"),
            groupId = json.optString("groupId").ifBlank { null },
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            messages = buildList {
                for (i in 0 until messages.length()) {
                    add(messageFromJson(messages.getJSONObject(i)))
                }
            }
        )
    }

    private fun messageToJson(value: Message) = JSONObject().apply {
        put("role", value.role.name)
        put("text", value.text)
        put("createdAt", value.createdAt)
    }

    private fun messageFromJson(json: JSONObject) = Message(
        role = Message.Role.valueOf(json.getString("role")),
        text = json.getString("text"),
        createdAt = json.optLong("createdAt", System.currentTimeMillis())
    )

    private fun groupToJson(value: ConversationGroup) = JSONObject().apply {
        put("id", value.id)
        put("name", value.name)
        put("createdAt", value.createdAt)
        put("updatedAt", value.updatedAt)
    }

    private fun groupFromJson(json: JSONObject) = ConversationGroup(
        id = json.getString("id"),
        name = json.getString("name"),
        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
    )

    private companion object {
        const val PREFERENCES = "aps_conversations"
        const val KEY_CONVERSATIONS = "conversations"
        const val KEY_GROUPS = "groups"
    }
}

interface ConversationStoreApi {
    fun allConversations(): List<Conversation>
    fun getConversation(id: String): Conversation?
    fun saveConversation(conversation: Conversation)
    fun deleteConversation(id: String)
    fun allGroups(): List<ConversationGroup>
    fun getGroup(id: String): ConversationGroup?
    fun saveGroup(group: ConversationGroup)
    fun deleteGroup(id: String)
    fun moveConversation(id: String, groupId: String?)
}

class InMemoryConversationStore : ConversationStoreApi {
    private val conversations = linkedMapOf<String, Conversation>()
    private val groups = linkedMapOf<String, ConversationGroup>()

    override fun allConversations() = conversations.values.sortedByDescending { it.updatedAt }
    override fun getConversation(id: String) = conversations[id]
    override fun saveConversation(conversation: Conversation) { conversations[conversation.id] = conversation }
    override fun deleteConversation(id: String) { conversations.remove(id) }
    override fun allGroups() = groups.values.sortedWith(compareBy<ConversationGroup> { it.updatedAt }.thenBy { it.name })
    override fun getGroup(id: String) = groups[id]
    override fun saveGroup(group: ConversationGroup) { groups[group.id] = group }

    override fun deleteGroup(id: String) {
        groups.remove(id)
        conversations.replaceAll { _, conversation ->
            if (conversation.groupId == id) conversation.copy(groupId = null)
            else conversation
        }
    }

    override fun moveConversation(id: String, groupId: String?) {
        require(groupId == null || groups.containsKey(groupId)) { "目标对话分组不存在" }
        val conversation = conversations[id] ?: return
        conversations[id] = conversation.copy(groupId = groupId, updatedAt = System.currentTimeMillis())
    }
}

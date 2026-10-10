package com.abridgefs.app.local

import com.abridgefs.app.connection.Connection

/** User-granted Android document tree; URI access remains bounded by the OS-granted tree permission. */
data class LocalConnection(
    override val id: String,
    val name: String,
    val treeUri: String,
    val canRead: Boolean = true,
    val canWrite: Boolean = false
) : Connection {
    override val type: Connection.Type = Connection.Type.LOCAL

    init {
        require(id.isNotBlank()) { "Local Connection ID 不能为空" }
        require(name.isNotBlank()) { "本地连接名称不能为空" }
        require(treeUri.startsWith("content://")) { "本地目录必须使用 Android 文档提供方 URI" }
        require(canRead || !canWrite) { "允许写入时必须同时允许读取" }
    }
}

data class LocalEntry(
    val documentId: String,
    val displayName: String,
    val mimeType: String,
    val isDirectory: Boolean
) {
    init {
        require(documentId.isNotBlank()) { "文档 ID 不能为空" }
        require(displayName.isNotBlank()) { "文件名不能为空" }
    }
}

/** Blocking platform operations are isolated here so connector policy can be tested independently. */
interface LocalDocumentGateway {
    fun listChildren(treeUri: String, parentDocumentId: String?): List<LocalEntry>
    fun readText(treeUri: String, documentId: String): String
    fun writeText(treeUri: String, documentId: String, content: String)

    /** Writes only if the content shown to the editor is still current. */
    fun writeTextIfUnchanged(
        treeUri: String,
        documentId: String,
        expectedContent: String,
        content: String
    ) {
        if (readText(treeUri, documentId) != expectedContent) {
            throw java.io.IOException("文件在编辑期间已发生变化；为避免覆盖新内容，已取消本次保存")
        }
        writeText(treeUri, documentId, content)
    }
}

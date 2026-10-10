package com.abridgefs.app.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local-file operations with explicit read/write grants and text-file safeguards. */
class LocalFileConnector(
    private val connection: LocalConnection,
    private val gateway: LocalDocumentGateway
) {
    suspend fun listChildren(parentDocumentId: String? = null): List<LocalEntry> =
        withContext(Dispatchers.IO) {
            requireRead()
            gateway.listChildren(connection.treeUri, parentDocumentId)
        }

    suspend fun readText(entry: LocalEntry): String = withContext(Dispatchers.IO) {
        requireRead()
        requireTextFile(entry)
        gateway.readText(connection.treeUri, entry.documentId)
    }

    suspend fun writeText(
        entry: LocalEntry,
        content: String,
        expectedOriginalContent: String? = null
    ) = withContext(Dispatchers.IO) {
        requireRead()
        check(connection.canWrite) { "当前本地连接只有读取权限，不能修改文件" }
        requireTextFile(entry)
        check(!entry.isDirectory) { "不能把目录当作文本文件写入" }
        if (expectedOriginalContent == null) {
            gateway.writeText(connection.treeUri, entry.documentId, content)
        } else {
            gateway.writeTextIfUnchanged(
                connection.treeUri,
                entry.documentId,
                expectedOriginalContent,
                content
            )
        }
    }

    private fun requireRead() {
        check(connection.canRead) { "当前本地连接未授权读取" }
    }

    private fun requireTextFile(entry: LocalEntry) {
        require(!entry.isDirectory) { "不能读取目录内容为文本" }
        val name = entry.displayName.lowercase()
        val mime = entry.mimeType.lowercase()
        val allowedMime = mime.startsWith("text/") ||
            mime in setOf("application/json", "application/xml", "application/javascript", "application/x-yaml", "application/yaml")
        val allowedExtension = TEXT_EXTENSIONS.any { name.endsWith(it) }
        require(allowedMime || allowedExtension) { "当前只允许读取或修改可识别的文本文件" }
    }

    private companion object {
        val TEXT_EXTENSIONS = setOf(
            ".txt", ".md", ".markdown", ".kt", ".java", ".json", ".xml", ".yml", ".yaml",
            ".gradle", ".properties", ".py", ".js", ".ts", ".html", ".css", ".csv", ".sh",
            ".toml", ".ini", ".sql", ".gitignore", ".ktest"
        )
    }
}

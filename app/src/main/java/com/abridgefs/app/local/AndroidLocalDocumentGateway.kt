package com.abridgefs.app.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/** Android Storage Access Framework implementation, scoped to a user-selected document tree. */
class AndroidLocalDocumentGateway(context: Context) : LocalDocumentGateway {
    private val resolver = context.contentResolver

    override fun listChildren(treeUri: String, parentDocumentId: String?): List<LocalEntry> {
        val tree = Uri.parse(treeUri)
        val parentId = parentDocumentId ?: DocumentsContract.getTreeDocumentId(tree)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val result = mutableListOf<LocalEntry>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idColumn) ?: continue
                val name = cursor.getString(nameColumn) ?: continue
                val mime = cursor.getString(mimeColumn) ?: "application/octet-stream"
                result += LocalEntry(
                    documentId = id,
                    displayName = name,
                    mimeType = mime,
                    isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                )
            }
        } ?: throw IOException("无法读取本地目录；请检查目录授权是否仍有效")
        return result.sortedWith(compareBy<LocalEntry> { !it.isDirectory }.thenBy { it.displayName.lowercase() })
    }

    override fun readText(treeUri: String, documentId: String): String {
        val uri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri), documentId)
        val input = resolver.openInputStream(uri) ?: throw IOException("无法打开本地文件")
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_TEXT_BYTES) throw IOException("文件超过 2 MiB 文本读取上限")
                output.write(buffer, 0, count)
            }
            return String(output.toByteArray(), StandardCharsets.UTF_8)
        }
    }

    override fun writeText(treeUri: String, documentId: String, content: String) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_TEXT_BYTES) throw IOException("文件超过 2 MiB 文本写入上限")
        val uri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri), documentId)

        // SAF providers do not universally support atomic replacement. Keep an exact byte
        // snapshot so ordinary write/close failures can attempt to restore the original.
        val originalBytes = readBytes(uri, MAX_TEXT_BYTES)
        try {
            val output = resolver.openOutputStream(uri, "wt")
                ?: throw IOException("无法打开本地文件进行写入")
            output.use { it.write(bytes) }
        } catch (writeError: Exception) {
            try {
                val restore = resolver.openOutputStream(uri, "wt")
                    ?: throw IOException("无法重新打开本地文件进行恢复")
                restore.use { it.write(originalBytes) }
            } catch (restoreError: Exception) {
                val failure = IOException(
                    "文件写入失败，且恢复原内容也失败；文件内容可能不完整。写入错误：${writeError.message ?: "未知错误"}；恢复错误：${restoreError.message ?: "未知错误"}",
                    writeError
                )
                failure.addSuppressed(restoreError)
                throw failure
            }
            throw writeError
        }
    }

    private fun readBytes(uri: Uri, maxBytes: Int): ByteArray {
        val input = resolver.openInputStream(uri) ?: throw IOException("无法读取原文件，已取消写入以保护现有内容")
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > maxBytes) {
                    throw IOException("原文件超过 2 MiB 安全备份上限，已取消写入以保护现有内容")
                }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }

    private companion object {
        const val MAX_TEXT_BYTES = 2 * 1024 * 1024
    }
}

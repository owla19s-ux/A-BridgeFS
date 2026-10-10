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
        writeDocument(treeUri, documentId, content, expectedOriginalContent = null)
    }

    override fun writeTextIfUnchanged(
        treeUri: String,
        documentId: String,
        expectedContent: String,
        content: String
    ) {
        writeDocument(treeUri, documentId, content, expectedOriginalContent = expectedContent)
    }

    private fun writeDocument(
        treeUri: String,
        documentId: String,
        content: String,
        expectedOriginalContent: String?
    ) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_TEXT_BYTES) throw IOException("文件超过 2 MiB 文本写入上限")
        val uri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri), documentId)

        // SAF providers do not universally support atomic replacement. Keep an exact byte
        // snapshot so ordinary write/close failures can attempt to restore the original.
        val originalBytes = readBytes(uri, MAX_TEXT_BYTES)

        // When saving an open editor, compare against the exact content originally shown to
        // the user, not merely a new snapshot taken at save time.
        if (expectedOriginalContent != null) {
            val expectedBytes = expectedOriginalContent.toByteArray(StandardCharsets.UTF_8)
            if (!originalBytes.contentEquals(expectedBytes)) {
                throw IOException("文件在编辑期间已发生变化；为避免覆盖新内容，已取消本次保存")
            }
        }

        // A second check narrows, but cannot eliminate, the race between checking and writing.
        val latestBytes = readBytes(uri, MAX_TEXT_BYTES)
        if (!latestBytes.contentEquals(originalBytes)) {
            throw IOException("文件在保存前已发生变化；为避免覆盖新内容，已取消本次保存")
        }

        LocalWriteRecovery.write(originalBytes, bytes) { contentBytes ->
            val output = resolver.openOutputStream(uri, "wt")
                ?: throw IOException("无法打开本地文件进行写入")
            output.use { it.write(contentBytes) }

            // Some providers may not surface a short or otherwise incomplete write as an
            // exception. Read back the document before treating this attempt as successful.
            val actualBytes = readBytes(uri, MAX_TEXT_BYTES)
            if (!actualBytes.contentEquals(contentBytes)) {
                throw IOException("写入后校验失败，文件内容与目标内容不一致")
            }
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

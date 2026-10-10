package com.abridgefs.app.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFileConnectorTest {
    private class FakeGateway : LocalDocumentGateway {
        var listed = false
        var read = false
        var written: String? = null
        val entry = LocalEntry("root:notes.md", "notes.md", "text/markdown", false)

        override fun listChildren(treeUri: String, parentDocumentId: String?): List<LocalEntry> {
            listed = true
            return listOf(entry)
        }

        override fun readText(treeUri: String, documentId: String): String {
            read = true
            return "before"
        }

        override fun writeText(treeUri: String, documentId: String, content: String) {
            written = content
        }
    }

    private fun connection(canRead: Boolean = true, canWrite: Boolean = false) = LocalConnection(
        id = "local-test",
        name = "Test folder",
        treeUri = "content://provider/tree/root%3Adocs",
        canRead = canRead,
        canWrite = canWrite
    )

    @Test
    fun listAndReadUseSelectedTree() = runBlocking {
        val gateway = FakeGateway()
        val connector = LocalFileConnector(connection(), gateway)

        assertEquals(listOf(gateway.entry), connector.listChildren())
        assertEquals("before", connector.readText(gateway.entry))
        assertTrue(gateway.listed)
        assertTrue(gateway.read)
    }

    @Test
    fun writeRequiresExplicitWriteGrant() = runBlocking {
        val gateway = FakeGateway()
        val connector = LocalFileConnector(connection(canWrite = false), gateway)

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { connector.writeText(gateway.entry, "after") }
        }
        assertTrue(error.message!!.contains("读取权限"))
        assertEquals(null, gateway.written)
    }

    @Test
    fun writeUsesGatewayWhenGranted() = runBlocking {
        val gateway = FakeGateway()
        val connector = LocalFileConnector(connection(canWrite = true), gateway)

        connector.writeText(gateway.entry, "after")

        assertEquals("after", gateway.written)
    }

    @Test
    fun refusesBinaryFiles() = runBlocking {
        val gateway = FakeGateway()
        val connector = LocalFileConnector(connection(canWrite = true), gateway)
        val binary = LocalEntry("root:photo.png", "photo.png", "image/png", false)

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { connector.readText(binary) }
        }
        assertTrue(error.message!!.contains("文本文件"))
        assertEquals(false, gateway.read)
    }
}

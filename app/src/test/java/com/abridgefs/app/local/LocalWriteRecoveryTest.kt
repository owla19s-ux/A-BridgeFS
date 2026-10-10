package com.abridgefs.app.local

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalWriteRecoveryTest {
    @Test
    fun restoresOriginalBytesWhenNewWriteFails() {
        val original = "original".toByteArray()
        val updated = "updated".toByteArray()
        var current = original
        var calls = 0

        val error = assertThrows(IOException::class.java) {
            LocalWriteRecovery.write(original, updated) { bytes ->
                calls++
                if (calls == 1) {
                    current = bytes.copyOfRange(0, 3)
                    throw IOException("simulated partial write")
                }
                current = bytes
            }
        }

        assertEquals("simulated partial write", error.message)
        assertArrayEquals(original, current)
        assertEquals(2, calls)
    }

    @Test
    fun reportsBothErrorsWhenRestoreAlsoFails() {
        val original = "original".toByteArray()
        val updated = "updated".toByteArray()
        val writeError = IOException("write failed")
        val restoreError = IOException("restore failed")
        var calls = 0

        val error = assertThrows(IOException::class.java) {
            LocalWriteRecovery.write(original, updated) {
                calls++
                if (calls == 1) throw writeError
                throw restoreError
            }
        }

        assertSame(writeError, error.cause)
        assertEquals(1, error.suppressed.size)
        assertSame(restoreError, error.suppressed[0])
        assertEquals(2, calls)
    }

    @Test
    fun doesNotAttemptRestoreWhenWriteSucceeds() {
        val original = "original".toByteArray()
        val updated = "updated".toByteArray()
        var current = original
        var calls = 0

        LocalWriteRecovery.write(original, updated) { bytes ->
            calls++
            current = bytes
        }

        assertArrayEquals(updated, current)
        assertEquals(1, calls)
    }
}

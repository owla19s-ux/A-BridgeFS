package com.abridgefs.app.local

import java.io.IOException

/** Attempts to restore a snapshot when replacing a local document fails. This is best-effort, not atomic. */
internal object LocalWriteRecovery {
    fun write(
        originalBytes: ByteArray,
        newBytes: ByteArray,
        writeBytes: (ByteArray) -> Unit
    ) {
        try {
            writeBytes(newBytes)
        } catch (writeError: Exception) {
            try {
                writeBytes(originalBytes)
            } catch (restoreError: Exception) {
                val failure = IOException(
                    "文件写入失败，且恢复原内容也失败；文件内容可能不完整。" +
                        "写入错误：${writeError.message ?: "未知错误"}；恢复错误：${restoreError.message ?: "未知错误"}",
                    writeError
                )
                failure.addSuppressed(restoreError)
                throw failure
            }
            throw writeError
        }
    }
}

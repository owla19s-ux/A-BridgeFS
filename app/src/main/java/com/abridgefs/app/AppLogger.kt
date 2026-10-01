package com.abridgefs.app

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local diagnostics. Collaboration events are stored separately as JSONL for diagnosis. */
object AppLogger {
    private const val DIR = "logs"
    private const val COLLAB_DIR = "collaboration"
    private const val CRASH_DIR = "crash"
    private const val PUBLIC_ROOT = "A-BridgeFS"

    fun log(context: Context, event: String, detail: String = "") {
        runCatching {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            appendLine(File(dir, dailyName(".log")), buildRuntimeLine(event, detail))
        }
    }

    fun collaboration(
        context: Context,
        stage: String,
        status: String,
        taskId: String? = null,
        messageId: String? = null,
        replyTo: String? = null,
        detail: String? = null,
        durationMs: Long? = null,
        error: Throwable? = null
    ) {
        runCatching {
            val dir = collaborationDir(context)
            val obj = org.json.JSONObject()
                .put("ts", java.time.Instant.now().toString())
                .put("stage", stage)
                .put("status", status)
            taskId?.let { obj.put("task_id", it) }
            messageId?.let { obj.put("message_id", it) }
            replyTo?.let { obj.put("reply_to", it) }
            detail?.let { obj.put("detail", it.take(2000)) }
            durationMs?.let { obj.put("duration_ms", it) }
            error?.let {
                obj.put("error_type", it::class.java.simpleName)
                obj.put("error", (it.message ?: it.toString()).take(2000))
            }
            appendLine(File(dir, dailyName(".jsonl")), obj.toString())
        }
    }

    fun diagnosticsRoot(context: Context): File = collaborationDir(context)

    fun runtimeLogDir(context: Context): File = File(context.filesDir, DIR)

    fun crashLogDir(context: Context): File = File(context.filesDir, CRASH_DIR)

    private fun collaborationDir(context: Context): File {
        val publicDir = File(Environment.getExternalStorageDirectory(), "$PUBLIC_ROOT/$COLLAB_DIR")
        return if (runCatching {
            publicDir.mkdirs()
            File(publicDir, ".write_test").apply { createNewFile(); delete() }
            true
        }.getOrDefault(false)) {
            publicDir
        } else {
            File(context.filesDir, COLLAB_DIR).apply { mkdirs() }
        }
    }

    private fun dailyName(extension: String): String {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "$day$extension"
    }

    private fun buildRuntimeLine(event: String, detail: String): String {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val suffix = if (detail.isBlank()) "" else " | $detail"
        return "[$time] $event$suffix"
    }

    private fun appendLine(file: File, line: String) {
        file.parentFile?.mkdirs()
        file.appendText(line + "\n")
    }

    fun recordCrash(context: Context, throwable: Throwable) {
        runCatching {
            val dir = File(context.filesDir, CRASH_DIR).apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val file = File(dir, "crash_$stamp.log")
            val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            file.writeText(buildString {
                appendLine("time=$stamp")
                appendLine("package=${context.packageName}")
                appendLine("thread=${Thread.currentThread().name}")
                appendLine()
                append(stack)
            })
        }
    }
}

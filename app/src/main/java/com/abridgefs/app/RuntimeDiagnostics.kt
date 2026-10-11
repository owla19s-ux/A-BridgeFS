package com.abridgefs.app

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small local diagnostic log for startup, runtime failures, and API request outcomes.
 * Event names and values must be controlled labels; never pass prompts, response bodies,
 * credentials, headers, or arbitrary server messages to this API.
 */
object RuntimeDiagnostics {
    private const val LOG_DIRECTORY = "aps-diagnostics"
    private const val RETENTION_DAYS = 7L
    private val lock = Any()

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        record("app.start", "success")
    }

    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous !is RuntimeCrashHandler) {
            Thread.setDefaultUncaughtExceptionHandler(RuntimeCrashHandler(previous))
        }
    }

    fun recordApiEvent(
        event: String,
        outcome: String,
        durationMs: Long,
        error: Throwable? = null
    ) {
        val httpStatus = error?.message
            ?.let { Regex("""HTTP\s+(\d{3})""").find(it)?.groupValues?.getOrNull(1) }
        record(
            event = event,
            outcome = outcome,
            durationMs = durationMs,
            httpStatus = httpStatus,
            errorType = error?.javaClass?.simpleName
        )
    }

    fun readRecentLogs(context: Context, maxChars: Int = 24_000): String {
        return try {
            val directory = File(context.applicationContext.filesDir, LOG_DIRECTORY)
            val files = directory.listFiles()
                ?.filter { it.isFile && it.name.startsWith("aps-") && it.name.endsWith(".log") }
                ?.sortedBy { it.lastModified() }
                .orEmpty()
            if (files.isEmpty()) return "暂无诊断日志。"

            val output = StringBuilder()
            files.forEach { file ->
                val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull().orEmpty()
                if (text.isNotEmpty()) {
                    output.append(text).append('\n')
                    if (output.length > maxChars * 2) {
                        output.delete(0, output.length - maxChars)
                    }
                }
            }
            output.toString().takeLast(maxChars).ifBlank { "暂无诊断日志。" }
        } catch (_: Exception) {
            "读取诊断日志失败。"
        }
    }

    fun record(
        event: String,
        outcome: String,
        durationMs: Long? = null,
        httpStatus: String? = null,
        errorType: String? = null
    ) {
        val safeEvent = safeLabel(event)
        val safeOutcome = safeLabel(outcome)
        val line = buildString {
            append(timestamp())
            append(" event=").append(safeEvent)
            append(" outcome=").append(safeOutcome)
            append(" thread=").append(safeLabel(Thread.currentThread().name))
            durationMs?.let { append(" duration_ms=").append(it.coerceAtLeast(0)) }
            httpStatus?.takeIf { it.matches(Regex("""\d{3}""")) }
                ?.let { append(" http_status=").append(it) }
            errorType?.let { append(" error_type=").append(safeLabel(it)) }
        }
        appendLineSafely(line)
    }

    internal fun recordCrash(thread: Thread, throwable: Throwable) {
        val frames = buildString {
            append(timestamp())
            append(" event=app.crash outcome=uncaught")
            append(" thread=").append(safeLabel(thread.name))
            append(" error_type=").append(safeLabel(throwable.javaClass.name))
            throwable.stackTrace.take(60).forEach { frame ->
                append("\n  at ").append(safeLabel(frame.toString()))
            }
            var cause = throwable.cause
            var depth = 0
            while (cause != null && depth < 5) {
                append("\n caused_by=").append(safeLabel(cause.javaClass.name))
                cause = cause.cause
                depth++
            }
        }
        appendLineSafely(frames)
    }

    private fun appendLineSafely(line: String) {
        val context = appContext ?: return
        try {
            synchronized(lock) {
                val directory = File(context.filesDir, LOG_DIRECTORY)
                if (!directory.exists() && !directory.mkdirs()) return
                pruneOldLogs(directory)
                val file = File(directory, "aps-" + fileDate() + ".log")
                FileWriter(file, true).buffered().use { writer ->
                    writer.append(line)
                    writer.newLine()
                }
            }
        } catch (_: Exception) {
            // Diagnostics must never bring down the app's primary flow.
        }
    }

    private fun pruneOldLogs(directory: File) {
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * 24 * 60 * 60 * 1000
        directory.listFiles()
            ?.filter { it.isFile && it.name.startsWith("aps-") && it.name.endsWith(".log") }
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    private fun safeLabel(value: String): String =
        value.replace(Regex("""[\r\n\t]"""), " ")
            .replace(Regex("""(?i)bearer\s+[^\s]+"""), "Bearer [redacted]")
            .take(180)

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())

    private fun fileDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
 
private class RuntimeCrashHandler(
    private val previous: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        RuntimeDiagnostics.recordCrash(thread, throwable)
        if (previous != null) {
            previous.uncaughtException(thread, throwable)
        } else {
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(10)
        }
    }
}

package com.abridgefs.app

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local diagnostics: one runtime log file per calendar day. */
object AppLogger {
    private const val DIR = "logs"

    enum class Category(val dir: String) {
        RUNTIME("runtime"),
        COLLABORATION("collaboration"),
        EXECUTION("execution"),
        API("api"),
        GITHUB("github")
    }
    private const val CRASH_DIR = "crash"

    fun log(context: Context, event: String, detail: String = "") =
        log(context, Category.RUNTIME, event, detail)

    fun log(context: Context, category: Category, event: String, detail: String = "") {
        runCatching {
            val dir = File(context.filesDir, DIR + File.separator + category.dir).apply { mkdirs() }
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            val suffix = if (detail.isBlank()) "" else " | $detail"
            File(dir, "$day.log").appendText("[$time] $event$suffix\n")
        }
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

package com.abridgefs.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local diagnostics: one runtime log file per calendar day. */
object AppLogger {
    private const val DIR = "logs"
    private const val CRASH_DIR = "crash"

    fun log(context: Context, event: String, detail: String = "") {
        runCatching {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            File(dir, "$day.log").appendText("[$time] $event${if (detail.isBlank()) "" else " | $detail"}\n")
        }
    }

    fun recordCrash(context: Context, throwable: Throwable) {
        runCatching {
            val dir = File(context.filesDir, CRASH_DIR).apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val file = File(dir, "crash_$stamp.log")
            file.writeText(buildString {
                appendLine("time=$stamp")
                appendLine("package=${context.packageName}")
                appendLine("version=${BuildConfig.VERSION_NAME}")
                appendLine("thread=${Thread.currentThread().name}")
                appendLine()
                throwable.printStackTrace(java.io.PrintWriter(this))
            })
        }
    }
}

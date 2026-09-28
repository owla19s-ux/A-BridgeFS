package com.abridgefs.app

import android.app.Application
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BridgeFSApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                if (!getSharedPreferences("bridgefs", 0).getBoolean("run_log_enabled", true)) {
                    default?.uncaughtException(t, e)
                    return@runCatching
                }
                val now = Date()
                val versionInfo = packageManager.getPackageInfo(packageName, 0)
                val text = buildString {
                    appendLine("time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now))
                    appendLine("thread: " + t.name)
                    appendLine("version: " + versionInfo.versionName + "(" + versionInfo.longVersionCode + ")")
                    appendLine()
                    appendLine("run.log snapshot:")
                    appendLine(readRunLog())
                    appendLine()
                    appendLine("crash:")
                    appendLine(Log.getStackTraceString(e))
                }
                val dir = File("/sdcard/BridgeFS/logs")
                val targetDir = if (dir.mkdirs() || dir.isDirectory) dir else File(getExternalFilesDir(null), "logs").apply { mkdirs() }
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
                File(targetDir, "crash_" + ts + ".log").writeText(text)
                File(targetDir, "latest.log").writeText(text)
            }.onFailure { Log.e("BridgeFS", "crash log failed", it) }
            default?.uncaughtException(t, e)
        }
    }
    private fun readRunLog(): String {
        val public = File("/sdcard/BridgeFS/logs/run.log")
        return runCatching {
            if (public.isFile) public.readLines().takeLast(500).joinToString("\n")
            else {
                val fallback = File(getExternalFilesDir(null), "logs/run.log")
                if (fallback.isFile) fallback.readLines().takeLast(500).joinToString("\n") else "run.log unavailable"
            }
        }.getOrDefault("run.log unavailable")
    }
}

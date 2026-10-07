package com.abridgefs.app

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Runtime diagnostics. Primary location is user-accessible shared storage. */
object AppLogger {
    private const val PRIVATE_DIR = "logs"
    private const val PUBLIC_ROOT = "APS/runtime"

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
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            val suffix = if (detail.isBlank()) "" else " | $detail"
            val line = "[$time] $event$suffix\n"
            if (!appendToPublicFile(context, "$PUBLIC_ROOT/${category.dir}/$day.log", line)) {
                val dir = File(context.filesDir, "$PRIVATE_DIR/${category.dir}").apply { mkdirs() }
                File(dir, "$day.log").appendText(line)
            }
        }
    }

    fun recordCrash(context: Context, throwable: Throwable) {
        runCatching {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS", Locale.US).format(Date())
            val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            val content = buildString {
                appendLine("time=$stamp")
                appendLine("package=${context.packageName}")
                appendLine("thread=${Thread.currentThread().name}")
                appendLine()
                append(stack)
            }
            val publicName = "$PUBLIC_ROOT/$CRASH_DIR/crash_$stamp.log"
            if (!createPublicFile(context, publicName, content)) {
                val dir = File(context.filesDir, CRASH_DIR).apply { mkdirs() }
                File(dir, "crash_$stamp.log").writeText(content)
            }
        }
    }

    private fun appendToPublicFile(context: Context, relativePath: String, content: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val direct = File(Environment.getExternalStorageDirectory(), relativePath)
        if (direct.parentFile?.exists() == true || direct.parentFile?.mkdirs() == true) {
            return runCatching {
                direct.appendText(content)
                true
            }.getOrDefault(false)
        }
        val uri = findDownloadFile(context, relativePath) ?: createDownloadFile(context, relativePath)
        return runCatching {
            if (uri == null) return false
            context.contentResolver.openOutputStream(uri, "wa")?.use {
                it.write(content.toByteArray(Charsets.UTF_8))
            } ?: return false
            true
        }.getOrDefault(false)
    }

    private fun createPublicFile(context: Context, relativePath: String, content: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val direct = File(Environment.getExternalStorageDirectory(), relativePath)
        if (direct.parentFile?.exists() == true || direct.parentFile?.mkdirs() == true) {
            return runCatching {
                direct.writeText(content)
                true
            }.getOrDefault(false)
        }
        val uri = createDownloadFile(context, relativePath) ?: return false
        return runCatching {
            context.contentResolver.openOutputStream(uri, "w")?.use {
                it.write(content.toByteArray(Charsets.UTF_8))
            } ?: return false
            true
        }.getOrDefault(false)
    }

    private fun findDownloadFile(context: Context, relativePath: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val (relativeDir, name) = splitRelativePath(relativePath)
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?"
        val args = arrayOf(name, "${Environment.DIRECTORY_DOWNLOADS}/$relativeDir")
        return context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            ContentUris.withAppendedId(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
            )
        }
    }

    private fun createDownloadFile(context: Context, relativePath: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val (relativeDir, name) = splitRelativePath(relativePath)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$relativeDir")
        }
        return runCatching {
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }.getOrNull()
    }

    private fun splitRelativePath(path: String): Pair<String, String> {
        val normalized = path.trimStart('/')
        val slash = normalized.lastIndexOf('/')
        return if (slash < 0) "" to normalized else normalized.substring(0, slash) to normalized.substring(slash + 1)
    }
}

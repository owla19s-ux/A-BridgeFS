package com.abridgefs.app

import android.app.*
import android.content.Intent
import android.os.IBinder
import java.io.File

class BridgeService : Service() {
    override fun onCreate() {
        super.onCreate()
        val c = NotificationChannel("bridge", "APS", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        startForeground(
            1,
            Notification.Builder(this, "bridge")
                .setContentTitle("APS")
                .setContentText("本地执行服务运行中")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build()
        )
    }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        val rootPath = i?.getStringExtra("root")
        val text = i?.getStringExtra("command")
        val projectId = i?.getStringExtra("projectId")
        val allowed = i?.getStringArrayListExtra("allowed")
            ?.mapNotNull { runCatching { FileAction.valueOf(it) }.getOrNull() }
            ?.toSet() ?: emptySet()
        val confirm = i?.getStringArrayListExtra("confirm")
            ?.mapNotNull { runCatching { FileAction.valueOf(it) }.getOrNull() }
            ?.toSet() ?: emptySet()

        val receipt = when {
            rootPath.isNullOrBlank() -> BridgeReceipt("FAILED", "", "未提供授权目录")
            text.isNullOrBlank() -> BridgeReceipt("FAILED", "", "未提供执行命令")
            else -> executeRequest(rootPath, text, allowed, confirm)
        }

        val result = Intent("com.bridgefs.RESULT").setPackage(packageName)
            .putExtra("status", receipt.status)
            .putExtra("command", receipt.command)
            .putExtra("message", receipt.message)
        if (projectId != null) result.putExtra("projectId", projectId)
        sendBroadcast(result)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun executeRequest(
        rootPath: String,
        text: String,
        allowed: Set<FileAction>,
        confirm: Set<FileAction>
    ): BridgeReceipt {
        val root = File(rootPath)
        if (!root.isDirectory) {
            return BridgeReceipt("FAILED", "", "授权目录不存在：$rootPath")
        }

        val auth = Authorization(rootPath, allowed, confirm)
        val commands = CommandParser.parse(text)
        if (commands.isEmpty()) {
            return BridgeReceipt("FAILED", text, "没有识别到可执行命令")
        }

        val denied = commands.firstOrNull { PermissionPolicy.check(it, auth) == Decision.DENY }
        if (denied != null) {
            return BridgeReceipt("DENIED", denied.toString(), "权限规则禁止该操作")
        }

        val waiting = commands.firstOrNull { PermissionPolicy.check(it, auth) == Decision.CONFIRM }
        if (waiting != null) {
            return BridgeReceipt("WAITING_CONFIRMATION", waiting.toString(), "该操作需要用户确认")
        }

        val results = commands.map { CommandExecutor(root, this).execute(it) }
        val message = results.joinToString("\n\n")
        val failed = results.any { it.contains("✗") }
        return BridgeReceipt(
            if (failed) "FAILED" else "SUCCEEDED",
            commands.joinToString(" | ") { it.toString() },
            message
        )
    }

    override fun onBind(i: Intent?): IBinder? = null
}

data class BridgeReceipt(val status: String, val command: String, val message: String)

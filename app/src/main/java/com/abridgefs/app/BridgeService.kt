package com.abridgefs.app

import android.app.*
import android.content.Intent
import android.os.IBinder
import java.io.File

class BridgeService:Service(){
    override fun onCreate(){
        super.onCreate()
        val c=NotificationChannel("bridge","A-BridgeFS",NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        startForeground(1,Notification.Builder(this,"bridge").setContentTitle("A-BridgeFS").setContentText("本地执行服务运行中").setSmallIcon(android.R.drawable.stat_notify_sync).build())
    }

    override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int{
        val rootPath=i?.getStringExtra("root")?:return START_NOT_STICKY
        val text=i.getStringExtra("command")?:return START_NOT_STICKY
        val projectId=i.getStringExtra("projectId")
        val root=File(rootPath)
        val allowed=i.getStringArrayListExtra("allowed")?.mapNotNull{runCatching{FileAction.valueOf(it)}.getOrNull()}?.toSet() ?: emptySet()
        val confirm=i.getStringArrayListExtra("confirm")?.mapNotNull{runCatching{FileAction.valueOf(it)}.getOrNull()}?.toSet() ?: emptySet()
        val auth=Authorization(rootPath,allowed,confirm)
        val commands=CommandParser.parse(text)
        val denied=commands.firstOrNull{PermissionPolicy.check(it,auth)==Decision.DENY}
        val waiting=commands.firstOrNull{PermissionPolicy.check(it,auth)==Decision.CONFIRM}
        val receipt=when{
            !root.isDirectory->BridgeReceipt("FAILED","", "授权目录不存在：$rootPath")
            denied!=null->BridgeReceipt("DENIED",denied.toString(),"权限规则禁止该操作")
            waiting!=null->BridgeReceipt("WAITING_CONFIRMATION",waiting.toString(),"该操作需要用户确认")
            else->BridgeReceipt("SUCCEEDED",commands.joinToString(" | "){it.toString()},commands.map{CommandExecutor(root).execute(it)}.joinToString("\n\n"))
        }
        val result=Intent("com.abridgefs.RESULT").setPackage(packageName)
            .putExtra("status",receipt.status)
            .putExtra("command",receipt.command)
            .putExtra("message",receipt.message)
        if(projectId!=null) result.putExtra("projectId",projectId)
        sendBroadcast(result)
        stopSelf(startId)
        return START_NOT_STICKY
    }
    override fun onBind(i:Intent?):IBinder?=null
}

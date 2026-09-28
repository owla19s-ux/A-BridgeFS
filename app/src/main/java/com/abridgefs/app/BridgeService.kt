package com.abridgefs.app
import android.app.*
import android.content.Intent
import android.os.IBinder
import java.io.File
class BridgeService:Service(){
 override fun onCreate(){super.onCreate();val c=NotificationChannel("bridge","A-BridgeFS",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager::class.java).createNotificationChannel(c);startForeground(1,Notification.Builder(this,"bridge").setContentTitle("A-BridgeFS").setContentText("本地执行服务运行中").setSmallIcon(android.R.drawable.stat_notify_sync).build())}
 override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int{val rootPath=i?.getStringExtra("root")?:return START_NOT_STICKY;val text=i.getStringExtra("command")?:return START_NOT_STICKY;val root=File(rootPath);val result=if(!root.isDirectory)"[BridgeFS] ✗ 授权目录不存在：$rootPath" else CommandParser.parse(text).map{CommandExecutor(root).execute(it)}.joinToString("\n\n");sendBroadcast(Intent("com.abridgefs.RESULT").setPackage(packageName).putExtra("result",result));stopSelf(startId);return START_NOT_STICKY}
 override fun onBind(i:Intent?):IBinder?=null
}

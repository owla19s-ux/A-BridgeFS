package com.abridgefs.app
import android.content.*
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
class MainActivity:AppCompatActivity(){
 private lateinit var rootInput:EditText;private lateinit var commandInput:EditText;private lateinit var resultView:TextView
 private val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){resultView.text=i.getStringExtra("result")?:""}}
 override fun onCreate(b:Bundle?){super.onCreate(b);val l=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24)};rootInput=EditText(this).apply{hint="授权目录";setText("/storage/emulated/0/A3/A0项目")};commandInput=EditText(this).apply{hint="BridgeFS操作，例如 [list]";minLines=4};resultView=TextView(this).apply{setPadding(0,24,0,0)};val run=Button(this).apply{text="执行 BridgeFS 操作";setOnClickListener{startForegroundService(Intent(this@MainActivity,BridgeService::class.java).putExtra("root",rootInput.text.toString().trim()).putExtra("command",commandInput.text.toString()))}};l.addView(rootInput);l.addView(commandInput);l.addView(run);l.addView(resultView);setContentView(ScrollView(this).apply{addView(l)});registerReceiver(receiver,IntentFilter("com.abridgefs.RESULT"),Context.RECEIVER_NOT_EXPORTED)}
 override fun onDestroy(){unregisterReceiver(receiver);super.onDestroy()}
}

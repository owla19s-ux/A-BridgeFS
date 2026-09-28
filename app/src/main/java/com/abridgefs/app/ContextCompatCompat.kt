package com.abridgefs.app
import android.content.Context
import android.content.Intent
import android.os.Build
object ContextCompatCompat { fun startService(context: Context, intent: Intent) { if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent) } }

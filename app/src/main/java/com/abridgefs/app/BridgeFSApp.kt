package com.abridgefs.app

import android.app.Application

class BridgeFSApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(BridgeFSCrashHandler(this, previous))
        AppLogger.log(this, "APP_START")
    }
}

package com.abridgefs.app

import android.content.Context

class BridgeFSCrashHandler(
    private val context: Context,
    private val previous: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        AppLogger.recordCrash(context, throwable)
        previous?.uncaughtException(thread, throwable)
    }
}

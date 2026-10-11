package com.abridgefs.app

import java.io.IOException
import org.junit.Test

class RuntimeDiagnosticsTest {
    @Test
    fun loggingBeforeApplicationInitializationDoesNotBreakCallers() {
        RuntimeDiagnostics.record("app.test", "started")
        RuntimeDiagnostics.recordApiEvent(
            event = "api.test",
            outcome = "failed",
            durationMs = 12,
            error = IOException("HTTP 401: provider returned an error")
        )
        RuntimeDiagnostics.recordCrash(
            Thread.currentThread(),
            IllegalStateException("exception details must not be copied into crash logs")
        )
    }
}

package com.abridgefs.app

import java.util.UUID

data class BridgeReceiptRecord(
    val status: String,
    val command: String,
    val message: String,
    val time: Long = System.currentTimeMillis(),
    val receiptId: String = UUID.randomUUID().toString()
)

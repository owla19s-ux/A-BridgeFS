package com.abridgefs.app.connection

interface Connection {
    val id: String
    val type: Type

    enum class Type {
        GITHUB,
        AI,
        LOCAL,
        FILE
    }
}

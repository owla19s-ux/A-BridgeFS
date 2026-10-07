package com.abridgefs.app.connection

sealed interface Connection {
    val id: String
    val type: Type

    enum class Type {
        GITHUB
    }
}

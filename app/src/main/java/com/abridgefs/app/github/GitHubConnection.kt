package com.abridgefs.app.github

import com.abridgefs.app.connection.Connection

data class GitHubConnection(
    override val id: String,
    val address: GitHubAddress
) : Connection {
    override val type: Connection.Type = Connection.Type.GITHUB

    init {
        require(id.isNotBlank()) { "Connection ID 不能为空" }
    }
}

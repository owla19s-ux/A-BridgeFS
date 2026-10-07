package com.abridgefs.app.github

import com.abridgefs.app.github.api.GitHubApi
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubConnectorTest {
    @Test
    fun connector_uses_connection_identity() {
        val connection = GitHubConnection(
            id = "github-main",
            address = GitHubAddress("owla19s-ux/APS", "main")
        )
        val api = Proxy.newProxyInstance(
            GitHubApi::class.java.classLoader,
            arrayOf(GitHubApi::class.java)
        ) { _, _, _ -> null } as GitHubApi

        val connector = GitHubConnector(api, connection)

        assertEquals(connection, connector.connection)
    }
}

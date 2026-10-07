package com.abridgefs.app.connection

import com.abridgefs.app.github.GitHubAddress
import com.abridgefs.app.github.GitHubConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTest {
    @Test
    fun github_connection_has_stable_identity_and_type() {
        val connection = GitHubConnection(
            id = "github-main",
            address = GitHubAddress("owla19s-ux/APS", "main")
        )

        assertEquals("github-main", connection.id)
        assertEquals(Connection.Type.GITHUB, connection.type)
        assertTrue(connection is Connection)
        assertEquals("owla19s-ux/APS", connection.address.repository)
    }

    @Test(expected = IllegalArgumentException::class)
    fun connection_rejects_blank_id() {
        GitHubConnection(
            id = " ",
            address = GitHubAddress("owla19s-ux/APS", "main")
        )
    }
}

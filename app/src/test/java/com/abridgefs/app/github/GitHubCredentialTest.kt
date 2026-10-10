package com.abridgefs.app.github

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubCredentialTest {
    @Test
    fun to_string_redacts_access_token() {
        val credential = GitHubCredential(
            login = "test-user",
            accessToken = "sensitive-github-token"
        )

        assertFalse(credential.toString().contains("sensitive-github-token"))
        assertTrue(credential.toString().contains("[redacted]"))
    }
}

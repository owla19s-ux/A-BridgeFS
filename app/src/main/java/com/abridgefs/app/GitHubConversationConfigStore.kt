package com.abridgefs.app

import android.content.Context

/**
 * Global GitHub binding used by ordinary conversations.
 *
 * Authentication is stored separately in GitHubTokenStore.
 * Repository/branch here are read-only conversation defaults and do not grant write access.
 */
data class GitHubConversationConfig(
    val repository: String?,
    val branch: String?
)

class GitHubConversationConfigStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("github_conversation", Context.MODE_PRIVATE)

    fun state(): GitHubConversationConfig = GitHubConversationConfig(
        prefs.getString("repository", null)?.takeIf { it.isNotBlank() },
        prefs.getString("branch", null)?.takeIf { it.isNotBlank() }
    )

    fun save(repository: String, branch: String?) {
        prefs.edit()
            .putString("repository", repository.trim())
            .putString("branch", branch?.trim()?.takeIf { it.isNotBlank() })
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}

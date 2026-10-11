package com.abridgefs.app.github

import android.content.Context

/** Non-secret repository selection for ordinary conversation read-only access. */
class GitHubConversationReadConfigStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): GitHubReadTarget? {
        val repository = preferences.getString(KEY_REPOSITORY, null)
            ?.takeIf { it.isNotBlank() } ?: return null
        return GitHubReadTarget(
            repository = repository,
            branch = preferences.getString(KEY_BRANCH, null)?.ifBlank { null }
        )
    }

    fun save(target: GitHubReadTarget) {
        val address = GitHubAddress(target.repository, target.branch?.trim()?.ifBlank { null })
        preferences.edit()
            .putString(KEY_REPOSITORY, address.repository.trim())
            .putString(KEY_BRANCH, address.branch.orEmpty())
            .apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_REPOSITORY).remove(KEY_BRANCH).apply()
    }

    private companion object {
        const val PREFERENCES = "github_conversation_read"
        const val KEY_REPOSITORY = "repository"
        const val KEY_BRANCH = "branch"
    }
}

package com.abridgefs.app.github

import android.content.Context
import com.abridgefs.app.github.api.GitHubApiFactory

class GitHubCredentialAuthenticator(context: Context) {
    private val store = GitHubCredentialStore(context)

    suspend fun verifyAndSave(credential: GitHubCredential): GitHubVerificationResult {
        val api = GitHubApiFactory.create(credential)
        val result = GitHubCredentialVerifier(api).verify()

        store.save(credential.copy(login = result.login))
        return result
    }

    fun load(): GitHubCredential? = store.load()

    fun clear() = store.clear()
}

package com.abridgefs.app.github

import com.abridgefs.app.github.api.GitHubApi

data class GitHubVerificationResult(
    val login: String,
    val repositoryCount: Int
)

class GitHubCredentialVerifier(
    private val api: GitHubApi
) {
    suspend fun verify(): GitHubVerificationResult {
        val userResponse = api.currentUser()
        check(userResponse.isSuccessful) {
            "GitHub 账号验证失败：HTTP " + userResponse.code()
        }

        val login = userResponse.body()
            ?.get("login")
            ?.takeIf { !it.isJsonNull }
            ?.asString
            ?.takeIf { it.isNotBlank() }
            ?: error("GitHub 账号验证失败：未返回 login")

        val repositoriesResponse = api.repositories()
        check(repositoriesResponse.isSuccessful) {
            "GitHub Repository 访问验证失败：HTTP " + repositoriesResponse.code()
        }

        return GitHubVerificationResult(
            login = login,
            repositoryCount = repositoriesResponse.body()?.size ?: 0
        )
    }
}

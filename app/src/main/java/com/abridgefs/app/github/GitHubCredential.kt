package com.abridgefs.app.github

data class GitHubCredential(
    val login: String,
    val accessToken: String,
    val type: Type = Type.PERSONAL_ACCESS_TOKEN
) {
    enum class Type {
        PERSONAL_ACCESS_TOKEN
    }

    override fun toString(): String =
        "GitHubCredential(login=$login, accessToken=[redacted], type=$type)"
}

package com.abridgefs.app.github

data class GitHubAddress(
    val repository: String,
    val branch: String? = null
) {
    init {
        val parts = repository.trim().split("/", limit = 2)
        require(parts.size == 2 && parts.all { it.isNotBlank() }) {
            "GitHub Repository 格式应为 owner/name"
        }
    }

    val owner: String
        get() = repository.trim().substringBefore('/')

    val repo: String
        get() = repository.trim().substringAfter('/')
}

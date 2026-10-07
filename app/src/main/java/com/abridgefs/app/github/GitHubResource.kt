package com.abridgefs.app.github

sealed interface GitHubResource {
    data object Repository : GitHubResource
    data class Branch(val name: String) : GitHubResource
    data class File(val path: String) : GitHubResource
    data class Commit(val ref: String) : GitHubResource
}

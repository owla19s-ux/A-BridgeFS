package com.abridgefs.app.github.api

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class GitHubConcurrentReader(private val api: GitHubApi) {
    suspend fun loadRepositorySnapshot(owner: String, repo: String, branch: String, paths: List<String>): Snapshot = coroutineScope {
        val repository = async { api.repository(owner, repo) }
        val branchInfo = async { api.branch(owner, repo, branch) }
        val commit = async { api.commit(owner, repo, branch) }
        val files = paths.map { path -> async { path to api.file(owner, repo, path, branch) } }
        val fileResults = files.awaitAll().toMap()
        Snapshot(repository.await(), branchInfo.await(), commit.await(), fileResults)
    }
}

data class Snapshot(
    val repository: retrofit2.Response<com.google.gson.JsonObject>,
    val branch: retrofit2.Response<com.google.gson.JsonObject>,
    val commit: retrofit2.Response<com.google.gson.JsonObject>,
    val files: Map<String, retrofit2.Response<com.google.gson.JsonObject>>
)

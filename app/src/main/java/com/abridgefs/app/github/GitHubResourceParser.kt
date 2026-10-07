package com.abridgefs.app.github

import com.google.gson.JsonObject

object GitHubResourceParser {
    fun repository(json: JsonObject): GitHubResource.Repository {
        val id = json.requiredLong("id")
        val fullName = json.requiredString("full_name")
        val defaultBranch = json.optionalString("default_branch")

        return GitHubResource.Repository(
            id = id,
            fullName = fullName,
            defaultBranch = defaultBranch
        )
    }

    fun branch(json: JsonObject, repositoryId: Long): GitHubResource.Branch =
        GitHubResource.Branch(
            repositoryId = repositoryId,
            name = json.requiredString("name")
        )

    private fun JsonObject.requiredLong(name: String): Long =
        get(name)?.takeIf { !it.isJsonNull }?.asLong
            ?: error("GitHub API 缺少字段：$name")

    private fun JsonObject.requiredString(name: String): String =
        get(name)?.takeIf { !it.isJsonNull }?.asString
            ?.takeIf { it.isNotBlank() }
            ?: error("GitHub API 缺少字段：$name")

    private fun JsonObject.optionalString(name: String): String? =
        get(name)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
}

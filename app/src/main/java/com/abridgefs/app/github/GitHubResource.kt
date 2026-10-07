package com.abridgefs.app.github

sealed interface GitHubResource {
    data class Repository(
        val id: Long,
        val fullName: String,
        val defaultBranch: String? = null
    ) : GitHubResource {
        init {
            require(id > 0) { "GitHub Repository ID 必须有效" }
            require(fullName.trim().split("/", limit = 2).size == 2) {
                "GitHub Repository 格式应为 owner/name"
            }
        }
    }

    data class Branch(
        val repositoryId: Long,
        val name: String
    ) : GitHubResource {
        init {
            require(repositoryId > 0) { "GitHub Repository ID 必须有效" }
            require(name.isNotBlank()) { "GitHub Branch 不能为空" }
        }
    }

    data class File(val path: String) : GitHubResource

    data class Commit(val ref: String) : GitHubResource
}

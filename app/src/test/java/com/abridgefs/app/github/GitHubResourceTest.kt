package com.abridgefs.app.github

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubResourceTest {
    @Test
    fun repository_keeps_stable_id_and_identity() {
        val resource = GitHubResource.Repository(
            id = 177,
            fullName = "owla19s-ux/APS",
            defaultBranch = "main"
        )

        assertEquals(177, resource.id)
        assertEquals("owla19s-ux/APS", resource.fullName)
        assertEquals("main", resource.defaultBranch)
    }

    @Test
    fun branch_is_associated_with_repository() {
        val resource = GitHubResource.Branch(
            repositoryId = 177,
            name = "main"
        )

        assertEquals(177, resource.repositoryId)
        assertEquals("main", resource.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun repository_rejects_invalid_identity() {
        GitHubResource.Repository(id = 177, fullName = "APS")
    }

    @Test(expected = IllegalArgumentException::class)
    fun branch_rejects_blank_name() {
        GitHubResource.Branch(repositoryId = 177, name = " ")
    }
}

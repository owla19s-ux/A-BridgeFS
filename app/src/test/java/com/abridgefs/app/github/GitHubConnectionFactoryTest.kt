package com.abridgefs.app.github

import com.abridgefs.app.context.Context
import com.abridgefs.app.context.ResourceRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GitHubConnectionFactoryTest {
    @Test
    fun repository_resource_uses_default_branch() {
        val context = Context("ctx-1", "APS").withResource(
            ResourceRef.GitHub(
                GitHubResource.Repository(177, "owla19s-ux/APS", "main")
            )
        )

        val connection = GitHubConnectionFactory.create(context)

        assertEquals("github:repository:177", connection.id)
        assertEquals(GitHubAddress("owla19s-ux/APS", "main"), connection.address)
    }

    @Test
    fun branch_resource_overrides_repository_default_branch() {
        val repository = GitHubResource.Repository(177, "owla19s-ux/APS", "main")
        val branch = GitHubResource.Branch(177, "develop")
        val context = Context("ctx-1", "APS")
            .withResource(ResourceRef.GitHub(repository))
            .withResource(ResourceRef.GitHub(branch))

        val connection = GitHubConnectionFactory.create(context)

        assertEquals(GitHubAddress("owla19s-ux/APS", "develop"), connection.address)
    }

    @Test
    fun branch_resource_for_another_repository_is_ignored() {
        val context = Context("ctx-1", "APS")
            .withResource(
                ResourceRef.GitHub(
                    GitHubResource.Repository(177, "owla19s-ux/APS", "main")
                )
            )
            .withResource(
                ResourceRef.GitHub(
                    GitHubResource.Branch(178, "other")
                )
            )

        val connection = GitHubConnectionFactory.create(context)

        assertEquals(GitHubAddress("owla19s-ux/APS", "main"), connection.address)
    }

    @Test
    fun repository_without_default_branch_leaves_branch_unset() {
        val context = Context("ctx-1", "APS").withResource(
            ResourceRef.GitHub(
                GitHubResource.Repository(177, "owla19s-ux/APS")
            )
        )

        val connection = GitHubConnectionFactory.create(context)

        assertEquals(GitHubAddress("owla19s-ux/APS", null), connection.address)
    }

    @Test
    fun empty_context_is_rejected() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            GitHubConnectionFactory.create(Context("ctx-1", "APS"))
        }
        assertEquals("Context 未关联 GitHub Repository", error.message)
    }

    @Test
    fun multiple_repositories_are_rejected() {
        val context = Context("ctx-1", "APS")
            .withResource(ResourceRef.GitHub(GitHubResource.Repository(177, "owla19s-ux/APS")))
            .withResource(ResourceRef.GitHub(GitHubResource.Repository(178, "other/repo")))

        assertThrows(IllegalArgumentException::class.java) {
            GitHubConnectionFactory.create(context)
        }
    }

    @Test
    fun multiple_branches_for_repository_are_rejected() {
        val context = Context("ctx-1", "APS")
            .withResource(ResourceRef.GitHub(GitHubResource.Repository(177, "owla19s-ux/APS", "main")))
            .withResource(ResourceRef.GitHub(GitHubResource.Branch(177, "develop")))
            .withResource(ResourceRef.GitHub(GitHubResource.Branch(177, "feature")))

        assertThrows(IllegalArgumentException::class.java) {
            GitHubConnectionFactory.create(context)
        }
    }
}

package com.abridgefs.app.github

import com.abridgefs.app.context.Context
import com.abridgefs.app.context.ResourceRef
import org.junit.Assert.assertEquals
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

    @Test(expected = IllegalArgumentException::class)
    fun multiple_repositories_are_rejected() {
        val context = Context("ctx-1", "APS")
            .withResource(ResourceRef.GitHub(GitHubResource.Repository(177, "owla19s-ux/APS")))
            .withResource(ResourceRef.GitHub(GitHubResource.Repository(178, "other/repo")))

        GitHubConnectionFactory.create(context)
    }

    @Test(expected = IllegalArgumentException::class)
    fun multiple_branches_for_repository_are_rejected() {
        val context = Context("ctx-1", "APS")
            .withResource(ResourceRef.GitHub(GitHubResource.Repository(177, "owla19s-ux/APS", "main")))
            .withResource(ResourceRef.GitHub(GitHubResource.Branch(177, "develop")))
            .withResource(ResourceRef.GitHub(GitHubResource.Branch(177, "feature")))

        GitHubConnectionFactory.create(context)
    }
}

package com.abridgefs.app.context

import com.abridgefs.app.github.GitHubResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextTest {
    @Test
    fun context_can_associate_github_repository() {
        val repository = GitHubResource.Repository(
            id = 177,
            fullName = "owla19s-ux/APS",
            defaultBranch = "main"
        )

        val context = Context("ctx-1", "APS").withResource(ResourceRef.GitHub(repository))

        assertEquals(1, context.resources.size)
        assertEquals("github:repository:177", context.resources.single().key)
    }

    @Test
    fun same_resource_replacement_does_not_duplicate() {
        val repository = GitHubResource.Repository(
            id = 177,
            fullName = "owla19s-ux/APS",
            defaultBranch = "main"
        )
        val first = ResourceRef.GitHub(repository)
        val second = ResourceRef.GitHub(repository)

        val context = Context("ctx-1", "APS")
            .withResource(first)
            .withResource(second)

        assertEquals(1, context.resources.size)
        assertTrue(context.resources.single() === second || context.resources.single() == second)
    }

    @Test(expected = IllegalArgumentException::class)
    fun context_requires_id() {
        Context("", "APS")
    }
}

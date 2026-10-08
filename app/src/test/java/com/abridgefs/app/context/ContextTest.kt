package com.abridgefs.app.context

import com.abridgefs.app.github.GitHubResource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ContextTest {
    @Test
    fun context_can_associate_github_repository() {
        val context = Context("ctx-1", "APS")
            .withResource(
                ResourceRef.GitHub(
                    GitHubResource.Repository(177, "owla19s-ux/APS", "main")
                )
            )

        assertEquals(1, context.resources.size)
        assertEquals("github:repository:177", context.resources.single().key)
    }

    @Test
    fun same_resource_replacement_does_not_duplicate() {
        val first = ResourceRef.GitHub(
            GitHubResource.Repository(177, "owla19s-ux/APS", "main")
        )
        val second = ResourceRef.GitHub(
            GitHubResource.Repository(177, "owla19s-ux/APS", "develop")
        )

        val context = Context("ctx-1", "APS")
            .withResource(first)
            .withResource(second)

        assertEquals(1, context.resources.size)
        assertEquals("github:repository:177", context.resources.single().key)
    }

    @Test
    fun context_can_bind_ai_connection() {
        val context = Context("ctx-1", "APS")
            .withAIConnection("ai-1")

        assertEquals("ai-1", context.aiConnectionId)
    }

    @Test
    fun context_without_ai_connection_is_valid() {
        val context = Context("ctx-1", "APS")

        assertFalse(context.aiConnectionId != null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun empty_id_is_rejected() {
        Context("", "APS")
    }
}

package com.abridgefs.app.github

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubResourceParserTest {
    @Test
    fun repository_maps_api_fields() {
        val resource = GitHubResourceParser.repository(
            JsonParser.parseString(
                """{"id":177,"full_name":"owla19s-ux/APS","default_branch":"main"}"""
            ).asJsonObject
        )

        assertEquals(177, resource.id)
        assertEquals("owla19s-ux/APS", resource.fullName)
        assertEquals("main", resource.defaultBranch)
    }

    @Test
    fun branch_maps_api_name_to_repository() {
        val resource = GitHubResourceParser.branch(
            JsonParser.parseString("""{"name":"feature/test"}""").asJsonObject,
            repositoryId = 177
        )

        assertEquals(177, resource.repositoryId)
        assertEquals("feature/test", resource.name)
    }

    @Test(expected = IllegalStateException::class)
    fun repository_requires_id() {
        GitHubResourceParser.repository(
            JsonParser.parseString(
                """{"full_name":"owla19s-ux/APS","default_branch":"main"}"""
            ).asJsonObject
        )
    }
}

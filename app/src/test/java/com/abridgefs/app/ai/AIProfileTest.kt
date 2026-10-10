package com.abridgefs.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AIProfileTest {
    @Test
    fun normalized_base_url_removes_trailing_slashes() {
        val profile = AIProfile(
            name = "Test API",
            baseUrl = "https://api.example.com/v1///",
            model = "model-x",
            apiKey = "test-secret"
        )

        assertEquals("https://api.example.com/v1", profile.normalizedBaseUrl)
    }

    @Test
    fun to_string_redacts_api_key() {
        val profile = AIProfile(
            name = "Test API",
            baseUrl = "https://api.example.com/v1",
            model = "model-x",
            apiKey = "sensitive-api-key"
        )

        assertFalse(profile.toString().contains("sensitive-api-key"))
        assertTrue(profile.toString().contains("[redacted]"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blank_model_is_rejected() {
        AIProfile(
            name = "Test API",
            baseUrl = "https://api.example.com/v1",
            model = " ",
            apiKey = "test-secret"
        )
    }
}

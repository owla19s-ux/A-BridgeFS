package com.abridgefs.app.ai

import org.junit.Assert.assertEquals
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

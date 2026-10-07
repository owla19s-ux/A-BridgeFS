package com.abridgefs.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ApiProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val key: String,
    val model: String,
    val avatar: String = ""
)

class ApiProfileStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("bridgefs", 0)
    private val secrets = ApiSecretStore(context)

    fun list(): List<ApiProfile> {
        val raw = prefs.getString("api_profiles", null) ?: return legacy()
        val arr = JSONArray(raw)
        return List(arr.length()) { index ->
            val item = arr.getJSONObject(index)
            val id = item.getString("id")
            val legacyKey = item.optString("key", "")
            if (legacyKey.isNotBlank()) secrets.put(id, legacyKey)
            ApiProfile(
                id = id,
                name = item.optString("name"),
                baseUrl = item.optString("baseUrl"),
                key = secrets.get(id).orEmpty(),
                model = item.optString("model"),
                avatar = item.optString("avatar")
            )
        }
    }

    fun find(id: String?): ApiProfile? = list().firstOrNull { it.id == id }

    fun save(profile: ApiProfile) {
        secrets.put(profile.id, profile.key)
        saveAll(list().filterNot { it.id == profile.id } + profile.copy(key = ""))
    }

    fun remove(id: String) {
        secrets.remove(id)
        saveAll(list().filterNot { it.id == id })
    }

    private fun saveAll(items: List<ApiProfile>) {
        prefs.edit().putString("api_profiles", JSONArray().apply {
            items.forEach {
                put(JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("avatar", it.avatar)
                    .put("baseUrl", it.baseUrl)
                    .put("model", it.model))
            }
        }.toString()).apply()
    }

    private fun legacy(): List<ApiProfile> {
        val url = prefs.getString("api_base_url", "").orEmpty()
        val model = prefs.getString("api_model", "").orEmpty()
        if (url.isBlank() && model.isBlank()) return emptyList()
        val profile = ApiProfile(
            id = "legacy",
            name = prefs.getString("api_provider", "API") ?: "API",
            baseUrl = url,
            key = prefs.getString("api_key", "").orEmpty(),
            model = model,
            avatar = "AI"
        )
        secrets.put(profile.id, profile.key)
        saveAll(listOf(profile.copy(key = "")))
        return listOf(profile)
    }
}

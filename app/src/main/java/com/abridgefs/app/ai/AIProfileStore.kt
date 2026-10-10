package com.abridgefs.app.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persists multiple API profiles. Profile metadata is stored separately from encrypted API keys.
 * The former single-profile preferences are migrated once and removed only after a successful write.
 */
class AIProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /** Returns all readable profiles in stable insertion order. */
    fun loadAll(): List<AIProfile> {
        migrateLegacyProfileIfNeeded()
        val metadata = prefs.getString(KEY_PROFILES_JSON, null) ?: return emptyList()
        val array = runCatching { JSONArray(metadata) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = runCatching { array.getJSONObject(index) }.getOrNull() ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val encryptedKey = prefs.getString(secretPreferenceKey(id), null) ?: continue
                val apiKey = decrypt(encryptedKey, profileKeyAlias(id)) ?: continue
                val profile = runCatching {
                    AIProfile(
                        id = id,
                        name = item.getString("name"),
                        baseUrl = item.getString("baseUrl"),
                        model = item.getString("model"),
                        apiKey = apiKey
                    )
                }.getOrNull() ?: continue
                add(profile)
            }
        }
    }

    /** Loads the explicitly selected default, falling back to the first readable profile. */
    fun load(): AIProfile? {
        val profiles = loadAll()
        val preferredId = prefs.getString(KEY_DEFAULT_PROFILE_ID, null)
        return profiles.firstOrNull { it.id == preferredId } ?: profiles.firstOrNull()
    }

    fun load(id: String): AIProfile? = loadAll().firstOrNull { it.id == id }

    fun defaultProfileId(): String? {
        val profiles = loadAll()
        return prefs.getString(KEY_DEFAULT_PROFILE_ID, null)
            ?.takeIf { id -> profiles.any { it.id == id } }
            ?: profiles.firstOrNull()?.id
    }

    /** Saves or replaces a profile without making it default unless requested or none exists. */
    fun save(profile: AIProfile, makeDefault: Boolean = false) {
        val existing = loadAll().toMutableList()
        val oldIndex = existing.indexOfFirst { it.id == profile.id }
        if (oldIndex >= 0) existing[oldIndex] = profile else existing.add(profile)

        val encryptedKey = encrypt(profile.apiKey, profileKeyAlias(profile.id))
        val editor = prefs.edit()
            .putString(secretPreferenceKey(profile.id), encryptedKey)
            .putString(KEY_PROFILES_JSON, encodeMetadata(existing))
        if (makeDefault || prefs.getString(KEY_DEFAULT_PROFILE_ID, null).isNullOrBlank()) {
            editor.putString(KEY_DEFAULT_PROFILE_ID, profile.id)
        }
        check(editor.commit()) { "API Profile 保存失败" }
    }

    fun setDefault(id: String) {
        require(loadAll().any { it.id == id }) { "找不到要设为默认的 API 连接" }
        check(prefs.edit().putString(KEY_DEFAULT_PROFILE_ID, id).commit()) {
            "默认 API 连接保存失败"
        }
    }

    fun delete(id: String) {
        val profiles = loadAll()
        val target = profiles.firstOrNull { it.id == id } ?: return
        val remaining = profiles.filterNot { it.id == id }
        val editor = prefs.edit()
            .remove(secretPreferenceKey(id))
            .putString(KEY_PROFILES_JSON, encodeMetadata(remaining))
        if (prefs.getString(KEY_DEFAULT_PROFILE_ID, null) == id) {
            if (remaining.isEmpty()) editor.remove(KEY_DEFAULT_PROFILE_ID)
            else editor.putString(KEY_DEFAULT_PROFILE_ID, remaining.first().id)
        }
        check(editor.commit()) { "API Profile 删除失败" }
        deleteKeyAlias(profileKeyAlias(target.id))
    }

    fun clear() {
        val profiles = loadAll()
        check(
            prefs.edit()
                .remove(KEY_PROFILES_JSON)
                .remove(KEY_DEFAULT_PROFILE_ID)
                .remove(KEY_NAME)
                .remove(KEY_BASE_URL)
                .remove(KEY_MODEL)
                .remove(KEY_API_KEY)
                .also { editor -> profiles.forEach { editor.remove(secretPreferenceKey(it.id)) } }
                .commit()
        ) { "API Profile 删除失败" }
        profiles.forEach { deleteKeyAlias(profileKeyAlias(it.id)) }
        deleteKeyAlias(LEGACY_KEY_ALIAS)
    }

    private fun migrateLegacyProfileIfNeeded() {
        if (prefs.contains(KEY_PROFILES_JSON)) return
        val name = prefs.getString(KEY_NAME, null) ?: return
        val baseUrl = prefs.getString(KEY_BASE_URL, null) ?: return
        val model = prefs.getString(KEY_MODEL, null) ?: return
        val oldEncryptedKey = prefs.getString(KEY_API_KEY, null) ?: return
        val apiKey = decrypt(oldEncryptedKey, LEGACY_KEY_ALIAS) ?: return
        val profile = runCatching {
            AIProfile(
                id = AIProfile.DEFAULT_ID,
                name = name,
                baseUrl = baseUrl,
                model = model,
                apiKey = apiKey
            )
        }.getOrNull() ?: return

        val encryptedKey = encrypt(profile.apiKey, profileKeyAlias(profile.id))
        val committed = prefs.edit()
            .putString(secretPreferenceKey(profile.id), encryptedKey)
            .putString(KEY_PROFILES_JSON, encodeMetadata(listOf(profile)))
            .putString(KEY_DEFAULT_PROFILE_ID, profile.id)
            .remove(KEY_NAME)
            .remove(KEY_BASE_URL)
            .remove(KEY_MODEL)
            .remove(KEY_API_KEY)
            .commit()
        check(committed) { "旧 API 配置迁移失败，原配置已保留" }
        deleteKeyAlias(LEGACY_KEY_ALIAS)
    }

    private fun encodeMetadata(profiles: List<AIProfile>): String =
        JSONArray().apply {
            profiles.forEach { profile ->
                put(JSONObject().apply {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("baseUrl", profile.normalizedBaseUrl)
                    put("model", profile.model)
                })
            }
        }.toString()

    private fun profileKeyAlias(id: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "$KEY_ALIAS_PREFIX.$digest"
    }

    private fun secretPreferenceKey(id: String): String = "$KEY_SECRET_PREFIX$id"

    private fun key(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String, alias: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(alias))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(
            cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)),
            Base64.NO_WRAP
        )
        return "$iv:$data"
    }

    private fun decrypt(value: String, alias: String): String? = runCatching {
        val parts = value.split(":", limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(alias),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8)
    }.getOrNull()

    private fun deleteKeyAlias(alias: String) {
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }

    private companion object {
        const val PREFERENCES = "ai_profile"
        const val KEY_ALIAS_PREFIX = "aps.ai.profile"
        const val LEGACY_KEY_ALIAS = "aps.ai.profile"
        const val KEY_PROFILES_JSON = "profiles_json"
        const val KEY_DEFAULT_PROFILE_ID = "default_profile_id"
        const val KEY_SECRET_PREFIX = "api_key."
        const val KEY_NAME = "name"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_API_KEY = "api_key"
    }
}

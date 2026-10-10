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

    /** Returns all readable profiles in stable insertion order. Unreadable metadata is retained. */
    fun loadAll(): List<AIProfile> {
        migrateLegacyProfileIfNeeded()
        return readMetadata().mapNotNull { item ->
            val encryptedKey = prefs.getString(secretPreferenceKey(item.id), null) ?: return@mapNotNull null
            val apiKey = decrypt(encryptedKey, profileKeyAlias(item.id)) ?: return@mapNotNull null
            runCatching { AIProfile(item.id, item.name, item.baseUrl, item.model, apiKey) }.getOrNull()
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
        migrateLegacyProfileIfNeeded()
        val metadata = readMetadata()
        return prefs.getString(KEY_DEFAULT_PROFILE_ID, null)
            ?.takeIf { id -> metadata.any { it.id == id } }
            ?: metadata.firstOrNull()?.id
    }

    /** Saves or replaces a profile without making it default unless requested or none exists. */
    fun save(profile: AIProfile, makeDefault: Boolean = false) {
        migrateLegacyProfileIfNeeded()
        val metadata = readMetadata().toMutableList()
        val replacement = ProfileMetadata(profile.id, profile.name, profile.normalizedBaseUrl, profile.model)
        val oldIndex = metadata.indexOfFirst { it.id == profile.id }
        if (oldIndex >= 0) metadata[oldIndex] = replacement else metadata.add(replacement)
        val encryptedKey = encrypt(profile.apiKey, profileKeyAlias(profile.id))
        val editor = prefs.edit()
            .putString(secretPreferenceKey(profile.id), encryptedKey)
            .putString(KEY_PROFILES_JSON, encodeMetadata(metadata))
        val currentDefault = prefs.getString(KEY_DEFAULT_PROFILE_ID, null)
        if (makeDefault || currentDefault.isNullOrBlank() || metadata.none { it.id == currentDefault }) {
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
        migrateLegacyProfileIfNeeded()
        val metadata = readMetadata()
        if (metadata.none { it.id == id } && !prefs.contains(secretPreferenceKey(id))) return
        val remaining = metadata.filterNot { it.id == id }
        val editor = prefs.edit()
            .remove(secretPreferenceKey(id))
            .putString(KEY_PROFILES_JSON, encodeMetadata(remaining))
        if (prefs.getString(KEY_DEFAULT_PROFILE_ID, null) == id) {
            if (remaining.isEmpty()) editor.remove(KEY_DEFAULT_PROFILE_ID)
            else editor.putString(KEY_DEFAULT_PROFILE_ID, remaining.first().id)
        }
        check(editor.commit()) { "API Profile 删除失败" }
        deleteKeyAlias(profileKeyAlias(id))
    }

    fun clear() {
        migrateLegacyProfileIfNeeded()
        val ids = (readMetadata().map { it.id } + prefs.all.keys
            .filter { it.startsWith(KEY_SECRET_PREFIX) }
            .map { it.removePrefix(KEY_SECRET_PREFIX) }).toSet()
        val editor = prefs.edit()
            .remove(KEY_PROFILES_JSON)
            .remove(KEY_DEFAULT_PROFILE_ID)
            .remove(KEY_NAME)
            .remove(KEY_BASE_URL)
            .remove(KEY_MODEL)
            .remove(KEY_API_KEY)
        ids.forEach { editor.remove(secretPreferenceKey(it)) }
        check(editor.commit()) { "API Profile 删除失败" }
        ids.forEach { deleteKeyAlias(profileKeyAlias(it)) }
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

    private data class ProfileMetadata(val id: String, val name: String, val baseUrl: String, val model: String)

    /** Reads metadata independently of secret decryption so unreadable profiles are not lost. */
    private fun readMetadata(): List<ProfileMetadata> {
        val raw = prefs.getString(KEY_PROFILES_JSON, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = runCatching { array.getJSONObject(index) }.getOrNull() ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                val baseUrl = item.optString("baseUrl").takeIf { it.isNotBlank() } ?: continue
                val model = item.optString("model").takeIf { it.isNotBlank() } ?: continue
                add(ProfileMetadata(id, name, baseUrl, model))
            }
        }
    }

    private fun encodeMetadata(profiles: List<ProfileMetadata>): String =
        JSONArray().apply {
            profiles.forEach { profile ->
                put(JSONObject().apply {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("baseUrl", profile.baseUrl.trim().trimEnd('/'))
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

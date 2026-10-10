package com.abridgefs.app.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Persists non-secret profile fields in preferences and encrypts the API key with Android Keystore. */
class AIProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): AIProfile? {
        val name = prefs.getString(KEY_NAME, null) ?: return null
        val baseUrl = prefs.getString(KEY_BASE_URL, null) ?: return null
        val model = prefs.getString(KEY_MODEL, null) ?: return null
        val encryptedKey = prefs.getString(KEY_API_KEY, null) ?: return null
        val apiKey = decrypt(encryptedKey) ?: return null
        return runCatching {
            AIProfile(name = name, baseUrl = baseUrl, model = model, apiKey = apiKey)
        }.getOrNull()
    }

    fun save(profile: AIProfile) {
        val encryptedKey = encrypt(profile.apiKey)
        check(
            prefs.edit()
                .putString(KEY_NAME, profile.name)
                .putString(KEY_BASE_URL, profile.normalizedBaseUrl)
                .putString(KEY_MODEL, profile.model)
                .putString(KEY_API_KEY, encryptedKey)
                .commit()
        ) { "API Profile 保存失败" }
    }

    fun clear() {
        check(prefs.edit().clear().commit()) { "API Profile 删除失败" }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(
            cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)),
            Base64.NO_WRAP
        )
        return "$iv:$data"
    }

    private fun decrypt(value: String): String? = runCatching {
        val parts = value.split(":", limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val PREFERENCES = "ai_profile"
        const val KEY_ALIAS = "aps.ai.profile"
        const val KEY_NAME = "name"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_API_KEY = "api_key"
    }
}

package com.abridgefs.app.github

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class GitHubCredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val keyAlias = "aps.github.credential"

    fun load(): GitHubCredential? {
        val login = prefs.getString(KEY_LOGIN, null) ?: return null
        val encryptedToken = prefs.getString(KEY_TOKEN, null) ?: return null
        val token = decrypt(encryptedToken) ?: return null
        val type = prefs.getString(KEY_TYPE, null)
            ?.let { runCatching { GitHubCredential.Type.valueOf(it) }.getOrNull() }
            ?: return null
        return GitHubCredential(login, token, type)
    }

    fun save(credential: GitHubCredential) {
        prefs.edit()
            .putString(KEY_LOGIN, credential.login)
            .putString(KEY_TOKEN, encrypt(credential.accessToken))
            .putString(KEY_TYPE, credential.type.name)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
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
        String(
            cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)),
            StandardCharsets.UTF_8
        )
    }.getOrNull()

    private companion object {
        const val PREFERENCES = "github_credential"
        const val KEY_LOGIN = "login"
        const val KEY_TOKEN = "token"
        const val KEY_TYPE = "credential_type"
    }
}

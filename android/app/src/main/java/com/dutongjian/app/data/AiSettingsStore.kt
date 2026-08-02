package com.dutongjian.app.data

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

data class StoredAiSettings(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
)

class AiSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): StoredAiSettings = StoredAiSettings(
        baseUrl = preferences.getString(KEY_BASE_URL, DEFAULT_BASE_URL).orEmpty(),
        model = preferences.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty(),
        apiKey = decrypt(preferences.getString(KEY_API_KEY, null)),
    )

    fun save(baseUrl: String, model: String, apiKey: String?) {
        val editor = preferences.edit()
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_MODEL, model)
        if (apiKey != null) {
            if (apiKey.isBlank()) editor.remove(KEY_API_KEY)
            else editor.putString(KEY_API_KEY, encrypt(apiKey))
        }
        editor.apply()
    }

    fun clearApiKey() {
        preferences.edit().remove(KEY_API_KEY).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching {
            val payload = Base64.decode(value, Base64.NO_WRAP)
            val iv = payload.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = payload.copyOfRange(GCM_IV_LENGTH, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).apply {
                init(android.security.keystore.KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build())
            }.generateKey()
        }
        return (keyStore.getKey(KEY_ALIAS, null) as SecretKey)
    }
}

private const val PREFERENCES_NAME = "ai_settings"
private const val KEY_ALIAS = "dutongjian_ai_key"
private const val KEY_BASE_URL = "base_url"
private const val KEY_MODEL = "model"
private const val KEY_API_KEY = "api_key"
private const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
private const val DEFAULT_MODEL = "gpt-4o-mini"
private const val ANDROID_KEY_STORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_IV_LENGTH = 12
private const val GCM_TAG_LENGTH = 128

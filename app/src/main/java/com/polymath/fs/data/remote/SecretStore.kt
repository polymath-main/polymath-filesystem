package com.polymath.fs.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecretStore @Inject constructor(
    private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun storeSecret(alias: String, secret: String) {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv ?: ByteArray(12).apply { java.security.SecureRandom().nextBytes(this) }
        val encryptedBytes = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(1 + iv.size + encryptedBytes.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, 1 + iv.size, encryptedBytes.size)

        val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
        prefs.edit().putString(alias, encoded).apply()
    }

    @Synchronized
    fun getSecret(alias: String): String? {
        val encoded = prefs.getString(alias, null) ?: return null
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.isEmpty()) return null
            val ivLength = combined[0].toInt() and 0xFF
            if (combined.size < 1 + ivLength) return null

            val iv = ByteArray(ivLength)
            System.arraycopy(combined, 1, iv, 0, ivLength)
            val cipherTextLength = combined.size - 1 - ivLength
            val cipherText = ByteArray(cipherTextLength)
            System.arraycopy(combined, 1 + ivLength, cipherText, 0, cipherTextLength)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun deleteSecret(alias: String) {
        prefs.edit().remove(alias).apply()
    }

    fun hasSecret(alias: String): Boolean {
        return prefs.contains(alias)
    }

    fun encrypt(alias: String, secret: String) = storeSecret(alias, secret)
    fun decrypt(alias: String): String? = getSecret(alias)

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (entry != null) {
                    return entry.secretKey
                }
            }

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Throwable) {
            getFallbackSoftwareKey()
        }
    }

    private fun getFallbackSoftwareKey(): SecretKey {
        val fallbackRaw = prefs.getString(FALLBACK_KEY_PREF, null)
        if (fallbackRaw != null) {
            val decoded = Base64.decode(fallbackRaw, Base64.NO_WRAP)
            return SecretKeySpec(decoded, "AES")
        }
        val raw = ByteArray(32)
        java.security.SecureRandom().nextBytes(raw)
        prefs.edit().putString(FALLBACK_KEY_PREF, Base64.encodeToString(raw, Base64.NO_WRAP)).apply()
        return SecretKeySpec(raw, "AES")
    }

    companion object {
        private const val PREFS_NAME = "polymath_secret_store"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "polymath_remote_secret_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH = 128
        private const val FALLBACK_KEY_PREF = "__fallback_aes_seed"
    }
}

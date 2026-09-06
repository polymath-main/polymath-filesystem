package com.polymath.fs.js.runtime.modules

import android.os.Build
import com.polymath.fs.js.runtime.PolymathJSCryptoInterface
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

class CryptoNativeModule : PolymathJSCryptoInterface {

    private val secureRandom = SecureRandom()

    override fun hash(algo: String, input: String): String {
        val standardAlgo = when (algo.lowercase().replace("-", "")) {
            "sha256" -> "SHA-256"
            "sha512" -> "SHA-512"
            "sha1" -> "SHA-1"
            "md5" -> "MD5"
            else -> "SHA-256"
        }
        val digest = MessageDigest.getInstance(standardAlgo)
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    override fun randomUuid(): String {
        return UUID.randomUUID().toString()
    }

    override fun randomHex(byteLength: Int): String {
        val len = byteLength.coerceIn(1, 4096)
        val bytes = ByteArray(len)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun base64Encode(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Base64.getEncoder().encodeToString(bytes)
        } else {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    }

    override fun base64Decode(input: String): String {
        val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Base64.getDecoder().decode(input)
        } else {
            android.util.Base64.decode(input, android.util.Base64.DEFAULT)
        }
        return String(bytes, Charsets.UTF_8)
    }
}

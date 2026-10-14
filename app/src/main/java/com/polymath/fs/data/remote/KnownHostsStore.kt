package com.polymath.fs.data.remote

import android.content.Context
import android.util.Base64
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.MessageDigest
import java.security.PublicKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnownHostsStore @Inject constructor(
    context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun computeFingerprint(key: PublicKey): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(key.encoded)
            "SHA256:" + Base64.encodeToString(digest, Base64.NO_WRAP).trimEnd('=')
        } catch (_: Exception) {
            net.schmizz.sshj.common.SecurityUtils.getFingerprint(key)
        }
    }

    @Synchronized
    fun getFingerprint(hostname: String, port: Int): String? {
        val key = hostKey(hostname, port)
        return prefs.getString(key, null)
    }

    @Synchronized
    fun storeFingerprint(hostname: String, port: Int, fingerprint: String) {
        val key = hostKey(hostname, port)
        prefs.edit().putString(key, fingerprint).apply()
    }

    @Synchronized
    fun acceptFingerprint(hostname: String, port: Int, key: PublicKey): String {
        val fp = computeFingerprint(key)
        storeFingerprint(hostname, port, fp)
        return fp
    }

    @Synchronized
    fun removeHost(hostname: String, port: Int) {
        val key = hostKey(hostname, port)
        prefs.edit().remove(key).apply()
    }

    @Synchronized
    fun hasHost(hostname: String, port: Int): Boolean {
        val key = hostKey(hostname, port)
        return prefs.contains(key)
    }

    @Synchronized
    fun verify(hostname: String, port: Int, key: PublicKey, expectedFingerprint: String? = null): Boolean {
        val currentFp = computeFingerprint(key)

        // 1. If an explicit fingerprint was pinned in the profile, enforce it
        if (!expectedFingerprint.isNullOrEmpty()) {
            if (expectedFingerprint != currentFp) {
                throw SecurityException(
                    "SSH host key fingerprint mismatch for $hostname:$port. " +
                            "Pinned: $expectedFingerprint, Remote presented: $currentFp"
                )
            }
            storeFingerprint(hostname, port, currentFp)
            return true
        }

        // 2. Trust-On-First-Use (TOFU)
        val storedFp = getFingerprint(hostname, port)
        if (storedFp == null) {
            // First connect: trust and record
            storeFingerprint(hostname, port, currentFp)
            return true
        }

        if (storedFp != currentFp) {
            throw SecurityException(
                "POTENTIAL SECURITY BREACH: Host key for $hostname:$port does not match stored fingerprint! " +
                        "Stored: $storedFp, Remote presented: $currentFp. Connection refused."
            )
        }

        return true
    }

    fun createVerifier(expectedFingerprint: String? = null): HostKeyVerifier {
        return object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                return this@KnownHostsStore.verify(hostname, port, key, expectedFingerprint)
            }

            override fun findExistingAlgorithms(hostname: String, port: Int): List<String> {
                return emptyList()
            }
        }
    }

    private fun hostKey(hostname: String, port: Int): String = "$hostname:$port"

    companion object {
        private const val PREFS_NAME = "polymath_known_hosts"
    }
}

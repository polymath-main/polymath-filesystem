package com.polymath.fs.data.remote

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshConnectionPool @Inject constructor(
    private val profileDao: RemoteProfileDao,
    private val secretStore: SecretStore,
    private val knownHostsStore: KnownHostsStore
) : Closeable {

    class ConnectionHolder(
        val sshClient: SSHClient,
        val sftpClient: SFTPClient,
        val homePath: String
    ) {
        val mutex = Mutex()

        fun closeSilently() {
            try {
                sftpClient.close()
            } catch (_: Exception) {}
            try {
                sshClient.disconnect()
            } catch (_: Exception) {}
        }
    }

    private val connections = ConcurrentHashMap<String, ConnectionHolder>()
    private val creationLock = Any()

    @Synchronized
    fun getOrCreateHolder(profileId: String): ConnectionHolder {
        val existing = connections[profileId]
        if (existing != null && existing.sshClient.isConnected && existing.sshClient.isAuthenticated) {
            return existing
        }

        synchronized(creationLock) {
            val rechecked = connections[profileId]
            if (rechecked != null && rechecked.sshClient.isConnected && rechecked.sshClient.isAuthenticated) {
                return rechecked
            }
            rechecked?.closeSilently()
            connections.remove(profileId)

            val profile = profileDao.getProfileSync(profileId)
                ?: throw IllegalArgumentException("Remote profile not found for id: $profileId")

            val client = SSHClient()
            client.addHostKeyVerifier(knownHostsStore.createVerifier(profile.hostKeyFingerprint))
            client.connectTimeout = CONNECT_TIMEOUT_MS
            client.timeout = SOCKET_TIMEOUT_MS
            client.connect(profile.host, profile.port)

            try {
                client.connection.keepAlive.keepAliveInterval = KEEPALIVE_INTERVAL_SEC
            } catch (_: Exception) {}

            val secret = profile.encryptedSecretRef?.let { secretStore.getSecret(it) }

            if (profile.authType.equals("key", ignoreCase = true)) {
                val keyPath = profile.keyPath
                    ?: throw IllegalStateException("Key path required for public key auth on profile '${profile.label}'")
                val keyProvider = if (!secret.isNullOrEmpty()) {
                    client.loadKeys(keyPath, secret)
                } else {
                    client.loadKeys(keyPath)
                }
                client.authPublickey(profile.username, keyProvider)
            } else {
                val password = secret ?: ""
                client.authPassword(profile.username, password)
            }

            if (!client.isAuthenticated) {
                try {
                    client.disconnect()
                } catch (_: Exception) {}
                throw SecurityException(
                    "SSH authentication failed for user '${profile.username}' on ${profile.host}:${profile.port}"
                )
            }

            val sftp = client.newSFTPClient()
            val home = if (profile.defaultPath.isNotBlank()) {
                profile.defaultPath
            } else {
                try {
                    val canonical = sftp.canonicalize("")
                    if (canonical.isNullOrBlank()) "/" else canonical
                } catch (_: Exception) {
                    "/"
                }
            }

            val holder = ConnectionHolder(client, sftp, home)
            connections[profileId] = holder
            return holder
        }
    }

    suspend fun <T> withSftp(profileId: String, block: suspend (SFTPClient, String) -> T): T {
        val holder = getOrCreateHolder(profileId)
        return holder.mutex.withLock {
            try {
                block(holder.sftpClient, holder.homePath)
            } catch (e: Exception) {
                if (isConnectionDropped(e)) {
                    close(profileId)
                }
                throw e
            }
        }
    }

    fun sftp(profileId: String): SFTPClient {
        return getOrCreateHolder(profileId).sftpClient
    }

    fun homePath(profileId: String): String {
        return getOrCreateHolder(profileId).homePath
    }

    fun isConnected(profileId: String): Boolean {
        val holder = connections[profileId] ?: return false
        return holder.sshClient.isConnected && holder.sshClient.isAuthenticated
    }

    fun close(profileId: String) {
        val holder = connections.remove(profileId)
        holder?.closeSilently()
    }

    fun closeAll() {
        for ((_, holder) in connections) {
            holder.closeSilently()
        }
        connections.clear()
    }

    override fun close() {
        closeAll()
    }

    private fun isConnectionDropped(e: Exception): Boolean {
        return e is net.schmizz.sshj.transport.TransportException ||
                e is net.schmizz.sshj.sftp.SFTPException ||
                e is java.net.SocketException ||
                e is IOException
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val SOCKET_TIMEOUT_MS = 30_000
        private const val KEEPALIVE_INTERVAL_SEC = 30
    }
}

package com.polymath.fs.domain.vfs.provider

import android.content.Context
import android.webkit.MimeTypeMap
import com.polymath.fs.data.remote.KnownHostsStore
import com.polymath.fs.data.remote.SshConnectionPool
import com.polymath.fs.domain.vfs.VfsCapabilities
import com.polymath.fs.domain.vfs.VfsProvider
import com.polymath.fs.domain.vfs.VfsStat
import com.polymath.fs.domain.vfs.VfsUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SftpVfsProvider(
    private val context: Context,
    private val pool: SshConnectionPool?,
    private val knownHosts: KnownHostsStore?
) : VfsProvider {

    @Inject
    constructor(
        context: Context,
        pool: SshConnectionPool,
        knownHosts: KnownHostsStore
    ) : this(context, pool as SshConnectionPool?, knownHosts as KnownHostsStore?)

    constructor(context: Context) : this(context, null, null)

    private fun requirePool(): SshConnectionPool =
        pool ?: throw IllegalStateException("SshConnectionPool not initialized")

    override val schemes: Set<String> = setOf("sftp")

    override fun capabilities(uri: VfsUri): VfsCapabilities = VfsCapabilities(
        canRead = true,
        canWrite = true,
        canRename = true,
        canDelete = true,
        canMkdir = true,
        canChmod = true,
        canChown = true,
        canSymlink = true,
        canStream = true,
        canSeek = false
    )

    override suspend fun list(uri: VfsUri): List<VfsStat> = withContext(Dispatchers.IO) {
        val profileId = uri.authority
            ?: throw IllegalArgumentException("Missing profile id in SFTP URI: ${uri.serialized()}")

        requirePool().withSftp(profileId) { sftp, home ->
            val remotePath = resolvePath(home, uri.path)
            val entries = sftp.ls(remotePath)
            val result = ArrayList<VfsStat>(entries.size)

            for (e in entries) {
                val name = e.name
                if (name == "." || name == "..") continue
                val attrs = e.attributes
                val isDir = attrs.type == FileMode.Type.DIRECTORY
                val isSymlink = attrs.type == FileMode.Type.SYMLINK
                val childPath = if (remotePath.endsWith("/")) "$remotePath$name" else "$remotePath/$name"
                val childVfsUri = VfsUri(scheme = "sftp", authority = profileId, path = childPath)
                val mtime = if (attrs.mtime > 0) attrs.mtime * 1000L else 0L
                val perms = formatPermissions(attrs.mode.permissionsMask, isDir)
                val ext = name.substringAfterLast('.', "")
                val mime = if (isDir) null else if (ext.isNotEmpty()) {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
                } else null

                result.add(
                    VfsStat(
                        name = name,
                        uri = childVfsUri.serialized(),
                        isDirectory = isDir,
                        isSymlink = isSymlink,
                        size = attrs.size,
                        lastModified = mtime,
                        permissions = perms,
                        mimeType = mime,
                        capabilities = capabilities(childVfsUri)
                    )
                )
            }
            result.sortWith(compareByDescending<VfsStat> { it.isDirectory }.thenBy { it.name.lowercase() })
            result
        }
    }

    override suspend fun stat(uri: VfsUri): VfsStat? = withContext(Dispatchers.IO) {
        val profileId = uri.authority ?: return@withContext null
        try {
            requirePool().withSftp(profileId) { sftp, home ->
                val remotePath = resolvePath(home, uri.path)
                val attrs = try {
                    sftp.statExistence(remotePath)
                } catch (_: Exception) {
                    try {
                        sftp.stat(remotePath)
                    } catch (_: Exception) {
                        null
                    }
                } ?: return@withSftp null

                val name = remotePath.substringAfterLast('/').ifEmpty { remotePath }
                val isDir = attrs.type == FileMode.Type.DIRECTORY
                val isSymlink = attrs.type == FileMode.Type.SYMLINK
                val size = attrs.size
                val mtime = if (attrs.mtime > 0) attrs.mtime * 1000L else 0L
                val perms = formatPermissions(attrs.mode.permissionsMask, isDir)
                val ext = name.substringAfterLast('.', "")
                val mime = if (isDir) null else if (ext.isNotEmpty()) {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
                } else null

                VfsStat(
                    name = name,
                    uri = uri.serialized(),
                    isDirectory = isDir,
                    isSymlink = isSymlink,
                    size = size,
                    lastModified = mtime,
                    permissions = perms,
                    mimeType = mime,
                    capabilities = capabilities(uri)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun openRead(uri: VfsUri): InputStream {
        val profileId = uri.authority
            ?: throw IllegalArgumentException("Missing profile id in SFTP URI: ${uri.serialized()}")
        val holder = requirePool().getOrCreateHolder(profileId)
        val remotePath = resolvePath(holder.homePath, uri.path)
        val remoteFile = holder.sftpClient.open(remotePath, EnumSet.of(OpenMode.READ))
        return remoteFile.RemoteFileInputStream()
    }

    override fun openWrite(uri: VfsUri, append: Boolean): OutputStream {
        val profileId = uri.authority
            ?: throw IllegalArgumentException("Missing profile id in SFTP URI: ${uri.serialized()}")
        val holder = requirePool().getOrCreateHolder(profileId)
        val remotePath = resolvePath(holder.homePath, uri.path)
        val modes = if (append) {
            EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.APPEND)
        } else {
            EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)
        }
        val remoteFile = holder.sftpClient.open(remotePath, modes)
        return remoteFile.RemoteFileOutputStream()
    }

    override suspend fun delete(uri: VfsUri, recursive: Boolean): Boolean = withContext(Dispatchers.IO) {
        val profileId = uri.authority ?: return@withContext false
        try {
            requirePool().withSftp(profileId) { sftp, home ->
                val remotePath = resolvePath(home, uri.path)
                val attrs = sftp.statExistence(remotePath) ?: return@withSftp true
                if (attrs.type == FileMode.Type.DIRECTORY) {
                    if (recursive) {
                        deleteRecursive(sftp, remotePath)
                    } else {
                        sftp.rmdir(remotePath)
                    }
                } else {
                    sftp.rm(remotePath)
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun mkdir(uri: VfsUri): Boolean = withContext(Dispatchers.IO) {
        val profileId = uri.authority ?: return@withContext false
        try {
            requirePool().withSftp(profileId) { sftp, home ->
                val remotePath = resolvePath(home, uri.path)
                sftp.mkdirs(remotePath)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun rename(uri: VfsUri, newName: String): Boolean = withContext(Dispatchers.IO) {
        val profileId = uri.authority ?: return@withContext false
        try {
            requirePool().withSftp(profileId) { sftp, home ->
                val remotePath = resolvePath(home, uri.path)
                val targetRemotePath = if (newName.startsWith("/")) {
                    newName
                } else {
                    val parent = remotePath.substringBeforeLast('/', "")
                    if (parent.isEmpty()) "/$newName" else "$parent/$newName"
                }
                sftp.rename(remotePath, targetRemotePath)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun chmod(uri: VfsUri, mode: String): Boolean = withContext(Dispatchers.IO) {
        val profileId = uri.authority ?: return@withContext false
        try {
            val octal = mode.toIntOrNull(8) ?: return@withContext false
            requirePool().withSftp(profileId) { sftp, home ->
                val remotePath = resolvePath(home, uri.path)
                sftp.chmod(remotePath, octal)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun chown(uri: VfsUri, owner: String): Boolean = withContext(Dispatchers.IO) {
        val profileId = uri.authority ?: return@withContext false
        try {
            val parts = owner.split(":")
            val uid = parts[0].toIntOrNull()
            val gid = parts.getOrNull(1)?.toIntOrNull()
            if (uid != null) {
                requirePool().withSftp(profileId) { sftp, home ->
                    val remotePath = resolvePath(home, uri.path)
                    val builder = net.schmizz.sshj.sftp.FileAttributes.Builder().withUIDGID(uid, gid ?: 0)
                    sftp.setattr(remotePath, builder.build())
                    true
                }
            } else {
                val holder = requirePool().getOrCreateHolder(profileId)
                val remotePath = resolvePath(holder.homePath, uri.path)
                val session = holder.sshClient.startSession()
                session.use { s ->
                    val safeOwner = owner.replace("'", "'\\''")
                    val safePath = remotePath.replace("'", "'\\''")
                    val cmd = s.exec("chown '$safeOwner' '$safePath'")
                    cmd.join(10, TimeUnit.SECONDS)
                    cmd.exitStatus == 0
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun close() {
        pool?.closeAll()
    }


    private fun resolvePath(home: String, path: String): String {
        if (path.isEmpty()) return home.ifEmpty { "/" }
        if (path == "/") return if (home.isNotEmpty() && home != "/") home else "/"
        if (path.startsWith("/~/")) {
            val rel = path.removePrefix("/~/")
            return if (home.endsWith("/")) "$home$rel" else "$home/$rel"
        }
        return path
    }

    private fun deleteRecursive(sftp: SFTPClient, path: String) {
        val attrs = sftp.statExistence(path) ?: return
        if (attrs.type == FileMode.Type.DIRECTORY) {
            val entries = sftp.ls(path)
            for (e in entries) {
                if (e.name == "." || e.name == "..") continue
                val childPath = if (path.endsWith("/")) "$path${e.name}" else "$path/${e.name}"
                deleteRecursive(sftp, childPath)
            }
            sftp.rmdir(path)
        } else {
            sftp.rm(path)
        }
    }

    private fun formatPermissions(mask: Int, isDirectory: Boolean): String {
        val sb = StringBuilder(10)
        sb.append(if (isDirectory) 'd' else '-')
        sb.append(if (mask and 0x100 != 0) 'r' else '-')
        sb.append(if (mask and 0x80 != 0) 'w' else '-')
        sb.append(if (mask and 0x40 != 0) 'x' else '-')
        sb.append(if (mask and 0x20 != 0) 'r' else '-')
        sb.append(if (mask and 0x10 != 0) 'w' else '-')
        sb.append(if (mask and 0x08 != 0) 'x' else '-')
        sb.append(if (mask and 0x04 != 0) 'r' else '-')
        sb.append(if (mask and 0x02 != 0) 'w' else '-')
        sb.append(if (mask and 0x01 != 0) 'x' else '-')
        return sb.toString()
    }
}

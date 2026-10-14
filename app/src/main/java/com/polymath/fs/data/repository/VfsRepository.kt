package com.polymath.fs.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.polymath.fs.domain.vfs.TransferEngine
import com.polymath.fs.domain.vfs.VfsCapabilities
import com.polymath.fs.domain.vfs.VfsRegistry
import com.polymath.fs.domain.vfs.VfsStat
import com.polymath.fs.domain.vfs.VfsUri
import com.polymath.fs.domain.vfs.provider.LocalVfsProvider
import com.polymath.fs.models.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VfsRepository @Inject constructor(
    private val registry: VfsRegistry,
    private val context: Context? = null,
    private val transferEngine: TransferEngine = TransferEngine(registry)
) : IFileSystemRepository {

    override suspend fun listDir(path: String): List<FileNode> = withContext(Dispatchers.IO) {
        val uri = VfsUri.parse(path)
        val provider = registry.providerFor(uri)
        provider.list(uri).map(::toFileNode)
    }

    override fun copy(src: List<String>, dest: String): Flow<Progress> {
        return transferEngine.copy(src, dest)
    }

    override fun move(src: List<String>, dest: String): Flow<Progress> = flow {
        if (src.isEmpty()) {
            emit(Progress(0L, 0L, isComplete = true))
            return@flow
        }

        val destUri = VfsUri.parse(dest)
        val destProvider = registry.providerFor(destUri)

        if (destProvider is LocalVfsProvider) {
            val total = src.size.toLong()
            emit(Progress(0L, total, isComplete = false))

            val destStat = destProvider.stat(destUri)
            if (destStat == null || !destStat.isDirectory) {
                destProvider.mkdir(destUri)
            }

            for ((index, s) in src.withIndex()) {
                val srcUri = VfsUri.parse(s)
                val srcProvider = registry.providerFor(srcUri)
                val fileName = srcUri.path.substringAfterLast('/')
                val destPath = if (dest.endsWith("/")) "$dest$fileName" else "$dest/$fileName"
                val targetUri = VfsUri.parse(destPath)
                var moved = false

                if (srcProvider is LocalVfsProvider) {
                    val srcFile = File(srcUri.path)
                    val targetFile = File(targetUri.path)
                    moved = srcProvider.moveLocal(srcFile, targetFile)
                }

                if (!moved) {
                    // Cross-provider move or fallback if moveLocal was unsupported/failed
                    transferEngine.move(listOf(s), dest).collect { /* wait for completion */ }
                    moved = true
                }

                val isLast = (index + 1 == src.size)
                emit(Progress((index + 1).toLong(), total, isComplete = isLast, currentFile = fileName))
            }
        } else {
            // Destination is cross-provider (SAF, SFTP, etc.) -> delegate directly to TransferEngine
            transferEngine.move(src, dest).collect { progress ->
                emit(progress)
            }
        }
    }

    override suspend fun delete(paths: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext true
        var allDeleted = true
        for (p in paths) {
            val uri = VfsUri.parse(p)
            val provider = registry.providerFor(uri)
            val deleted = provider.delete(uri, recursive = true)
            if (!deleted) {
                allDeleted = false
            }
        }
        allDeleted
    }

    override suspend fun mkdir(path: String): Boolean = withContext(Dispatchers.IO) {
        val uri = VfsUri.parse(path)
        val provider = registry.providerFor(uri)
        provider.mkdir(uri)
    }

    override suspend fun rename(oldPath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val uri = VfsUri.parse(oldPath)
        val provider = registry.providerFor(uri)
        provider.rename(uri, newName)
    }

    override suspend fun chmod(path: String, mode: String): Boolean = withContext(Dispatchers.IO) {
        val uri = VfsUri.parse(path)
        val provider = registry.providerFor(uri)
        provider.chmod(uri, mode)
    }

    override suspend fun chown(path: String, owner: String): Boolean = withContext(Dispatchers.IO) {
        val uri = VfsUri.parse(path)
        val provider = registry.providerFor(uri)
        provider.chown(uri, owner)
    }

    override suspend fun stat(path: String): VfsStat? = withContext(Dispatchers.IO) {
        val uri = VfsUri.parse(path)
        registry.providerFor(uri).stat(uri)
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        stat(path) != null
    }

    override fun openRead(path: String): InputStream {
        val uri = VfsUri.parse(path)
        return registry.providerFor(uri).openRead(uri)
    }

    override fun openWrite(path: String, append: Boolean): OutputStream {
        val uri = VfsUri.parse(path)
        return registry.providerFor(uri).openWrite(uri, append)
    }

    override fun capabilities(path: String): VfsCapabilities {
        val uri = VfsUri.parse(path)
        return registry.providerFor(uri).capabilities(uri)
    }

    override fun toSharableUri(path: String): Uri? {
        val uri = VfsUri.parse(path)
        return when (uri.scheme) {
            "content" -> Uri.parse(uri.serialized())
            "file" -> {
                val file = File(uri.path)
                if (context != null) {
                    try {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    } catch (_: Exception) {
                        Uri.fromFile(file)
                    }
                } else {
                    Uri.fromFile(file)
                }
            }
            else -> null
        }
    }

    private fun toFileNode(stat: VfsStat): FileNode {
        return if (stat.permissions != null && stat.permissions.length >= 9) {
            FileNode.RootFile(
                name = stat.name,
                path = stat.uri,
                size = stat.size,
                lastModified = stat.lastModified,
                isDirectory = stat.isDirectory,
                permissions = stat.permissions
            )
        } else {
            FileNode.LocalFile(
                name = stat.name,
                path = stat.uri,
                size = stat.size,
                lastModified = stat.lastModified,
                isDirectory = stat.isDirectory
            )
        }
    }
}

package com.polymath.fs.domain.vfs.provider

import android.webkit.MimeTypeMap
import com.polymath.fs.core.RootShellHolder
import com.polymath.fs.core.StatParser
import com.polymath.fs.domain.vfs.VfsCapabilities
import com.polymath.fs.domain.vfs.VfsProvider
import com.polymath.fs.domain.vfs.VfsStat
import com.polymath.fs.domain.vfs.VfsUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalVfsProvider @Inject constructor(
    private val shellHolder: RootShellHolder
) : VfsProvider {

    override val schemes: Set<String> = setOf("file")

    override fun capabilities(uri: VfsUri): VfsCapabilities {
        val file = File(resolvePath(uri))
        val hasRoot = shellHolder.hasRootAccess()
        val canRead = file.canRead() || hasRoot
        val canWrite = file.canWrite() || hasRoot
        val parent = file.parentFile
        val canModifyParent = (parent?.canWrite() ?: false) || hasRoot
        return VfsCapabilities(
            canRead = canRead,
            canWrite = canWrite,
            canRename = canModifyParent,
            canDelete = canModifyParent,
            canMkdir = canWrite || canModifyParent,
            canChmod = hasRoot,
            canChown = hasRoot,
            canSymlink = true,
            canStream = true,
            canSeek = true
        )
    }

    override suspend fun list(uri: VfsUri): List<VfsStat> = withContext(Dispatchers.IO) {
        val path = resolvePath(uri)
        val target = File(path)
        if (target.exists() && target.isDirectory) {
            val files = target.listFiles()
            if (files != null) {
                return@withContext files.map { file ->
                    val isDir = file.isDirectory
                    val isLink = isSymlink(file)
                    VfsStat(
                        name = file.name,
                        uri = file.absolutePath,
                        isDirectory = isDir,
                        isSymlink = isLink,
                        size = if (isDir) 0L else file.length(),
                        lastModified = file.lastModified(),
                        permissions = null,
                        mimeType = getMimeType(file),
                        capabilities = capabilities(VfsUri("file", null, file.absolutePath))
                    )
                }.sortedWith(
                    compareByDescending<VfsStat> { it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )
            }
        }

        if (shellHolder.hasRootAccess()) {
            val safePath = path.replace("'", "'\\''")
            val script = "find '$safePath' -maxdepth 1 -mindepth 1 -print0 2>/dev/null | xargs -0 stat -c \"%A|%s|%Y|%n\" 2>/dev/null"
            val result = shellHolder.execute(script)
            if (result.isSuccess && result.output.isNotEmpty()) {
                return@withContext result.output.mapNotNull { line ->
                    StatParser.parseVfsStatLine(line)
                }.sortedWith(
                    compareByDescending<VfsStat> { it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )
            }
        }

        return@withContext emptyList()
    }

    override suspend fun stat(uri: VfsUri): VfsStat? = withContext(Dispatchers.IO) {
        val path = resolvePath(uri)
        val file = File(path)
        if (file.exists()) {
            val isDir = file.isDirectory
            val isLink = isSymlink(file)
            return@withContext VfsStat(
                name = file.name.ifEmpty { path },
                uri = file.absolutePath,
                isDirectory = isDir,
                isSymlink = isLink,
                size = if (isDir) 0L else file.length(),
                lastModified = file.lastModified(),
                permissions = null,
                mimeType = getMimeType(file),
                capabilities = capabilities(uri)
            )
        }

        if (shellHolder.hasRootAccess()) {
            val safePath = path.replace("'", "'\\''")
            val result = shellHolder.execute("stat -c \"%A|%s|%Y|%n\" '$safePath' 2>/dev/null")
            if (result.isSuccess && result.output.isNotEmpty()) {
                val line = result.output.firstOrNull { it.contains("|") }
                if (line != null) {
                    return@withContext StatParser.parseVfsStatLine(line)
                }
            }
        }

        null
    }

    override fun openRead(uri: VfsUri): InputStream {
        val path = resolvePath(uri)
        val file = File(path)
        if (file.canRead()) {
            return FileInputStream(file)
        }
        if (shellHolder.hasRootAccess()) {
            val safePath = path.replace("'", "'\\''")
            val process = ProcessBuilder("su", "-c", "cat '$safePath'").start()
            return process.inputStream
        }
        return FileInputStream(file)
    }

    override fun openWrite(uri: VfsUri, append: Boolean): OutputStream {
        val path = resolvePath(uri)
        val file = File(path)
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        if (file.canWrite() || (!file.exists() && (parent?.canWrite() == true))) {
            return FileOutputStream(file, append)
        }
        if (shellHolder.hasRootAccess()) {
            val safePath = path.replace("'", "'\\''")
            val redirect = if (append) ">>" else ">"
            val process = ProcessBuilder("su", "-c", "cat $redirect '$safePath'").start()
            return process.outputStream
        }
        return FileOutputStream(file, append)
    }

    override suspend fun delete(uri: VfsUri, recursive: Boolean): Boolean = withContext(Dispatchers.IO) {
        val path = resolvePath(uri)
        val file = File(path)
        try {
            val deleted = if (recursive && file.isDirectory) file.deleteRecursively() else file.delete()
            if (deleted) return@withContext true
        } catch (_: Exception) {}

        if (shellHolder.hasRootAccess()) {
            val safePath = file.absolutePath.replace("'", "'\\''")
            val cmd = if (recursive) "rm -rf '$safePath'" else "rm -f '$safePath'"
            val res = shellHolder.execute(cmd)
            return@withContext res.isSuccess
        }
        false
    }

    override suspend fun mkdir(uri: VfsUri): Boolean = withContext(Dispatchers.IO) {
        val path = resolvePath(uri)
        val file = File(path)
        if (file.exists() && file.isDirectory) return@withContext true
        if (file.mkdirs()) return@withContext true
        if (shellHolder.hasRootAccess()) {
            val safePath = file.absolutePath.replace("'", "'\\''")
            val result = shellHolder.execute("mkdir -p '$safePath'")
            return@withContext result.isSuccess
        }
        false
    }

    override suspend fun rename(uri: VfsUri, newName: String): Boolean = withContext(Dispatchers.IO) {
        val path = resolvePath(uri)
        val oldFile = File(path)
        val parent = oldFile.parentFile ?: return@withContext false
        val newFile = File(parent, newName)
        if (oldFile.renameTo(newFile)) return@withContext true
        if (shellHolder.hasRootAccess()) {
            val safeOldPath = oldFile.absolutePath.replace("'", "'\\''")
            val safeNewPath = newFile.absolutePath.replace("'", "'\\''")
            val result = shellHolder.execute("mv '$safeOldPath' '$safeNewPath'")
            return@withContext result.isSuccess
        }
        false
    }

    override suspend fun chmod(uri: VfsUri, mode: String): Boolean = withContext(Dispatchers.IO) {
        if (!shellHolder.hasRootAccess()) return@withContext false
        val safePath = resolvePath(uri).replace("'", "'\\''")
        val safeMode = mode.replace("'", "'\\''")
        val result = shellHolder.execute("chmod '$safeMode' '$safePath'")
        result.isSuccess
    }

    override suspend fun chown(uri: VfsUri, owner: String): Boolean = withContext(Dispatchers.IO) {
        if (!shellHolder.hasRootAccess()) return@withContext false
        val safePath = resolvePath(uri).replace("'", "'\\''")
        val safeOwner = owner.replace("'", "'\\''")
        val result = shellHolder.execute("chown '$safeOwner' '$safePath'")
        result.isSuccess
    }

    suspend fun copyLocal(src: File, dest: File): Boolean {
        var copied = false
        try {
            if (src.isDirectory) {
                src.copyRecursively(dest, overwrite = true)
                copied = true
            } else {
                src.copyTo(dest, overwrite = true)
                copied = true
            }
        } catch (_: Exception) {
            if (shellHolder.hasRootAccess()) {
                val safeSrc = src.absolutePath.replace("'", "'\\''")
                val safeDest = dest.absolutePath.replace("'", "'\\''")
                val res = shellHolder.execute("cp -a -p '$safeSrc' '$safeDest'")
                if (!res.isSuccess && !src.isDirectory) {
                    shellHolder.execute("cat '$safeSrc' > '$safeDest'")
                }
                copied = res.isSuccess
            }
        }
        return copied
    }

    suspend fun moveLocal(src: File, dest: File): Boolean {
        var moved = false
        try {
            if (src.renameTo(dest)) {
                moved = true
            } else {
                if (src.isDirectory) {
                    src.copyRecursively(dest, overwrite = true)
                    src.deleteRecursively()
                } else {
                    src.copyTo(dest, overwrite = true)
                    src.delete()
                }
                moved = true
            }
        } catch (_: Exception) {
            if (shellHolder.hasRootAccess()) {
                val safeSrc = src.absolutePath.replace("'", "'\\''")
                val safeDest = dest.absolutePath.replace("'", "'\\''")
                val res = shellHolder.execute("mv '$safeSrc' '$safeDest'")
                if (!res.isSuccess) {
                    shellHolder.execute("cp -a -p '$safeSrc' '$safeDest' || cat '$safeSrc' > '$safeDest'")
                    shellHolder.execute("rm -rf '$safeSrc'")
                }
                moved = res.isSuccess
            }
        }
        return moved
    }

    private fun resolvePath(uri: VfsUri): String {
        return if (uri.path.isEmpty()) "/" else uri.path
    }

    private fun isSymlink(file: File): Boolean {
        return try {
            Files.isSymbolicLink(file.toPath())
        } catch (_: Throwable) {
            file.canonicalPath != file.absolutePath
        }
    }

    private fun getMimeType(file: File): String? {
        if (file.isDirectory) return null
        val extension = file.extension.lowercase()
        if (extension.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }
}

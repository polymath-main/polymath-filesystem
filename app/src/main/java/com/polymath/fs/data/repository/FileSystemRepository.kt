package com.polymath.fs.data.repository

import com.polymath.fs.core.RootShellHolder
import com.polymath.fs.core.StatParser
import com.polymath.fs.models.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class Progress(
    val currentBytes: Long,
    val totalBytes: Long,
    val isComplete: Boolean = false
)

class FileSystemRepository @Inject constructor(
    private val shellHolder: RootShellHolder
) : IFileSystemRepository {

    override suspend fun listDir(path: String): List<FileNode> = withContext(Dispatchers.IO) {
        val target = File(path)
        if (target.exists() && target.isDirectory) {
            val files = target.listFiles()
            if (files != null) {
                return@withContext files.map { file ->
                    val isDir = file.isDirectory
                    val permissions = buildString {
                        append(if (isDir) 'd' else '-')
                        append(if (file.canRead()) 'r' else '-')
                        append(if (file.canWrite()) 'w' else '-')
                        append(if (file.canExecute()) 'x' else '-')
                    }
                    FileNode.LocalFile(
                        name = file.name,
                        path = file.absolutePath,
                        size = if (isDir) 0L else file.length(),
                        lastModified = file.lastModified(),
                        isDirectory = isDir
                    )
                }.sortedWith(
                    compareByDescending<FileNode> { it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )
            }
        }

        // Only attempt root shell inspection if standard filesystem query was restricted AND root is granted
        if (shellHolder.hasRootAccess()) {
            val safePath = path.replace("'", "'\\''")
            val script = "find '$safePath' -maxdepth 1 -mindepth 1 -print0 2>/dev/null | xargs -0 stat -c \"%A|%s|%Y|%n\" 2>/dev/null"
            val result = shellHolder.execute(script)
            if (result.isSuccess && result.output.isNotEmpty()) {
                return@withContext result.output.mapNotNull { line ->
                    StatParser.parseStatLine(line)
                }
            }
        }

        return@withContext emptyList()
    }

    override fun copy(src: List<String>, dest: String): Flow<Progress> = flow {
        val total = src.size.toLong()
        emit(Progress(0, total))
        val destDir = File(dest)
        if (!destDir.exists()) destDir.mkdirs()

        for ((index, s) in src.withIndex()) {
            val srcFile = File(s)
            val targetFile = File(destDir, srcFile.name)
            var copied = false

            try {
                if (srcFile.isDirectory) {
                    srcFile.copyRecursively(targetFile, overwrite = true)
                    copied = true
                } else {
                    srcFile.copyTo(targetFile, overwrite = true)
                    copied = true
                }
            } catch (e: Exception) {
                if (shellHolder.hasRootAccess()) {
                    val safeSrc = s.replace("'", "'\\''")
                    val safeDest = dest.replace("'", "'\\''")
                    val res = shellHolder.execute("cp -a -p '$safeSrc' '$safeDest'")
                    if (!res.isSuccess) {
                        shellHolder.execute("cat '$safeSrc' > '$safeDest/${s.substringAfterLast('/')}'")
                    }
                    copied = res.isSuccess
                }
            }
            emit(Progress(index.toLong() + 1, total, isComplete = (index + 1 == src.size)))
        }
    }

    override fun move(src: List<String>, dest: String): Flow<Progress> = flow {
        val total = src.size.toLong()
        emit(Progress(0, total))
        val destDir = File(dest)
        if (!destDir.exists()) destDir.mkdirs()

        for ((index, s) in src.withIndex()) {
            val srcFile = File(s)
            val targetFile = File(destDir, srcFile.name)
            var moved = false

            try {
                if (srcFile.renameTo(targetFile)) {
                    moved = true
                } else {
                    if (srcFile.isDirectory) {
                        srcFile.copyRecursively(targetFile, overwrite = true)
                        srcFile.deleteRecursively()
                    } else {
                        srcFile.copyTo(targetFile, overwrite = true)
                        srcFile.delete()
                    }
                    moved = true
                }
            } catch (e: Exception) {
                if (shellHolder.hasRootAccess()) {
                    val safeSrc = s.replace("'", "'\\''")
                    val safeDest = dest.replace("'", "'\\''")
                    val res = shellHolder.execute("mv '$safeSrc' '$safeDest'")
                    if (!res.isSuccess) {
                        shellHolder.execute("cp -a -p '$safeSrc' '$safeDest' || cat '$safeSrc' > '$safeDest/${s.substringAfterLast('/')}'")
                        shellHolder.execute("rm -rf '$safeSrc'")
                    }
                    moved = res.isSuccess
                }
            }
            emit(Progress(index.toLong() + 1, total, isComplete = (index + 1 == src.size)))
        }
    }

    override suspend fun delete(paths: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext true
        var allDeleted = true
        for (p in paths) {
            val file = File(p)
            try {
                val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (!deleted && shellHolder.hasRootAccess()) {
                    val safePath = p.replace("'", "'\\''")
                    val res = shellHolder.execute("rm -rf '$safePath'")
                    if (!res.isSuccess) allDeleted = false
                } else if (!deleted) {
                    allDeleted = false
                }
            } catch (e: Exception) {
                if (shellHolder.hasRootAccess()) {
                    val safePath = p.replace("'", "'\\''")
                    val res = shellHolder.execute("rm -rf '$safePath'")
                    if (!res.isSuccess) allDeleted = false
                } else {
                    allDeleted = false
                }
            }
        }
        allDeleted
    }

    override suspend fun mkdir(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.exists() && file.isDirectory) return@withContext true
        if (file.mkdirs()) return@withContext true
        if (shellHolder.hasRootAccess()) {
            val safePath = path.replace("'", "'\\''")
            val result = shellHolder.execute("mkdir -p '$safePath'")
            return@withContext result.isSuccess
        }
        false
    }

    override suspend fun rename(oldPath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val oldFile = File(oldPath)
        val parent = oldFile.parentFile ?: return@withContext false
        val newFile = File(parent, newName)
        if (oldFile.renameTo(newFile)) return@withContext true
        if (shellHolder.hasRootAccess()) {
            val safeOldPath = oldPath.replace("'", "'\\''")
            val safeNewPath = newFile.absolutePath.replace("'", "'\\''")
            val result = shellHolder.execute("mv '$safeOldPath' '$safeNewPath'")
            return@withContext result.isSuccess
        }
        false
    }

    override suspend fun chmod(path: String, mode: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.exists()) {
            try {
                file.setReadable(true, false)
                file.setWritable(true, false)
                file.setExecutable(true, false)
            } catch (ignored: Exception) {}
        }
        if (shellHolder.hasRootAccess()) {
            val safePath = path.replace("'", "'\\''")
            val result = shellHolder.execute("chmod $mode '$safePath'")
            return@withContext result.isSuccess
        }
        true
    }

    override suspend fun chown(path: String, owner: String): Boolean = withContext(Dispatchers.IO) {
        if (shellHolder.hasRootAccess()) {
            val safePath = path.replace("'", "'\\''")
            val result = shellHolder.execute("chown $owner '$safePath'")
            return@withContext result.isSuccess
        }
        true
    }
}


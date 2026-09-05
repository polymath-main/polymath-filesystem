package com.polymath.fs.data.repository

import com.polymath.fs.core.RootShellHolder
import com.polymath.fs.core.StatParser
import com.polymath.fs.models.FileNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

data class Progress(
    val currentBytes: Long,
    val totalBytes: Long,
    val isComplete: Boolean = false
)

class FileSystemRepository @Inject constructor(
    private val shellHolder: RootShellHolder
) {

    suspend fun listDir(path: String): List<FileNode> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val safePath = path.replace("'", "'\\''")
        val script = "find '$safePath' -maxdepth 1 -mindepth 1 -print0 2>/dev/null | xargs -0 stat -c \"%A|%s|%Y|%n\" 2>/dev/null"

        val result = shellHolder.execute(script)
        if (!result.isSuccess && result.output.isEmpty()) {
            return@withContext emptyList()
        }

        return@withContext result.output.mapNotNull { line ->
            StatParser.parseStatLine(line)
        }
    }

    fun copy(src: List<String>, dest: String): Flow<Progress> = flow {
        val total = src.size.toLong()
        emit(Progress(0, total))
        val safeDest = dest.replace("'", "'\\''")
        for ((index, s) in src.withIndex()) {
            val safeSrc = s.replace("'", "'\\''")
            val res = shellHolder.execute("cp -a -p '$safeSrc' '$safeDest'")
            if (!res.isSuccess) {
                shellHolder.execute("cat '$safeSrc' > '$safeDest/${s.substringAfterLast('/')}'")
            }
            emit(Progress(index.toLong() + 1, total, isComplete = (index + 1 == src.size)))
        }
    }

    fun move(src: List<String>, dest: String): Flow<Progress> = flow {
        val total = src.size.toLong()
        emit(Progress(0, total))
        val safeDest = dest.replace("'", "'\\''")
        for ((index, s) in src.withIndex()) {
            val safeSrc = s.replace("'", "'\\''")
            val res = shellHolder.execute("mv '$safeSrc' '$safeDest'")
            if (!res.isSuccess) {
                shellHolder.execute("cp -a -p '$safeSrc' '$safeDest' || cat '$safeSrc' > '$safeDest/${s.substringAfterLast('/')}'")
                shellHolder.execute("rm -rf '$safeSrc'")
            }
            emit(Progress(index.toLong() + 1, total, isComplete = (index + 1 == src.size)))
        }
    }

    suspend fun delete(paths: List<String>): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext true
        val commandArgs = paths.joinToString(" ") { "'${it.replace("'", "'\\''")}'" }
        shellHolder.execute("rm -rf $commandArgs")
        return@withContext true
    }

    suspend fun mkdir(path: String): Boolean {
        val safePath = path.replace("'", "'\\''")
        val result = shellHolder.execute("mkdir -p '$safePath'")
        return result.isSuccess
    }

    suspend fun rename(oldPath: String, newName: String): Boolean {
        val safeOldPath = oldPath.replace("'", "'\\''")
        val parent = oldPath.substringBeforeLast('/')
        val newPath = "${if(parent.isEmpty()) "" else parent}/$newName"
        val safeNewPath = newPath.replace("'", "'\\''")
        val result = shellHolder.execute("mv '$safeOldPath' '$safeNewPath'")
        return result.isSuccess
    }

    suspend fun chmod(path: String, mode: String): Boolean {
        val safePath = path.replace("'", "'\\''")
        val result = shellHolder.execute("chmod $mode '$safePath'")
        return result.isSuccess
    }

    suspend fun chown(path: String, owner: String): Boolean {
        val safePath = path.replace("'", "'\\''")
        val result = shellHolder.execute("chown $owner '$safePath'")
        return result.isSuccess
    }
}

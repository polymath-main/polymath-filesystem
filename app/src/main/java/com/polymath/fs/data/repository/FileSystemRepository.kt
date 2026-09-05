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

    suspend fun listDir(path: String): List<FileNode> {
        val safePath = path.replace("'", "'\\''")
        val script = """
            for f in '$safePath'/.* '$safePath'/*; do
                if [ "${'$'}f" = "'$safePath'/." ] || [ "${'$'}f" = "'$safePath'/.." ] || [ "${'$'}f" = "'$safePath'/.*" ] || [ "${'$'}f" = "'$safePath'/*" ]; then
                    continue
                fi
                if [ -e "${'$'}f" ] || [ -L "${'$'}f" ]; then
                    stat -c "%A|%s|%Y|%n" "${'$'}f" 2>/dev/null
                fi
            done
        """.trimIndent()

        val result = shellHolder.execute(script)
        if (!result.isSuccess && result.output.isEmpty()) {
            return emptyList()
        }

        return result.output.mapNotNull { line ->
            StatParser.parseStatLine(line)
        }
    }

    fun copy(src: List<String>, dest: String): Flow<Progress> = flow {
        val total = src.size.toLong()
        emit(Progress(0, total))
        val safeDest = dest.replace("'", "'\\''")
        for ((index, s) in src.withIndex()) {
            val safeSrc = s.replace("'", "'\\''")
            shellHolder.execute("cp -a '$safeSrc' '$safeDest'")
            emit(Progress(index.toLong() + 1, total, isComplete = (index + 1 == src.size)))
        }
    }

    fun move(src: List<String>, dest: String): Flow<Progress> = flow {
        val total = src.size.toLong()
        emit(Progress(0, total))
        val safeDest = dest.replace("'", "'\\''")
        for ((index, s) in src.withIndex()) {
            val safeSrc = s.replace("'", "'\\''")
            shellHolder.execute("mv '$safeSrc' '$safeDest'")
            emit(Progress(index.toLong() + 1, total, isComplete = (index + 1 == src.size)))
        }
    }

    suspend fun delete(paths: List<String>): Boolean {
        for (p in paths) {
            val safePath = p.replace("'", "'\\''")
            shellHolder.execute("rm -rf '$safePath'")
        }
        return true
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
}

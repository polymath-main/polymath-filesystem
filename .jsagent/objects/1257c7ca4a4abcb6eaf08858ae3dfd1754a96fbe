import re
with open('app/src/main/java/com/polymath/fs/data/repository/FileSystemRepository.kt', 'r') as f:
    content = f.read()
# Cut everything after "fun copy(src: List<String>, dest: String): Flow<Progress> = flow {" ... 
# actually wait, let's just find the end of copy and append.
start_idx = content.find("fun copy(src:")
if start_idx != -1:
    end_idx = content.find("}\n}", start_idx)
    content = content[:end_idx + 2] + """
    fun move(src: List<String>, dest: String): Flow<Progress> = flow {
        val total = src.size.toLong()
        emit(Progress(0, total))
        val safeDest = dest.replace("'", "'\\\\''")
        for ((index, s) in src.withIndex()) {
            val safeSrc = s.replace("'", "'\\\\''")
            shellHolder.execute("mv '$safeSrc' '$safeDest'")
            emit(Progress(index.toLong() + 1, total, isComplete = (index + 1 == src.size)))
        }
    }

    suspend fun delete(paths: List<String>): Boolean {
        for (p in paths) {
            val safePath = p.replace("'", "'\\\\''")
            shellHolder.execute("rm -rf '$safePath'")
        }
        return true
    }

    suspend fun mkdir(path: String): Boolean {
        val safePath = path.replace("'", "'\\\\''")
        val result = shellHolder.execute("mkdir -p '$safePath'")
        return result.isSuccess
    }

    suspend fun rename(oldPath: String, newName: String): Boolean {
        val safeOldPath = oldPath.replace("'", "'\\\\''")
        val parent = oldPath.substringBeforeLast('/')
        val newPath = "${if(parent.isEmpty()) "" else parent}/$newName"
        val safeNewPath = newPath.replace("'", "'\\\\''")
        val result = shellHolder.execute("mv '$safeOldPath' '$safeNewPath'")
        return result.isSuccess
    }
}
"""

with open('app/src/main/java/com/polymath/fs/data/repository/FileSystemRepository.kt', 'w') as f:
    f.write(content)

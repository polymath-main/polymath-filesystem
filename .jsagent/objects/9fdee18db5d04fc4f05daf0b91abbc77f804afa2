import sys

def main():
    with open('app/src/main/java/com/polymath/fs/data/repository/FileSystemRepository.kt', 'r') as f:
        content = f.read()

    new_methods = """
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
"""
    content = content.replace("}\n", new_methods)
    with open('app/src/main/java/com/polymath/fs/data/repository/FileSystemRepository.kt', 'w') as f:
        f.write(content)

if __name__ == '__main__':
    main()

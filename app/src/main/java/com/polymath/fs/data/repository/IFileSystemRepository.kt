package com.polymath.fs.data.repository

import com.polymath.fs.domain.vfs.VfsCapabilities
import com.polymath.fs.domain.vfs.VfsStat
import com.polymath.fs.models.FileNode
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

interface IFileSystemRepository {
    suspend fun listDir(path: String): List<FileNode>
    fun copy(src: List<String>, dest: String): Flow<Progress>
    fun move(src: List<String>, dest: String): Flow<Progress>
    suspend fun delete(paths: List<String>): Boolean
    suspend fun mkdir(path: String): Boolean
    suspend fun rename(oldPath: String, newName: String): Boolean
    suspend fun chmod(path: String, mode: String): Boolean
    suspend fun chown(path: String, owner: String): Boolean

    // Additive VFS operations
    suspend fun stat(path: String): VfsStat? = null
    suspend fun exists(path: String): Boolean = stat(path) != null
    fun openRead(path: String): InputStream = throw UnsupportedOperationException("openRead not supported")
    fun openWrite(path: String, append: Boolean = false): OutputStream = throw UnsupportedOperationException("openWrite not supported")
    fun capabilities(path: String): VfsCapabilities = VfsCapabilities.READ_ONLY
    fun toSharableUri(path: String): android.net.Uri? = null
}

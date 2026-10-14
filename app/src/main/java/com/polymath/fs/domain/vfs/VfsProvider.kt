package com.polymath.fs.domain.vfs

import java.io.InputStream
import java.io.OutputStream

interface VfsProvider {
    val schemes: Set<String>

    fun capabilities(uri: VfsUri): VfsCapabilities

    suspend fun list(uri: VfsUri): List<VfsStat>

    suspend fun stat(uri: VfsUri): VfsStat?

    fun openRead(uri: VfsUri): InputStream

    fun openWrite(uri: VfsUri, append: Boolean = false): OutputStream

    suspend fun delete(uri: VfsUri, recursive: Boolean = false): Boolean

    suspend fun mkdir(uri: VfsUri): Boolean

    suspend fun rename(uri: VfsUri, newName: String): Boolean

    suspend fun chmod(uri: VfsUri, mode: String): Boolean = false

    suspend fun chown(uri: VfsUri, owner: String): Boolean = false

    fun close() {}
}

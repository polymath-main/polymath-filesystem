package com.polymath.fs.domain.vfs

data class VfsStat(
    val name: String,
    val uri: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val size: Long,
    val lastModified: Long,
    val permissions: String?,
    val mimeType: String?,
    val capabilities: VfsCapabilities
)

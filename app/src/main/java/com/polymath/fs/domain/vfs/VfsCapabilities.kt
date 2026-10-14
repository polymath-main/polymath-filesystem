package com.polymath.fs.domain.vfs

data class VfsCapabilities(
    val canRead: Boolean,
    val canWrite: Boolean,
    val canRename: Boolean,
    val canDelete: Boolean,
    val canMkdir: Boolean,
    val canChmod: Boolean,
    val canChown: Boolean,
    val canSymlink: Boolean,
    val canStream: Boolean,
    val canSeek: Boolean
) {
    companion object {
        val READ_ONLY = VfsCapabilities(
            canRead = true,
            canWrite = false,
            canRename = false,
            canDelete = false,
            canMkdir = false,
            canChmod = false,
            canChown = false,
            canSymlink = false,
            canStream = true,
            canSeek = true
        )

        val FULL_LOCAL = VfsCapabilities(
            canRead = true,
            canWrite = true,
            canRename = true,
            canDelete = true,
            canMkdir = true,
            canChmod = true,
            canChown = true,
            canSymlink = true,
            canStream = true,
            canSeek = true
        )
    }
}

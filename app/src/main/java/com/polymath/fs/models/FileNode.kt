package com.polymath.fs.models

sealed class FileNode {
    abstract val name: String
    abstract val path: String
    abstract val size: Long
    abstract val lastModified: Long
    abstract val isDirectory: Boolean

    data class LocalFile(
        override val name: String,
        override val path: String,
        override val size: Long,
        override val lastModified: Long,
        override val isDirectory: Boolean
    ) : FileNode()

    data class RootFile(
        override val name: String,
        override val path: String,
        override val size: Long,
        override val lastModified: Long,
        override val isDirectory: Boolean,
        val permissions: String
    ) : FileNode()

    data class NetworkFile(
        override val name: String,
        override val path: String,
        override val size: Long,
        override val lastModified: Long,
        override val isDirectory: Boolean,
        val url: String
    ) : FileNode()
}

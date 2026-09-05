package com.polymath.fs.models

data class Progress(
    val currentBytes: Long,
    val totalBytes: Long,
    val currentFile: String,
    val percentage: Int
)

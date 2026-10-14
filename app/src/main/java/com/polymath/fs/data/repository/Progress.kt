package com.polymath.fs.data.repository

data class Progress(
    val currentBytes: Long,
    val totalBytes: Long,
    val isComplete: Boolean = false,
    val currentFile: String = "",
    val percentage: Int = if (totalBytes > 0) ((currentBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
)

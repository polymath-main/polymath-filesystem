package com.polymath.fs.models

data class TabState(
    val id: String,
    val currentPath: String,
    val history: List<String> = emptyList()
)

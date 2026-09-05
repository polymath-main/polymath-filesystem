package com.polymath.fs.models

data class FileBrowserUiState(
    val currentPath: String = "",
    val files: List<FileNode> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

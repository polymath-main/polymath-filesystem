package com.polymath.fs.models

data class Clipboard(val files: List<String>, val isCut: Boolean)

data class TabState(
    val id: String = java.util.UUID.randomUUID().toString(),
    val currentPath: String = "",
    val files: List<FileNode> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class FileBrowserUiState(
    val tabs: List<TabState> = emptyList(),
    val activeTabId: String = "",
    val clipboard: Clipboard? = null
) {
    val activeTab: TabState?
        get() = tabs.find { it.id == activeTabId }
}

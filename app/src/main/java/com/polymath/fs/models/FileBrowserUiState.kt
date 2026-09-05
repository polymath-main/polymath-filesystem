package com.polymath.fs.models

enum class SortOption { NAME, TYPE, TIME, MOSTLY_USED }
enum class SortDirection { ASCENDING, DESCENDING }
enum class ViewLayout { LIST, GRID }
enum class BoxSize { SMALL, MEDIUM, LARGE }

data class ViewOptions(
    val layout: ViewLayout = ViewLayout.LIST,
    val isVertical: Boolean = true,
    val boxSize: BoxSize = BoxSize.MEDIUM,
    val showDetails: Boolean = true
)

data class SortConfig(
    val option: SortOption = SortOption.NAME,
    val direction: SortDirection = SortDirection.ASCENDING
)

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
    val clipboard: Clipboard? = null,
    val error: String? = null,
    val viewOptions: ViewOptions = ViewOptions(),
    val sortConfig: SortConfig = SortConfig(),
    val recentFiles: List<FileNode> = emptyList()
) {
    val activeTab: TabState?
        get() = tabs.find { it.id == activeTabId }
}

package com.polymath.fs.viewmodels

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.polymath.fs.domain.usecase.ListDirUseCase
import com.polymath.fs.domain.usecase.DeleteFilesUseCase
import com.polymath.fs.domain.usecase.RenameUseCase
import com.polymath.fs.domain.usecase.CopyFilesUseCase
import com.polymath.fs.domain.usecase.MoveFilesUseCase
import com.polymath.fs.models.Clipboard
import com.polymath.fs.models.FileBrowserUiState
import com.polymath.fs.models.TabState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class FileSystemViewModel @Inject constructor(
    private val context: Context,
    private val listDirUseCase: ListDirUseCase,
    private val deleteFilesUseCase: DeleteFilesUseCase,
    private val renameUseCase: RenameUseCase,
    private val copyFilesUseCase: CopyFilesUseCase,
    private val moveFilesUseCase: MoveFilesUseCase,
    private val fileSystemRepository: com.polymath.fs.data.repository.FileSystemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == "icon_pack" || key == "theme") {
            syncPrefsToUiState()
        }
    }

    init {
        syncPrefsToUiState()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        val generalTab = TabState(id = "general", currentPath = "/")
        _uiState.update { state ->
            state.copy(tabs = listOf(generalTab), activeTabId = "general")
        }
        navigateTo("/", "general")
        newTab("/storage/emulated/0")
    }

    private fun syncPrefsToUiState() {
        val iconPackStr = prefs.getString("icon_pack", "default")
        val iconPack = when (iconPackStr) {
            "outline" -> com.polymath.fs.models.IconPack.OUTLINE
            "minimal" -> com.polymath.fs.models.IconPack.SOLID
            else -> com.polymath.fs.models.IconPack.FLUENT
        }
        _uiState.update { state ->
            state.copy(viewOptions = state.viewOptions.copy(iconPack = iconPack))
        }
    }

    fun newTab(path: String = "/storage/emulated/0") {
        val newTab = TabState(currentPath = path)
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + listOf(newTab),
                activeTabId = newTab.id
            )
        }
        navigateTo(path, newTab.id)
    }

    fun closeTab(tabId: String) {
        _uiState.update { state ->
            val remainingTabs = state.tabs.filter { it.id != tabId }
            val nextActiveId = if (state.activeTabId == tabId) {
                remainingTabs.lastOrNull()?.id ?: ""
            } else {
                state.activeTabId
            }
            state.copy(tabs = remainingTabs, activeTabId = nextActiveId)
        }
        val newState = _uiState.value
        if (newState.tabs.isEmpty()) {
            newTab()
        }
    }

    fun switchTab(tabId: String) {
        _uiState.update { state ->
            state.copy(activeTabId = tabId)
        }
    }

    fun navigateTo(path: String, tabId: String? = null) {
        val targetTabId = tabId ?: _uiState.value.activeTabId
        if (targetTabId.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { state ->
                val updatedTabs = state.tabs.map { 
                    if (it.id == targetTabId) it.copy(isLoading = true, currentPath = path, error = null) else it 
                }
                state.copy(tabs = updatedTabs)
            }
            
            val result = listDirUseCase(path)
            
            result.onSuccess { files ->
                val sortedFiles = sortFileList(files, _uiState.value.sortConfig)
                
                _uiState.update { state ->
                    val updatedTabs = state.tabs.map { 
                        if (it.id == targetTabId) it.copy(isLoading = false, files = sortedFiles, currentPath = path) else it 
                    }
                    state.copy(tabs = updatedTabs)
                }
            }.onFailure { e ->
                _uiState.update { state ->
                    val updatedTabs = state.tabs.map { 
                        if (it.id == targetTabId) it.copy(isLoading = false, error = e.message ?: "Unknown error") else it 
                    }
                    state.copy(tabs = updatedTabs)
                }
            }
        }
    }

    fun navigateUp() {
        val activeTab = _uiState.value.activeTab ?: return
        val currentPath = activeTab.currentPath
        if (currentPath.length > 1) {
            val parentPath = currentPath.substringBeforeLast('/')
            val resolvedParent = if (parentPath.isEmpty()) "/" else parentPath
            navigateTo(resolvedParent)
        }
    }

    fun deleteFiles(paths: List<String>) {
        viewModelScope.launch {
            val result = deleteFilesUseCase(paths)
            if (result.isSuccess) {
                val activeTab = _uiState.value.activeTab
                if (activeTab != null) navigateTo(activeTab.currentPath)
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun renameFile(oldPath: String, newName: String) {
        viewModelScope.launch {
            val result = renameUseCase(oldPath, newName)
            if (result.isSuccess) {
                val activeTab = _uiState.value.activeTab
                if (activeTab != null) navigateTo(activeTab.currentPath)
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun copyFiles(paths: List<String>) {
        _uiState.update { it.copy(clipboard = Clipboard(paths, isCut = false)) }
    }

    fun cutFiles(paths: List<String>) {
        _uiState.update { it.copy(clipboard = Clipboard(paths, isCut = true)) }
    }

    fun pasteFiles() {
        val clipboard = _uiState.value.clipboard ?: return
        val activeTab = _uiState.value.activeTab ?: return
        val currentPath = activeTab.currentPath
        viewModelScope.launch {
            try {
                if (clipboard.isCut) {
                    moveFilesUseCase(clipboard.files, currentPath).collect { progress ->
                        // Optional: handle progress
                    }
                    _uiState.update { it.copy(clipboard = null) }
                } else {
                    copyFilesUseCase(clipboard.files, currentPath).collect { progress ->
                        // Optional: handle progress
                    }
                }
                navigateTo(currentPath)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun chmod(path: String, mode: String) {
        viewModelScope.launch {
            val success = fileSystemRepository.chmod(path, mode)
            if (success) {
                val activeTab = _uiState.value.activeTab
                if (activeTab != null) navigateTo(activeTab.currentPath)
            } else {
                _uiState.update { it.copy(error = "chmod failed") }
            }
        }
    }

    fun chown(path: String, owner: String) {
        viewModelScope.launch {
            val success = fileSystemRepository.chown(path, owner)
            if (success) {
                val activeTab = _uiState.value.activeTab
                if (activeTab != null) navigateTo(activeTab.currentPath)
            } else {
                _uiState.update { it.copy(error = "chown failed") }
            }
        }
    }

    fun setSortConfig(option: com.polymath.fs.models.SortOption, direction: com.polymath.fs.models.SortDirection) {
        _uiState.update { state ->
            val newConfig = com.polymath.fs.models.SortConfig(option, direction)
            val updatedTabs = state.tabs.map { tab ->
                tab.copy(files = sortFileList(tab.files, newConfig))
            }
            state.copy(sortConfig = newConfig, tabs = updatedTabs)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    private fun sortFileList(
        files: List<com.polymath.fs.models.FileNode>,
        sortConfig: com.polymath.fs.models.SortConfig
    ): List<com.polymath.fs.models.FileNode> {
        val dirSort = compareBy<com.polymath.fs.models.FileNode> { !it.isDirectory }
        val mainSort: Comparator<com.polymath.fs.models.FileNode> = when (sortConfig.option) {
            com.polymath.fs.models.SortOption.NAME -> compareBy { it.name.lowercase() }
            com.polymath.fs.models.SortOption.TYPE -> compareBy { it.name.substringAfterLast('.', "").lowercase() }
            com.polymath.fs.models.SortOption.TIME -> compareBy { it.lastModified }
            com.polymath.fs.models.SortOption.SIZE -> compareBy { it.size }
            com.polymath.fs.models.SortOption.MOSTLY_USED -> compareBy { it.name.lowercase() }
        }
        val comparator = if (sortConfig.direction == com.polymath.fs.models.SortDirection.DESCENDING) {
            dirSort.then(mainSort.reversed())
        } else {
            dirSort.then(mainSort)
        }
        return files.sortedWith(comparator)
    }

    fun setViewOptions(options: com.polymath.fs.models.ViewOptions) {
        _uiState.update { it.copy(viewOptions = options) }
    }

    fun addRecentFile(file: com.polymath.fs.models.FileNode) {
        if (file.isDirectory) return
        _uiState.update { state ->
            val newRecents = (listOf(file) + state.recentFiles.filter { it.path != file.path }).take(5)
            state.copy(recentFiles = newRecents)
        }
    }

    companion object {
        fun provideFactory(app: com.polymath.fs.PolymathApp): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FileSystemViewModel(
                        app,
                        app.listDirUseCase,
                        app.deleteFilesUseCase,
                        app.renameUseCase,
                        app.copyFilesUseCase,
                        app.moveFilesUseCase,
                        app.fileSystemRepository
                    ) as T
                }
            }
        }
    }
}

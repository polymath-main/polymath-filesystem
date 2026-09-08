package com.polymath.fs.viewmodels

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.polymath.fs.core.FlowStateManager
import com.polymath.fs.core.RahmanFlowState
import com.polymath.fs.core.TabFlowState
import com.polymath.fs.core.SmartFolderManager
import com.polymath.fs.domain.usecase.ListDirUseCase
import com.polymath.fs.domain.usecase.DeleteFilesUseCase
import com.polymath.fs.domain.usecase.RenameUseCase
import com.polymath.fs.domain.usecase.CopyFilesUseCase
import com.polymath.fs.domain.usecase.MoveFilesUseCase
import com.polymath.fs.models.Clipboard
import com.polymath.fs.models.FileBrowserUiState
import com.polymath.fs.models.TabState
import kotlinx.coroutines.Dispatchers
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
    private val flowStateManager: FlowStateManager,
    private val smartFolderManager: SmartFolderManager,
    private val intentEngine: com.polymath.fs.core.IntentEngine,
    val synapseEngine: com.polymath.fs.core.SynapseEngine,
    val fileSystemRepository: com.polymath.fs.data.repository.IFileSystemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()
    
    // In-memory zero-storage Rift tracking
    private val activeRiftPaths = mutableSetOf<String>()

    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == "icon_pack" || key == "theme") {
            syncPrefsToUiState()
        } else if (key == "root_explorer") {
            // Root explorer permission mode changed; reload current active tabs
            _uiState.value.tabs.forEach { tab ->
                navigateTo(tab.currentPath, tab.id)
            }
        }
    }

    init {
        syncPrefsToUiState()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        
        viewModelScope.launch(Dispatchers.IO) {
            val savedFlow = flowStateManager.loadState()
            if (savedFlow != null && savedFlow.tabs.isNotEmpty()) {
                val reconstructedTabs = savedFlow.tabs.map { tabFlow ->
                    TabState(
                        id = tabFlow.id,
                        currentPath = tabFlow.path,
                        scrollPosition = tabFlow.scrollPosition,
                        terminalHistory = tabFlow.terminalHistory
                    )
                }
                _uiState.update { it.copy(tabs = reconstructedTabs, activeTabId = savedFlow.activeTabId) }
                // Load files for active tab
                val active = reconstructedTabs.find { it.id == savedFlow.activeTabId } ?: reconstructedTabs.first()
                navigateTo(active.currentPath, active.id)
            } else {
                val generalTab = TabState(id = "general", currentPath = "/")
                _uiState.update { state ->
                    state.copy(tabs = listOf(generalTab), activeTabId = "general")
                }
                navigateTo("/", "general")
                newTab("/storage/emulated/0")
            }
        }
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
        persistFlowState()
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
        persistFlowState()
    }

    fun switchTab(tabId: String) {
        _uiState.update { state ->
            state.copy(activeTabId = tabId)
        }
        persistFlowState()
    }

    fun navigateTo(path: String, tabId: String? = null) {
        val targetTabId = tabId ?: _uiState.value.activeTabId
        if (targetTabId.isEmpty()) return
        logIntent("navigated", path, "directory open")

        viewModelScope.launch {
            _uiState.update { state ->
                val updatedTabs = state.tabs.map { tab ->
                    if (tab.id == targetTabId) tab.copy(isLoading = true, error = null, currentPath = path) else tab
                }
                state.copy(tabs = updatedTabs)
            }

            try {
                val files = if (path.startsWith("synapse://")) {
                    val ip = path.substringAfter("synapse://").substringBefore("/")
                    val remotePath = "/" + path.substringAfter("synapse://").substringAfter("/")
                    synapseEngine.fetchRemoteDirectory(ip, remotePath)
                } else {
                    listDirUseCase(path).getOrThrow()
                }
                
                val currentConfig = _uiState.value.sortConfig
                val sortedFiles = sortFileList(files, currentConfig).map { file ->
                    // Apply the zero-storage in-memory Rift state dynamically
                    file.apply { isRift = activeRiftPaths.contains(this.path) }
                }
                
                smartFolderManager.categorizeFiles(sortedFiles).collect { categorized ->
                    _uiState.update { state ->
                        val updatedTabs = state.tabs.map { 
                            if (it.id == targetTabId) it.copy(isLoading = false, files = sortedFiles, smartFolders = categorized, currentPath = path) else it 
                        }
                        state.copy(tabs = updatedTabs)
                    }
                }
            } catch (e: Exception) {
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
            logIntent("navigated", resolvedParent, "directory open")
            navigateTo(resolvedParent)
        }
        persistFlowState()
    }
    
    private fun persistFlowState() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            val flowTabs = state.tabs.map { 
                TabFlowState(it.id, it.currentPath, it.scrollPosition, it.terminalHistory) 
            }
            flowStateManager.saveState(RahmanFlowState(flowTabs, state.activeTabId))
        }
    }
    
    fun updateTabState(scrollPosition: Int? = null, terminalHistory: String? = null) {
        val activeId = _uiState.value.activeTabId
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == activeId) {
                    tab.copy(
                        scrollPosition = scrollPosition ?: tab.scrollPosition,
                        terminalHistory = terminalHistory ?: tab.terminalHistory
                    )
                } else tab
            }
            state.copy(tabs = updatedTabs)
        }
        persistFlowState()
    }

    fun refreshCurrentDirectory() {
        val activeTab = _uiState.value.activeTab ?: return
        navigateTo(activeTab.currentPath)
    }

    fun toggleRiftState(path: String) {
        if (activeRiftPaths.contains(path)) {
            activeRiftPaths.remove(path)
        } else {
            activeRiftPaths.add(path)
        }
        val activeTab = _uiState.value.activeTab
        if (activeTab != null) navigateTo(activeTab.currentPath)
    }

    fun deleteFiles(paths: List<String>) {
        viewModelScope.launch {
            val result = deleteFilesUseCase(paths)
            if (result.isSuccess) {
                paths.forEach { logIntent("deleted", it, "file delete") }
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
                logIntent("renamed", "$oldPath -> $newName", "file rename")
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

    private var searchJob: kotlinx.coroutines.Job? = null

    fun logIntent(action: String, path: String, context: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            intentEngine.logIntent(action, path, context)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        
        if (query.trim().isNotEmpty()) {
            searchJob = viewModelScope.launch(Dispatchers.IO) {
                val intentMap = intentEngine.resolveIntent(query)
                _uiState.update { it.copy(intentResults = intentMap) }

                val activeTab = _uiState.value.activeTab ?: return@launch
                val startDir = if (activeTab.currentPath.isNotBlank() && activeTab.currentPath != "/") activeTab.currentPath else "/storage/emulated/0"
                val accumulatedResults = mutableListOf<com.polymath.fs.models.FileNode>()
                com.polymath.fs.core.DeepSearchEngine.search(
                    com.polymath.fs.core.DeepSearchEngine.SearchQuery(
                        keyword = query,
                        rootPath = startDir
                    )
                ).collect { matchedNodes ->
                    accumulatedResults.addAll(matchedNodes)
                    val snapshot = accumulatedResults.toList()
                    _uiState.update { state ->
                        val updatedTabs = state.tabs.map { tab ->
                            if (tab.id == state.activeTabId) {
                                tab.copy(files = snapshot)
                            } else {
                                tab
                            }
                        }
                        state.copy(tabs = updatedTabs)
                    }
                }
            }
        } else {
            _uiState.update { it.copy(intentResults = emptyMap()) }
            val activeTab = _uiState.value.activeTab
            if (activeTab != null) {
                navigateTo(activeTab.currentPath)
            }
        }
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

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
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
                        app.flowStateManager,
                        app.intentEngine,
                        app.synapseEngine,
                        app.fileSystemRepository
                    ) as T
                }
            }
        }
    }
}

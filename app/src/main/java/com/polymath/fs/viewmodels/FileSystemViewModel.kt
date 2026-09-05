package com.polymath.fs.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polymath.fs.domain.usecase.ListDirUseCase
import com.polymath.fs.models.FileBrowserUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FileSystemViewModel @Inject constructor(
    private val listDirUseCase: ListDirUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    init {
        navigateTo("/storage/emulated/0")
    }

    fun navigateTo(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, currentPath = path, error = null) }
            
            val result = listDirUseCase(path)
            
            result.onSuccess { files ->
                val sortedFiles = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        files = sortedFiles,
                        currentPath = path
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    fun navigateUp() {
        val currentPath = _uiState.value.currentPath
        if (currentPath.length > 1) {
            val parentPath = currentPath.substringBeforeLast('/')
            val resolvedParent = if (parentPath.isEmpty()) "/" else parentPath
            navigateTo(resolvedParent)
        }
    }
}

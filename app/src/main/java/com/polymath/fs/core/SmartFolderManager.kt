package com.polymath.fs.core

import com.polymath.fs.models.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartFolderManager @Inject constructor() {

    fun categorizeFiles(files: List<FileNode>): Flow<Map<String, List<FileNode>>> = flow {
        val categorized = mutableMapOf<String, MutableList<FileNode>>()
        categorized["Media"] = mutableListOf()
        categorized["Code"] = mutableListOf()
        categorized["Documents"] = mutableListOf()
        categorized["Other"] = mutableListOf()

        for (file in files) {
            if (file.isDirectory) continue
            val ext = file.name.substringAfterLast('.', "").lowercase()
            when (ext) {
                "mp4", "jpg", "png", "mp3" -> categorized["Media"]?.add(file)
                "kt", "java", "js", "json", "py" -> categorized["Code"]?.add(file)
                "pdf", "txt", "md" -> categorized["Documents"]?.add(file)
                else -> categorized["Other"]?.add(file)
            }
        }
        
        emit(categorized)
    }.flowOn(Dispatchers.Default)
}

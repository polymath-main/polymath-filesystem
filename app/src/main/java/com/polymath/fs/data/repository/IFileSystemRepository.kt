package com.polymath.fs.data.repository

import com.polymath.fs.models.FileNode
import kotlinx.coroutines.flow.Flow

interface IFileSystemRepository {
    suspend fun listDir(path: String): List<FileNode>
    fun copy(src: List<String>, dest: String): Flow<Progress>
    fun move(src: List<String>, dest: String): Flow<Progress>
    suspend fun delete(paths: List<String>): Boolean
    suspend fun mkdir(path: String): Boolean
    suspend fun rename(oldPath: String, newName: String): Boolean
    suspend fun chmod(path: String, mode: String): Boolean
    suspend fun chown(path: String, owner: String): Boolean
}

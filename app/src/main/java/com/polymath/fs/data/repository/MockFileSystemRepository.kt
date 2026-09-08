package com.polymath.fs.data.repository

import android.util.Log
import com.polymath.fs.models.FileNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MockFileSystemRepository : IFileSystemRepository {
    private val TAG = "MockFileSystemRepo"
    
    // In a real dry-run, we might build an in-memory VFS here, 
    // but for now we just log the intents to prove the concept.

    override suspend fun listDir(path: String): List<FileNode> {
        Log.d(TAG, "[Dry Run] listDir requested for path: $path")
        return emptyList()
    }

    override fun copy(src: List<String>, dest: String): Flow<Progress> = flow {
        Log.d(TAG, "[Dry Run] copy requested from $src to $dest")
        emit(Progress(src.size.toLong(), src.size.toLong(), true))
    }

    override fun move(src: List<String>, dest: String): Flow<Progress> = flow {
        Log.d(TAG, "[Dry Run] move requested from $src to $dest")
        emit(Progress(src.size.toLong(), src.size.toLong(), true))
    }

    override suspend fun delete(paths: List<String>): Boolean {
        Log.d(TAG, "[Dry Run] delete requested for paths: $paths")
        return true
    }

    override suspend fun mkdir(path: String): Boolean {
        Log.d(TAG, "[Dry Run] mkdir requested for path: $path")
        return true
    }

    override suspend fun rename(oldPath: String, newName: String): Boolean {
        Log.d(TAG, "[Dry Run] rename requested from $oldPath to $newName")
        return true
    }

    override suspend fun chmod(path: String, mode: String): Boolean {
        Log.d(TAG, "[Dry Run] chmod requested: $mode on $path")
        return true
    }

    override suspend fun chown(path: String, owner: String): Boolean {
        Log.d(TAG, "[Dry Run] chown requested: $owner on $path")
        return true
    }
}

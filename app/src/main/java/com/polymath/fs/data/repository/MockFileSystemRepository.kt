package com.polymath.fs.data.repository

import android.util.Log
import com.polymath.fs.models.FileNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MockFileSystemRepository(
    private val realRepository: IFileSystemRepository? = null
) : IFileSystemRepository {
    private val TAG = "MockFileSystemRepo"
    
    // The Zero-Storage VFS Overlay Layer
    // Maps a directory path to its list of in-memory files (overrides real files)
    private val vfsMemoryOverlay = mutableMapOf<String, MutableList<FileNode>>()
    
    // Tracks paths that have been virtually deleted
    private val vfsDeletedPaths = mutableSetOf<String>()

    override suspend fun listDir(path: String): List<FileNode> {
        Log.d(TAG, "[VFS] listDir requested for path: $path")
        
        // Fetch real files if a real repo is provided
        val realFiles = try {
            realRepository?.listDir(path) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        
        // Filter out virtually deleted files
        val filteredRealFiles = realFiles.filter { it.path !in vfsDeletedPaths }
        
        // Get virtual files created in memory
        val virtualFiles = vfsMemoryOverlay[path] ?: emptyList()
        
        // Merge real and virtual files, favoring virtual files for duplicates
        val merged = (filteredRealFiles + virtualFiles)
            .distinctBy { it.name }
            .sortedBy { it.name }
            
        return merged
    }

    override fun copy(src: List<String>, dest: String): Flow<Progress> = flow {
        Log.d(TAG, "[VFS] copy requested from $src to $dest")
        src.forEach { s ->
            val fileName = java.io.File(s).name
            val destPath = if (dest.endsWith("/")) "$dest$fileName" else "$dest/$fileName"
            
            // Create a virtual file in the destination
            val virtualFile = FileNode.LocalFile(
                name = fileName,
                path = destPath,
                size = 1024L,
                lastModified = System.currentTimeMillis(),
                isDirectory = false
            )
            vfsMemoryOverlay.getOrPut(dest) { mutableListOf() }.add(virtualFile)
        }
        emit(Progress(src.size.toLong(), src.size.toLong(), true))
    }

    override fun move(src: List<String>, dest: String): Flow<Progress> = flow {
        Log.d(TAG, "[VFS] move requested from $src to $dest")
        src.forEach { s ->
            // Mark source as virtually deleted
            vfsDeletedPaths.add(s)
            
            val fileName = java.io.File(s).name
            val destPath = if (dest.endsWith("/")) "$dest$fileName" else "$dest/$fileName"
            
            // Create a virtual file in the destination
            val virtualFile = FileNode.LocalFile(
                name = fileName,
                path = destPath,
                size = 1024L,
                lastModified = System.currentTimeMillis(),
                isDirectory = false
            )
            vfsMemoryOverlay.getOrPut(dest) { mutableListOf() }.add(virtualFile)
        }
        emit(Progress(src.size.toLong(), src.size.toLong(), true))
    }

    override suspend fun delete(paths: List<String>): Boolean {
        Log.d(TAG, "[VFS] delete requested for paths: $paths")
        paths.forEach { path ->
            vfsDeletedPaths.add(path)
            // If it was in memory, remove it
            vfsMemoryOverlay.values.forEach { list ->
                list.removeAll { it.path == path }
            }
        }
        return true
    }

    override suspend fun mkdir(path: String): Boolean {
        Log.d(TAG, "[VFS] mkdir requested for path: $path")
        val file = java.io.File(path)
        val parentDir = file.parent ?: "/"
        
        val virtualDir = FileNode.LocalFile(
            name = file.name,
            path = path,
            size = 0L,
            lastModified = System.currentTimeMillis(),
            isDirectory = true
        )
        vfsMemoryOverlay.getOrPut(parentDir) { mutableListOf() }.add(virtualDir)
        return true
    }

    override suspend fun rename(oldPath: String, newName: String): Boolean {
        Log.d(TAG, "[VFS] rename requested from $oldPath to $newName")
        vfsDeletedPaths.add(oldPath)
        
        val newFile = java.io.File(newName)
        val virtualFile = FileNode.LocalFile(
            name = newFile.name,
            path = newName,
            size = 1024L, // mock size
            lastModified = System.currentTimeMillis(),
            isDirectory = false
        )
        val parentDir = newFile.parent ?: "/"
        vfsMemoryOverlay.getOrPut(parentDir) { mutableListOf() }.add(virtualFile)
        
        return true
    }

    override suspend fun chmod(path: String, mode: String): Boolean {
        Log.d(TAG, "[VFS] chmod requested: $mode on $path")
        return true
    }

    override suspend fun chown(path: String, owner: String): Boolean {
        Log.d(TAG, "[VFS] chown requested: $owner on $path")
        return true
    }
}

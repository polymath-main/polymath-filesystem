package com.polymath.fs.domain.usecase

import com.polymath.fs.data.repository.IFileSystemRepository
import com.polymath.fs.models.FileNode
import javax.inject.Inject

class ListDirUseCase @Inject constructor(
    private val repository: IFileSystemRepository
) {
    suspend operator fun invoke(path: String): Result<List<FileNode>> {
        return try {
            val files = repository.listDir(path)
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

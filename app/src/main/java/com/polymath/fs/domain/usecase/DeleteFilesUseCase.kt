package com.polymath.fs.domain.usecase

import com.polymath.fs.data.repository.IFileSystemRepository
import javax.inject.Inject

class DeleteFilesUseCase @Inject constructor(
    private val repository: IFileSystemRepository
) {
    suspend operator fun invoke(paths: List<String>): Result<Boolean> {
        return try {
            val allPathsToDelete = paths.toMutableList()
            // Auto-cleanup any associated semantic wrappers
            paths.forEach { path ->
                val riftPath = "$path.rift"
                if (java.io.File(riftPath).exists()) {
                    allPathsToDelete.add(riftPath)
                }
            }
            val success = repository.delete(allPathsToDelete)
            if (success) Result.success(true) else Result.failure(Exception("Delete failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.polymath.fs.domain.usecase

import com.polymath.fs.data.repository.IFileSystemRepository
import javax.inject.Inject

class RenameUseCase @Inject constructor(
    private val repository: IFileSystemRepository
) {
    suspend operator fun invoke(oldPath: String, newName: String): Result<Boolean> {
        return try {
            val success = repository.rename(oldPath, newName)
            if (success) {
                // Sync companion rift wrapper rename if it exists
                val oldRift = java.io.File("$oldPath.rift")
                if (oldRift.exists()) {
                    repository.rename(oldRift.absolutePath, "$newName.rift")
                }
                Result.success(true)
            } else {
                Result.failure(Exception("Rename failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

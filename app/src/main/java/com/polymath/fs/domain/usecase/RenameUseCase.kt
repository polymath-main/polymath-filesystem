package com.polymath.fs.domain.usecase

import com.polymath.fs.data.repository.FileSystemRepository
import javax.inject.Inject

class RenameUseCase @Inject constructor(
    private val repository: FileSystemRepository
) {
    suspend operator fun invoke(oldPath: String, newName: String): Result<Boolean> {
        return try {
            val success = repository.rename(oldPath, newName)
            if (success) Result.success(true) else Result.failure(Exception("Rename failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.polymath.fs.domain.usecase

import com.polymath.fs.data.repository.FileSystemRepository
import javax.inject.Inject

class DeleteFilesUseCase @Inject constructor(
    private val repository: FileSystemRepository
) {
    suspend operator fun invoke(paths: List<String>): Result<Boolean> {
        return try {
            val success = repository.delete(paths)
            if (success) Result.success(true) else Result.failure(Exception("Delete failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

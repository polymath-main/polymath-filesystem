package com.polymath.fs.domain.usecase

import com.polymath.fs.data.repository.FileSystemRepository
import javax.inject.Inject

class MkdirUseCase @Inject constructor(
    private val repository: FileSystemRepository
) {
    suspend operator fun invoke(path: String): Result<Boolean> {
        return try {
            val success = repository.mkdir(path)
            if (success) Result.success(true) else Result.failure(Exception("Mkdir failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

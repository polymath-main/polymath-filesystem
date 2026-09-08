package com.polymath.fs.domain.usecase

import com.polymath.fs.data.repository.IFileSystemRepository
import com.polymath.fs.data.repository.Progress
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CopyFilesUseCase @Inject constructor(
    private val repository: IFileSystemRepository
) {
    operator fun invoke(src: List<String>, dest: String): Flow<Progress> {
        return repository.copy(src, dest)
    }
}

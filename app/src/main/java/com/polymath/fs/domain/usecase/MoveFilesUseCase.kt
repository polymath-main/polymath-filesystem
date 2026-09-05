package com.polymath.fs.domain.usecase

import com.polymath.fs.data.repository.FileSystemRepository
import com.polymath.fs.data.repository.Progress
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MoveFilesUseCase @Inject constructor(
    private val repository: FileSystemRepository
) {
    operator fun invoke(src: List<String>, dest: String): Flow<Progress> {
        return repository.move(src, dest)
    }
}

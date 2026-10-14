package com.polymath.fs.domain.vfs

import com.polymath.fs.data.repository.Progress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class TransferEngine @Inject constructor(
    private val registry: VfsRegistry,
    private val bufferSize: Int = BUFFER_SIZE,
    private val throttleMs: Long = PROGRESS_THROTTLE_MS,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    companion object {
        const val BUFFER_SIZE = 128 * 1024 // 128 KiB bounded chunk buffer
        const val PROGRESS_THROTTLE_MS = 200L // 200ms throttle interval
    }

    /**
     * Copies a list of files or directory trees to the destination directory.
     * Recursively traverses source directory trees across any provider.
     */
    fun copy(src: List<String>, dest: String): Flow<Progress> = executeTransfer(src, dest, deleteSource = false)

    /**
     * Moves a list of files or directory trees to the destination directory.
     * Recursively copies source directory trees and cleans up sources upon success.
     */
    fun move(src: List<String>, dest: String): Flow<Progress> = executeTransfer(src, dest, deleteSource = true)

    private fun executeTransfer(
        src: List<String>,
        dest: String,
        deleteSource: Boolean
    ): Flow<Progress> = flow {
        if (src.isEmpty()) {
            emit(Progress(currentBytes = 0L, totalBytes = 0L, isComplete = true, currentFile = ""))
            return@flow
        }

        val destUri = VfsUri.parse(dest)
        val destProvider = registry.providerFor(destUri)

        // Ensure base target directory exists on destination provider
        val destStat = destProvider.stat(destUri)
        if (destStat == null || !destStat.isDirectory) {
            destProvider.mkdir(destUri)
        }

        val srcUris = src.map { VfsUri.parse(it) }
        val totalBytes = calculateTotalBytes(srcUris)

        val state = TransferProgressState(
            bytesTransferred = 0L,
            lastEmitTime = timeProvider(),
            lastFileName = ""
        )

        // Initial 0% progress emission
        emit(
            Progress(
                currentBytes = 0L,
                totalBytes = totalBytes,
                isComplete = false,
                currentFile = ""
            )
        )

        for (srcUri in srcUris) {
            coroutineContext.ensureActive()
            val srcProvider = registry.providerFor(srcUri)
            val srcStat = srcProvider.stat(srcUri)
                ?: throw IOException("Source not found: ${srcUri.serialized()}")

            val entryName = srcStat.name.ifEmpty {
                srcUri.path.substringAfterLast('/').ifEmpty { "unnamed" }
            }
            val targetUri = resolveChildUri(destUri, entryName)

            if (srcStat.isDirectory) {
                copyDirectoryRecursive(srcUri, targetUri, totalBytes, state) { emit(it) }
            } else {
                copySingleFile(srcUri, targetUri, entryName, totalBytes, state) { emit(it) }
            }

            if (deleteSource) {
                val deleted = srcProvider.delete(srcUri, recursive = true)
                if (!deleted) {
                    throw IOException("Failed to delete source after transfer: ${srcUri.serialized()}")
                }
            }
        }

        // Final 100% progress emission
        val finalTotal = if (totalBytes > 0) totalBytes else state.bytesTransferred
        emit(
            Progress(
                currentBytes = maxOf(finalTotal, state.bytesTransferred),
                totalBytes = maxOf(finalTotal, state.bytesTransferred),
                isComplete = true,
                currentFile = state.lastFileName
            )
        )
    }

    private suspend fun copyDirectoryRecursive(
        srcDirUri: VfsUri,
        destDirUri: VfsUri,
        totalBytes: Long,
        state: TransferProgressState,
        emitProgress: suspend (Progress) -> Unit
    ) {
        coroutineContext.ensureActive()
        val destProvider = registry.providerFor(destDirUri)
        val destStat = destProvider.stat(destDirUri)
        if (destStat == null || !destStat.isDirectory) {
            destProvider.mkdir(destDirUri)
        }

        val srcProvider = registry.providerFor(srcDirUri)
        val children = srcProvider.list(srcDirUri)

        for (child in children) {
            coroutineContext.ensureActive()
            if (child.name == "." || child.name == "..") continue

            val childSrcUri = resolveSourceChildUri(srcDirUri, child)
            val childDestUri = resolveChildUri(destDirUri, child.name)

            if (child.isDirectory) {
                copyDirectoryRecursive(childSrcUri, childDestUri, totalBytes, state, emitProgress)
            } else {
                copySingleFile(childSrcUri, childDestUri, child.name, totalBytes, state, emitProgress)
            }
        }
    }

    private suspend fun copySingleFile(
        srcUri: VfsUri,
        destUri: VfsUri,
        fileName: String,
        totalBytes: Long,
        state: TransferProgressState,
        emitProgress: suspend (Progress) -> Unit
    ) = withContext(Dispatchers.IO) {
        val srcProvider = registry.providerFor(srcUri)
        val destProvider = registry.providerFor(destUri)

        srcProvider.openRead(srcUri).use { input ->
            destProvider.openWrite(destUri, append = false).use { output ->
                copyStreamInternal(
                    input = input,
                    output = output,
                    totalBytes = totalBytes,
                    fileName = fileName,
                    state = state,
                    emitProgress = emitProgress
                )
            }
        }
    }

    /**
     * Standalone stream copying utility with 128 KiB bounded buffer, coroutine cancellation
     * safety check on every chunk, and throttled progress reporting.
     */
    suspend fun copyStream(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long = -1L,
        bytesAlreadyTransferred: Long = 0L,
        fileName: String = "",
        onProgress: (suspend (Progress) -> Unit)? = null
    ): Long = withContext(Dispatchers.IO) {
        val state = TransferProgressState(
            bytesTransferred = bytesAlreadyTransferred,
            lastEmitTime = timeProvider(),
            lastFileName = fileName
        )

        if (onProgress != null) {
            val initialTotal = if (totalBytes > 0) totalBytes else 0L
            onProgress(
                Progress(
                    currentBytes = bytesAlreadyTransferred,
                    totalBytes = initialTotal,
                    isComplete = false,
                    currentFile = fileName
                )
            )
        }

        val copied = copyStreamInternal(
            input = input,
            output = output,
            totalBytes = totalBytes,
            fileName = fileName,
            state = state,
            emitProgress = onProgress ?: {}
        )

        if (onProgress != null) {
            val finalTotal = if (totalBytes > 0) totalBytes else state.bytesTransferred
            onProgress(
                Progress(
                    currentBytes = state.bytesTransferred,
                    totalBytes = finalTotal,
                    isComplete = true,
                    currentFile = fileName
                )
            )
        }

        copied
    }

    private suspend fun copyStreamInternal(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        fileName: String,
        state: TransferProgressState,
        emitProgress: suspend (Progress) -> Unit
    ): Long {
        val buffer = ByteArray(bufferSize)
        var bytesCopied = 0L
        var bytesRead: Int

        while (input.read(buffer).also { bytesRead = it } != -1) {
            coroutineContext.ensureActive()
            output.write(buffer, 0, bytesRead)
            bytesCopied += bytesRead
            state.bytesTransferred += bytesRead
            state.lastFileName = fileName

            val now = timeProvider()
            if (now - state.lastEmitTime >= throttleMs) {
                state.lastEmitTime = now
                val currentTotal = if (totalBytes > 0) totalBytes else state.bytesTransferred
                emitProgress(
                    Progress(
                        currentBytes = state.bytesTransferred,
                        totalBytes = currentTotal,
                        isComplete = false,
                        currentFile = fileName
                    )
                )
            }
        }
        output.flush()
        return bytesCopied
    }

    private suspend fun calculateTotalBytes(srcUris: List<VfsUri>): Long {
        var total = 0L
        for (uri in srcUris) {
            total += calculateUriBytes(uri)
        }
        return total
    }

    private suspend fun calculateUriBytes(uri: VfsUri): Long {
        return try {
            val provider = registry.providerFor(uri)
            val stat = provider.stat(uri) ?: return 0L
            if (stat.isDirectory) {
                var sum = 0L
                val children = provider.list(uri)
                for (child in children) {
                    if (child.name == "." || child.name == "..") continue
                    val childUri = resolveSourceChildUri(uri, child)
                    sum += calculateUriBytes(childUri)
                }
                sum
            } else {
                maxOf(0L, stat.size)
            }
        } catch (_: Exception) {
            0L
        }
    }

    fun resolveChildUri(parentUri: VfsUri, childName: String): VfsUri {
        val cleanChild = childName.trimStart('/')
        val parentPath = parentUri.path.trimEnd('/')
        val newPath = if (parentPath.isEmpty()) "/$cleanChild" else "$parentPath/$cleanChild"
        return parentUri.copy(path = newPath)
    }

    private fun resolveSourceChildUri(parentUri: VfsUri, childStat: VfsStat): VfsUri {
        return if (childStat.uri.isNotBlank()) {
            try {
                VfsUri.parse(childStat.uri)
            } catch (_: Exception) {
                resolveChildUri(parentUri, childStat.name)
            }
        } else {
            resolveChildUri(parentUri, childStat.name)
        }
    }

    private class TransferProgressState(
        var bytesTransferred: Long = 0L,
        var lastEmitTime: Long = 0L,
        var lastFileName: String = ""
    )
}

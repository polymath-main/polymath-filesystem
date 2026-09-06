package com.polymath.fs.core

import com.polymath.fs.models.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

object DeepSearchEngine {

    enum class FileCategoryFilter {
        ALL,
        IMAGES,
        VIDEOS,
        AUDIO,
        DOCUMENTS,
        ARCHIVES,
        CODE,
        APKS
    }

    data class SearchQuery(
        val keyword: String,
        val rootPath: String = "/storage/emulated/0",
        val category: FileCategoryFilter = FileCategoryFilter.ALL,
        val matchCase: Boolean = false,
        val includeHidden: Boolean = false,
        val maxDepth: Int = 12
    )

    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic")
    private val VIDEO_EXTS = setOf("mp4", "mkv", "mov", "avi", "webm", "flv", "3gp", "m4v")
    private val AUDIO_EXTS = setOf("mp3", "wav", "ogg", "flac", "m4a", "aac", "opus", "wma")
    private val DOC_EXTS = setOf("pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "csv", "epub")
    private val ARCHIVE_EXTS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
    private val CODE_EXTS = setOf("js", "json", "kt", "java", "py", "sh", "xml", "html", "css", "c", "cpp", "h", "md", "ts", "yml", "yaml")
    private val APK_EXTS = setOf("apk", "xapk", "apks")

    private val PRUNED_PATHS = setOf("/proc", "/sys", "/dev", "/acct", "/config", "/d", "/etc", "/system/bin")

    /**
     * Search filesystem progressively, emitting matches in real-time batches via Flow.
     */
    fun search(query: SearchQuery): Flow<List<FileNode>> = flow {
        if (query.keyword.isBlank()) {
            emit(emptyList())
            return@flow
        }

        val rootDir = File(query.rootPath)
        if (!rootDir.exists()) {
            emit(emptyList())
            return@flow
        }

        val searchTerms = query.keyword.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val processedTerms = if (query.matchCase) searchTerms else searchTerms.map { it.lowercase(Locale.ROOT) }

        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(Pair(rootDir, 0))

        val currentBatch = mutableListOf<FileNode>()
        var lastEmitTime = System.currentTimeMillis()

        while (queue.isNotEmpty()) {
            val (currentDir, depth) = queue.removeFirst()

            if (depth > query.maxDepth) continue
            if (PRUNED_PATHS.contains(currentDir.absolutePath)) continue

            val children = try {
                currentDir.listFiles()
            } catch (e: SecurityException) {
                null
            } catch (e: Exception) {
                null
            } ?: continue

            for (file in children) {
                val isHidden = file.name.startsWith(".")
                if (isHidden && !query.includeHidden) {
                    continue
                }

                val fileNameForMatch = if (query.matchCase) file.name else file.name.lowercase(Locale.ROOT)
                val matchesTerm = processedTerms.all { term -> fileNameForMatch.contains(term) }

                val isDir = file.isDirectory
                val matchesCategory = if (isDir) {
                    query.category == FileCategoryFilter.ALL
                } else {
                    matchesCategory(file.extension.lowercase(Locale.ROOT), query.category)
                }

                if (matchesTerm && matchesCategory) {
                    currentBatch.add(
                        FileNode.LocalFile(
                            name = file.name,
                            path = file.absolutePath,
                            size = if (isDir) 0L else file.length(),
                            lastModified = file.lastModified(),
                            isDirectory = isDir
                        )
                    )
                }

                if (isDir && depth + 1 <= query.maxDepth) {
                    queue.add(Pair(file, depth + 1))
                }

                // Progressive batch emission: every 20 items or 50ms for buttery-smooth responsiveness
                val now = System.currentTimeMillis()
                if (currentBatch.size >= 20 || (currentBatch.isNotEmpty() && now - lastEmitTime >= 50)) {
                    emit(currentBatch.toList())
                    currentBatch.clear()
                    lastEmitTime = now
                }
            }
        }

        // Emit any remaining matches
        if (currentBatch.isNotEmpty()) {
            emit(currentBatch.toList())
            currentBatch.clear()
        }
    }.flowOn(Dispatchers.IO)

    private fun matchesCategory(ext: String, category: FileCategoryFilter): Boolean {
        return when (category) {
            FileCategoryFilter.ALL -> true
            FileCategoryFilter.IMAGES -> IMAGE_EXTS.contains(ext)
            FileCategoryFilter.VIDEOS -> VIDEO_EXTS.contains(ext)
            FileCategoryFilter.AUDIO -> AUDIO_EXTS.contains(ext)
            FileCategoryFilter.DOCUMENTS -> DOC_EXTS.contains(ext)
            FileCategoryFilter.ARCHIVES -> ARCHIVE_EXTS.contains(ext)
            FileCategoryFilter.CODE -> CODE_EXTS.contains(ext)
            FileCategoryFilter.APKS -> APK_EXTS.contains(ext)
        }
    }
}

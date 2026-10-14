package com.polymath.fs

import com.polymath.fs.data.repository.Progress
import com.polymath.fs.data.repository.VfsRepository
import com.polymath.fs.domain.vfs.TransferEngine
import com.polymath.fs.domain.vfs.VfsCapabilities
import com.polymath.fs.domain.vfs.VfsProvider
import com.polymath.fs.domain.vfs.VfsRegistry
import com.polymath.fs.domain.vfs.VfsStat
import com.polymath.fs.domain.vfs.VfsUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class TransferEngineUnitTest {

    // ---------------------------------------------------------------------------------------------
    // 1. Test Bounded Buffer Copying Across Mock Streams
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testBoundedBufferChunking() = runBlocking {
        val registry = VfsRegistry(emptyList())
        val engine = TransferEngine(registry)

        // 300 KiB data stream (larger than 128 KiB chunk buffer)
        val totalSize = 300 * 1024
        val testBytes = ByteArray(totalSize) { (it % 127).toByte() }

        val bufferSizesObserved = mutableListOf<Int>()
        val chunkSizesRead = mutableListOf<Int>()

        val trackingInput = object : InputStream() {
            private val delegate = ByteArrayInputStream(testBytes)

            override fun read(): Int = delegate.read()

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                bufferSizesObserved.add(b.size)
                val read = delegate.read(b, off, len)
                if (read > 0) {
                    chunkSizesRead.add(read)
                }
                return read
            }
        }

        val output = ByteArrayOutputStream()
        val copiedBytes = engine.copyStream(
            input = trackingInput,
            output = output,
            totalBytes = totalSize.toLong(),
            fileName = "chunked_test.bin"
        )

        assertEquals(totalSize.toLong(), copiedBytes)
        assertArrayEquals(testBytes, output.toByteArray())

        // Verify buffer allocation was bounded strictly to 128 KiB (131072 bytes)
        assertTrue("Buffer sizes should not be empty", bufferSizesObserved.isNotEmpty())
        for (size in bufferSizesObserved) {
            assertEquals(TransferEngine.BUFFER_SIZE, size)
            assertEquals(128 * 1024, size)
        }

        // Chunks read: 128 KiB (131072), 128 KiB (131072), 44 KiB (45056)
        assertEquals(3, chunkSizesRead.size)
        assertEquals(131072, chunkSizesRead[0])
        assertEquals(131072, chunkSizesRead[1])
        assertEquals(45056, chunkSizesRead[2])
    }

    // ---------------------------------------------------------------------------------------------
    // 2. Test Coroutine Cancellation Behavior (ensureActive)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testCancellationSafetyWithEnsureActive() = runBlocking {
        val registry = VfsRegistry(emptyList())
        val engine = TransferEngine(registry)

        val totalSize = 500 * 1024
        val testBytes = ByteArray(totalSize) { 0x42 }

        val firstChunkRead = AtomicBoolean(false)
        val readAttemptsAfterCancel = AtomicInteger(0)
        var transferJob: Job? = null

        val cancellableInput = object : InputStream() {
            private val delegate = ByteArrayInputStream(testBytes)

            override fun read(): Int = delegate.read()

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val read = delegate.read(b, off, len)
                if (firstChunkRead.compareAndSet(false, true)) {
                    // Cancel the transfer job immediately after the first buffer read
                    transferJob?.cancel()
                } else {
                    readAttemptsAfterCancel.incrementAndGet()
                }
                return read
            }
        }

        val output = ByteArrayOutputStream()

        transferJob = launch(Dispatchers.Default) {
            engine.copyStream(
                input = cancellableInput,
                output = output,
                totalBytes = totalSize.toLong(),
                fileName = "cancel_test.bin"
            )
        }

        try {
            transferJob.join()
        } catch (_: Exception) {}

        assertTrue("Transfer job should be cancelled", transferJob.isCancelled)
        // With ensureActive() inside the loop, the transfer loop aborts immediately
        // without copying the entire 500 KiB
        assertTrue(
            "Output size should be less than total size due to immediate cancellation",
            output.size() < totalSize
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 3. Test Progress Throttling (emit at most once every 200-250ms + initial 0% and final 100%)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testProgressThrottlingReporting() = runBlocking {
        var simulatedTime = 1000L
        val throttleInterval = 200L

        val registry = VfsRegistry(emptyList())
        val engine = TransferEngine(
            registry = registry,
            bufferSize = 100, // Small buffer to produce multiple chunks easily
            throttleMs = throttleInterval,
            timeProvider = { simulatedTime }
        )

        // 600 bytes = 6 chunks of 100 bytes
        val totalBytes = 600
        val testBytes = ByteArray(totalBytes) { 1 }

        val emittedProgress = mutableListOf<Progress>()

        val throttledInput = object : InputStream() {
            private val delegate = ByteArrayInputStream(testBytes)
            private var chunkIndex = 0

            override fun read(): Int = delegate.read()

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val read = delegate.read(b, off, len)
                if (read > 0) {
                    chunkIndex++
                    // Advance simulated time per chunk:
                    // chunk 1: +50ms  (total 50ms from last emit -> NO emit)
                    // chunk 2: +100ms (total 150ms from last emit -> NO emit)
                    // chunk 3: +100ms (total 250ms from last emit -> EMIT at 300 bytes)
                    // chunk 4: +50ms  (total 50ms from last emit -> NO emit)
                    // chunk 5: +210ms (total 260ms from last emit -> EMIT at 500 bytes)
                    // chunk 6: +50ms  (total 50ms -> EOF reached -> FINAL 100% EMIT)
                    when (chunkIndex) {
                        1 -> simulatedTime += 50L
                        2 -> simulatedTime += 100L
                        3 -> simulatedTime += 100L
                        4 -> simulatedTime += 50L
                        5 -> simulatedTime += 210L
                        6 -> simulatedTime += 50L
                    }
                }
                return read
            }
        }

        val output = ByteArrayOutputStream()
        engine.copyStream(
            input = throttledInput,
            output = output,
            totalBytes = totalBytes.toLong(),
            fileName = "throttled.bin",
            onProgress = { emittedProgress.add(it) }
        )

        // Verify emissions:
        // 1. Initial 0% emission
        // 2. Chunk 3 emission (250ms elapsed)
        // 3. Chunk 5 emission (260ms elapsed)
        // 4. Final 100% emission
        assertEquals(4, emittedProgress.size)

        // Initial progress: 0%
        val initial = emittedProgress[0]
        assertEquals(0L, initial.currentBytes)
        assertEquals(600L, initial.totalBytes)
        assertFalse(initial.isComplete)

        // Intermediate throttled progress 1: chunk 3 (300 bytes)
        val intermediate1 = emittedProgress[1]
        assertEquals(300L, intermediate1.currentBytes)
        assertEquals(600L, intermediate1.totalBytes)
        assertFalse(intermediate1.isComplete)

        // Intermediate throttled progress 2: chunk 5 (500 bytes)
        val intermediate2 = emittedProgress[2]
        assertEquals(500L, intermediate2.currentBytes)
        assertEquals(600L, intermediate2.totalBytes)
        assertFalse(intermediate2.isComplete)

        // Final progress: 100%
        val finalProg = emittedProgress[3]
        assertEquals(600L, finalProg.currentBytes)
        assertEquals(600L, finalProg.totalBytes)
        assertTrue(finalProg.isComplete)
        assertEquals(100, finalProg.percentage)
    }

    // ---------------------------------------------------------------------------------------------
    // 4. Test Cross-Scheme Recursive Directory Tree Copy
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testCrossSchemeRecursiveDirectoryCopy() = runBlocking {
        val srcProvider = InMemoryVfsProvider(setOf("sftp"))
        val destProvider = InMemoryVfsProvider(setOf("content"))

        val registry = VfsRegistry(listOf(srcProvider, destProvider))
        val engine = TransferEngine(registry)

        // Populate SFTP source directory tree:
        // /home/user/project/
        //   readme.md (12 bytes)
        //   src/
        //     main.kt (25 bytes)
        srcProvider.mkdir(VfsUri.parse("sftp://my-server/home/user/project"))
        srcProvider.writeFile("sftp://my-server/home/user/project/readme.md", "SFTP Readme!".toByteArray())
        srcProvider.mkdir(VfsUri.parse("sftp://my-server/home/user/project/src"))
        srcProvider.writeFile("sftp://my-server/home/user/project/src/main.kt", "fun main() = println(42)".toByteArray())

        val destDir = "content://docs/tree/primary"
        val progressEvents = engine.copy(
            src = listOf("sftp://my-server/home/user/project"),
            dest = destDir
        ).toList()

        assertTrue("Progress should not be empty", progressEvents.isNotEmpty())
        assertTrue("Final progress should be complete", progressEvents.last().isComplete)

        // Verify destination tree was recursively created and populated in content provider
        assertTrue(destProvider.hasDirectory("/tree/primary/project"))
        assertTrue(destProvider.hasDirectory("/tree/primary/project/src"))
        assertTrue(destProvider.hasFile("/tree/primary/project/readme.md"))
        assertTrue(destProvider.hasFile("/tree/primary/project/src/main.kt"))

        assertEquals("SFTP Readme!", String(destProvider.getFile("/tree/primary/project/readme.md")))
        assertEquals("fun main() = println(42)", String(destProvider.getFile("/tree/primary/project/src/main.kt")))
    }

    // ---------------------------------------------------------------------------------------------
    // 5. Test Cross-Scheme Recursive Directory Tree Move
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testCrossSchemeRecursiveDirectoryMove() = runBlocking {
        val srcProvider = InMemoryVfsProvider(setOf("content"))
        val destProvider = InMemoryVfsProvider(setOf("sftp"))

        val registry = VfsRegistry(listOf(srcProvider, destProvider))
        val engine = TransferEngine(registry)

        // Populate content provider source directory tree
        srcProvider.mkdir(VfsUri.parse("content://docs/tree/source_folder"))
        srcProvider.writeFile("content://docs/tree/source_folder/note.txt", "Move me!".toByteArray())
        srcProvider.mkdir(VfsUri.parse("content://docs/tree/source_folder/assets"))
        srcProvider.writeFile("content://docs/tree/source_folder/assets/logo.png", byteArrayOf(1, 2, 3, 4))

        val destDir = "sftp://backup-server/var/backups"
        val progressEvents = engine.move(
            src = listOf("content://docs/tree/source_folder"),
            dest = destDir
        ).toList()

        assertTrue(progressEvents.last().isComplete)

        // Destination in sftp provider has the copied files
        assertTrue(destProvider.hasDirectory("/var/backups/source_folder"))
        assertTrue(destProvider.hasDirectory("/var/backups/source_folder/assets"))
        assertEquals("Move me!", String(destProvider.getFile("/var/backups/source_folder/note.txt")))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), destProvider.getFile("/var/backups/source_folder/assets/logo.png"))

        // Source in content provider must be deleted
        assertFalse("Source dir should be deleted after move", srcProvider.hasDirectory("/tree/source_folder"))
        assertFalse("Source file should be deleted after move", srcProvider.hasFile("/tree/source_folder/note.txt"))
    }

    // ---------------------------------------------------------------------------------------------
    // 6. Test VfsRepository Delegation
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testVfsRepositoryDelegatesToTransferEngine() = runBlocking {
        val alphaProvider = InMemoryVfsProvider(setOf("alpha"))
        val betaProvider = InMemoryVfsProvider(setOf("beta"))

        val registry = VfsRegistry(listOf(alphaProvider, betaProvider))
        val engine = TransferEngine(registry)
        val repo = VfsRepository(registry = registry, context = null, transferEngine = engine)

        alphaProvider.mkdir(VfsUri.parse("alpha://host/data"))
        alphaProvider.writeFile("alpha://host/data/report.csv", "id,name\n1,Alpha".toByteArray())

        val copyProgress = repo.copy(
            src = listOf("alpha://host/data/report.csv"),
            dest = "beta://backup/archive"
        ).toList()

        assertTrue(copyProgress.last().isComplete)
        assertTrue(betaProvider.hasFile("/archive/report.csv"))
        assertEquals("id,name\n1,Alpha", String(betaProvider.getFile("/archive/report.csv")))
    }

    // ---------------------------------------------------------------------------------------------
    // In-Memory Test VfsProvider
    // ---------------------------------------------------------------------------------------------

    private class InMemoryVfsProvider(
        override val schemes: Set<String>
    ) : VfsProvider {
        private val files = mutableMapOf<String, ByteArray>()
        private val dirs = mutableSetOf<String>()

        fun writeFile(rawUri: String, content: ByteArray) {
            val uri = VfsUri.parse(rawUri)
            dirs.add(uri.path.substringBeforeLast('/', ""))
            files[uri.path] = content
        }

        fun hasFile(path: String): Boolean = files.containsKey(path)
        fun hasDirectory(path: String): Boolean = dirs.contains(path)
        fun getFile(path: String): ByteArray = files[path] ?: throw FileNotFoundException(path)

        override fun capabilities(uri: VfsUri): VfsCapabilities = VfsCapabilities(
            canRead = true,
            canWrite = true,
            canRename = true,
            canDelete = true,
            canMkdir = true,
            canChmod = false,
            canChown = false,
            canSymlink = false,
            canStream = true,
            canSeek = false
        )

        override suspend fun list(uri: VfsUri): List<VfsStat> {
            val parentPath = uri.path.trimEnd('/')
            val result = mutableListOf<VfsStat>()

            for (dir in dirs) {
                if (dir != parentPath && dir.startsWith(parentPath)) {
                    val remainder = dir.removePrefix(parentPath).trimStart('/')
                    if (remainder.isNotEmpty() && !remainder.contains('/')) {
                        result.add(
                            VfsStat(
                                name = remainder,
                                uri = "${schemes.first()}://${uri.authority ?: ""}$dir",
                                isDirectory = true,
                                isSymlink = false,
                                size = 0L,
                                lastModified = 0L,
                                permissions = null,
                                mimeType = null,
                                capabilities = capabilities(uri)
                            )
                        )
                    }
                }
            }

            for ((filePath, content) in files) {
                if (filePath.startsWith(parentPath)) {
                    val remainder = filePath.removePrefix(parentPath).trimStart('/')
                    if (remainder.isNotEmpty() && !remainder.contains('/')) {
                        result.add(
                            VfsStat(
                                name = remainder,
                                uri = "${schemes.first()}://${uri.authority ?: ""}$filePath",
                                isDirectory = false,
                                isSymlink = false,
                                size = content.size.toLong(),
                                lastModified = 0L,
                                permissions = null,
                                mimeType = null,
                                capabilities = capabilities(uri)
                            )
                        )
                    }
                }
            }

            return result
        }

        override suspend fun stat(uri: VfsUri): VfsStat? {
            val path = uri.path
            if (dirs.contains(path)) {
                return VfsStat(
                    name = path.substringAfterLast('/'),
                    uri = uri.serialized(),
                    isDirectory = true,
                    isSymlink = false,
                    size = 0L,
                    lastModified = 0L,
                    permissions = null,
                    mimeType = null,
                    capabilities = capabilities(uri)
                )
            }
            val content = files[path] ?: return null
            return VfsStat(
                name = path.substringAfterLast('/'),
                uri = uri.serialized(),
                isDirectory = false,
                isSymlink = false,
                size = content.size.toLong(),
                lastModified = 0L,
                permissions = null,
                mimeType = null,
                capabilities = capabilities(uri)
            )
        }

        override fun openRead(uri: VfsUri): InputStream {
            val content = files[uri.path] ?: throw FileNotFoundException("File not found: ${uri.path}")
            return ByteArrayInputStream(content)
        }

        override fun openWrite(uri: VfsUri, append: Boolean): OutputStream {
            return object : ByteArrayOutputStream() {
                override fun close() {
                    super.close()
                    val bytes = toByteArray()
                    if (append && files.containsKey(uri.path)) {
                        files[uri.path] = files[uri.path]!! + bytes
                    } else {
                        files[uri.path] = bytes
                    }
                }
            }
        }

        override suspend fun delete(uri: VfsUri, recursive: Boolean): Boolean {
            val path = uri.path
            files.remove(path)
            dirs.remove(path)
            if (recursive) {
                val prefix = if (path.endsWith("/")) path else "$path/"
                files.keys.filter { it.startsWith(prefix) }.forEach { files.remove(it) }
                dirs.filter { it.startsWith(prefix) }.forEach { dirs.remove(it) }
            }
            return true
        }

        override suspend fun mkdir(uri: VfsUri): Boolean {
            dirs.add(uri.path)
            return true
        }

        override suspend fun rename(uri: VfsUri, newName: String): Boolean {
            val oldPath = uri.path
            val parent = oldPath.substringBeforeLast('/', "")
            val newPath = if (parent.isEmpty()) "/$newName" else "$parent/$newName"
            if (files.containsKey(oldPath)) {
                files[newPath] = files.remove(oldPath)!!
                return true
            }
            if (dirs.contains(oldPath)) {
                dirs.remove(oldPath)
                dirs.add(newPath)
                return true
            }
            return false
        }
    }
}

package com.polymath.fs

import com.polymath.fs.core.RootShellHolder
import com.polymath.fs.domain.vfs.VfsCapabilities
import com.polymath.fs.domain.vfs.VfsProvider
import com.polymath.fs.domain.vfs.VfsRegistry
import com.polymath.fs.domain.vfs.VfsStat
import com.polymath.fs.domain.vfs.VfsUri
import com.polymath.fs.domain.vfs.provider.LocalVfsProvider
import com.polymath.fs.domain.vfs.provider.SafVfsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class VfsCoreUnitTest {

    @Test
    fun testVfsUriParsingLocalPaths() {
        val uri1 = VfsUri.parse("/storage/emulated/0/Download")
        assertEquals("file", uri1.scheme)
        assertNull(uri1.authority)
        assertEquals("/storage/emulated/0/Download", uri1.path)
        assertEquals("/storage/emulated/0/Download", uri1.serialized())

        val uri2 = VfsUri.parse("file:///data/local/tmp")
        assertEquals("file", uri2.scheme)
        assertNull(uri2.authority)
        assertEquals("/data/local/tmp", uri2.path)
        assertEquals("/data/local/tmp", uri2.serialized())
    }

    @Test
    fun testVfsUriParsingRemoteAndContentSchemes() {
        val contentUri = VfsUri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload")
        assertEquals("content", contentUri.scheme)
        assertEquals("com.android.externalstorage.documents", contentUri.authority)
        assertEquals("/tree/primary%3ADownload", contentUri.path)
        assertEquals("content://com.android.externalstorage.documents/tree/primary%3ADownload", contentUri.serialized())

        val sftpUri = VfsUri.parse("sftp://my-server/home/user/project")
        assertEquals("sftp", sftpUri.scheme)
        assertEquals("my-server", sftpUri.authority)
        assertEquals("/home/user/project", sftpUri.path)
        assertEquals("sftp://my-server/home/user/project", sftpUri.serialized())

        val ftpUri = VfsUri.parse("ftp://192.168.1.50:21/pub/files")
        assertEquals("ftp", ftpUri.scheme)
        assertEquals("192.168.1.50:21", ftpUri.authority)
        assertEquals("/pub/files", ftpUri.path)
        assertEquals("ftp://192.168.1.50:21/pub/files", ftpUri.serialized())

        val smbUri = VfsUri.parse("smb://nas/share/movies")
        assertEquals("smb", smbUri.scheme)
        assertEquals("nas", smbUri.authority)
        assertEquals("/share/movies", smbUri.path)
        assertEquals("smb://nas/share/movies", smbUri.serialized())

        val synapseUri = VfsUri.parse("synapse://10.0.0.1:8888/nodes")
        assertEquals("synapse", synapseUri.scheme)
        assertEquals("10.0.0.1:8888", synapseUri.authority)
        assertEquals("/nodes", synapseUri.path)
        assertEquals("synapse://10.0.0.1:8888/nodes", synapseUri.serialized())
    }

    @Test
    fun testVfsRegistryRouting() {
        val localProvider = object : VfsProvider {
            override val schemes: Set<String> = setOf("file")
            override fun capabilities(uri: VfsUri): VfsCapabilities = VfsCapabilities.FULL_LOCAL
            override suspend fun list(uri: VfsUri): List<VfsStat> = emptyList()
            override suspend fun stat(uri: VfsUri): VfsStat? = null
            override fun openRead(uri: VfsUri) = throw UnsupportedOperationException()
            override fun openWrite(uri: VfsUri, append: Boolean) = throw UnsupportedOperationException()
            override suspend fun delete(uri: VfsUri, recursive: Boolean): Boolean = true
            override suspend fun mkdir(uri: VfsUri): Boolean = true
            override suspend fun rename(uri: VfsUri, newName: String): Boolean = true
        }

        val sftpProvider = object : VfsProvider {
            override val schemes: Set<String> = setOf("sftp")
            override fun capabilities(uri: VfsUri): VfsCapabilities = VfsCapabilities.READ_ONLY
            override suspend fun list(uri: VfsUri): List<VfsStat> = emptyList()
            override suspend fun stat(uri: VfsUri): VfsStat? = null
            override fun openRead(uri: VfsUri) = throw UnsupportedOperationException()
            override fun openWrite(uri: VfsUri, append: Boolean) = throw UnsupportedOperationException()
            override suspend fun delete(uri: VfsUri, recursive: Boolean): Boolean = true
            override suspend fun mkdir(uri: VfsUri): Boolean = true
            override suspend fun rename(uri: VfsUri, newName: String): Boolean = true
        }

        val registry = VfsRegistry(listOf(localProvider, sftpProvider))

        val resolvedLocal = registry.providerFor("/storage/emulated/0")
        assertEquals(localProvider, resolvedLocal)

        val resolvedSftp = registry.providerFor("sftp://my-host/etc")
        assertEquals(sftpProvider, resolvedSftp)

        try {
            registry.providerFor("smb://nas/share")
            fail("Expected IllegalArgumentException for unregistered smb scheme")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("smb") == true)
        }
    }

    @Test
    fun testVfsCapabilities() {
        val caps = VfsCapabilities(
            canRead = true,
            canWrite = true,
            canRename = true,
            canDelete = true,
            canMkdir = true,
            canChmod = false,
            canChown = false,
            canSymlink = true,
            canStream = true,
            canSeek = true
        )

        assertTrue(caps.canRead)
        assertTrue(caps.canWrite)
        assertFalse(caps.canChmod)
        assertFalse(caps.canChown)
    }

    @Test
    fun testSafVfsProviderRegistrationInRegistry() {
        val dummyContext = android.content.ContextWrapper(null)
        val safProvider = SafVfsProvider(dummyContext)
        assertEquals(setOf("content"), safProvider.schemes)

        val localProvider = object : VfsProvider {
            override val schemes: Set<String> = setOf("file")
            override fun capabilities(uri: VfsUri): VfsCapabilities = VfsCapabilities.FULL_LOCAL
            override suspend fun list(uri: VfsUri): List<VfsStat> = emptyList()
            override suspend fun stat(uri: VfsUri): VfsStat? = null
            override fun openRead(uri: VfsUri) = throw UnsupportedOperationException()
            override fun openWrite(uri: VfsUri, append: Boolean) = throw UnsupportedOperationException()
            override suspend fun delete(uri: VfsUri, recursive: Boolean): Boolean = true
            override suspend fun mkdir(uri: VfsUri): Boolean = true
            override suspend fun rename(uri: VfsUri, newName: String): Boolean = true
        }

        val registry = VfsRegistry(listOf(localProvider, safProvider))

        val treeUriStr = "content://com.android.externalstorage.documents/tree/primary%3ADocuments"
        val resolved = registry.providerFor(treeUriStr)
        assertEquals(safProvider, resolved)

        val parsedUri = VfsUri.parse(treeUriStr)
        val resolvedFromUri = registry.providerFor(parsedUri)
        assertEquals(safProvider, resolvedFromUri)
    }

    @Test
    fun testSafVfsProviderCapabilities() {
        val dummyContext = android.content.ContextWrapper(null)
        val safProvider = SafVfsProvider(dummyContext)
        val uri = VfsUri.parse("content://com.android.externalstorage.documents/document/123")
        val caps = safProvider.capabilities(uri)

        assertTrue(caps.canRead)
        assertTrue(caps.canWrite)
        assertTrue(caps.canRename)
        assertTrue(caps.canDelete)
        assertTrue(caps.canMkdir)
        assertFalse(caps.canChmod)
        assertFalse(caps.canChown)
        assertFalse(caps.canSymlink)
        assertTrue(caps.canStream)
        assertFalse(caps.canSeek)
    }

    @Test
    fun testContentUriHandling() {
        val treeUri = VfsUri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload")
        assertEquals("content", treeUri.scheme)
        assertEquals("com.android.externalstorage.documents", treeUri.authority)
        assertEquals("/tree/primary%3ADownload", treeUri.path)
        assertEquals("content://com.android.externalstorage.documents/tree/primary%3ADownload", treeUri.serialized())

        val childUri = VfsUri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload/document/primary%3ADownload%2Ftest.txt")
        assertEquals("content", childUri.scheme)
        assertEquals("com.android.externalstorage.documents", childUri.authority)
        assertEquals("/tree/primary%3ADownload/document/primary%3ADownload%2Ftest.txt", childUri.path)
        assertEquals("content://com.android.externalstorage.documents/tree/primary%3ADownload/document/primary%3ADownload%2Ftest.txt", childUri.serialized())

        val simpleContentUri = VfsUri.parse("content://media/external/audio/12345")
        assertEquals("content", simpleContentUri.scheme)
        assertEquals("media", simpleContentUri.authority)
        assertEquals("/external/audio/12345", simpleContentUri.path)
        assertEquals("content://media/external/audio/12345", simpleContentUri.serialized())
    }
}

package com.polymath.fs.domain.vfs.provider

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.polymath.fs.domain.vfs.VfsCapabilities
import com.polymath.fs.domain.vfs.VfsProvider
import com.polymath.fs.domain.vfs.VfsStat
import com.polymath.fs.domain.vfs.VfsUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafVfsProvider @Inject constructor(
    private val context: Context
) : VfsProvider {

    override val schemes: Set<String> = setOf("content")

    override fun capabilities(uri: VfsUri): VfsCapabilities {
        return VfsCapabilities(
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
    }

    override suspend fun list(uri: VfsUri): List<VfsStat> = withContext(Dispatchers.IO) {
        val rawUri = Uri.parse(uri.serialized())
        val childrenUri = when {
            rawUri.path?.endsWith("/children") == true -> rawUri
            DocumentsContract.isTreeUri(rawUri) -> {
                val docId = try {
                    DocumentsContract.getDocumentId(rawUri)
                } catch (_: Exception) {
                    DocumentsContract.getTreeDocumentId(rawUri)
                }
                DocumentsContract.buildChildDocumentsUriUsingTree(rawUri, docId)
            }
            else -> rawUri
        }

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS
        )

        val results = mutableListOf<VfsStat>()
        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = if (idIndex != -1) cursor.getString(idIndex) else null
                    if (docId == null) continue

                    val displayName = if (nameIndex != -1) cursor.getString(nameIndex) ?: docId else docId
                    val mimeType = if (mimeIndex != -1) cursor.getString(mimeIndex) else null
                    val size = if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                    val lastModified = if (modifiedIndex != -1 && !cursor.isNull(modifiedIndex)) cursor.getLong(modifiedIndex) else 0L
                    val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR

                    val childUri = if (DocumentsContract.isTreeUri(rawUri)) {
                        DocumentsContract.buildDocumentUriUsingTree(rawUri, docId)
                    } else {
                        DocumentsContract.buildDocumentUri(rawUri.authority ?: "", docId)
                    }
                    val childUriStr = childUri.toString()
                    val childVfsUri = VfsUri.parse(childUriStr)

                    results.add(
                        VfsStat(
                            name = displayName,
                            uri = childUriStr,
                            isDirectory = isDir,
                            isSymlink = false,
                            size = if (isDir) 0L else size,
                            lastModified = lastModified,
                            permissions = null,
                            mimeType = mimeType,
                            capabilities = capabilities(childVfsUri)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        results.sortedWith(
            compareByDescending<VfsStat> { it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
    }

    override suspend fun stat(uri: VfsUri): VfsStat? = withContext(Dispatchers.IO) {
        val rawUri = Uri.parse(uri.serialized())
        val docUri = toDocumentUri(rawUri)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS
        )

        try {
            context.contentResolver.query(docUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    val docId = if (idIndex != -1) cursor.getString(idIndex) else null
                    val fallbackName = docId ?: uri.path.substringAfterLast('/').ifEmpty { uri.path }
                    val displayName = if (nameIndex != -1) {
                        cursor.getString(nameIndex) ?: fallbackName
                    } else {
                        fallbackName
                    }
                    val mimeType = if (mimeIndex != -1) cursor.getString(mimeIndex) else null
                    val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                    val size = if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                    val lastModified = if (modifiedIndex != -1 && !cursor.isNull(modifiedIndex)) cursor.getLong(modifiedIndex) else 0L

                    return@withContext VfsStat(
                        name = displayName,
                        uri = uri.serialized(),
                        isDirectory = isDir,
                        isSymlink = false,
                        size = if (isDir) 0L else size,
                        lastModified = lastModified,
                        permissions = null,
                        mimeType = mimeType,
                        capabilities = capabilities(uri)
                    )
                }
            }
        } catch (_: Exception) {
            return@withContext null
        }

        null
    }

    override fun openRead(uri: VfsUri): InputStream {
        val targetUri = toDocumentUri(uri)
        return context.contentResolver.openInputStream(targetUri)
            ?: throw FileNotFoundException("Could not open input stream for: ${uri.serialized()}")
    }

    override fun openWrite(uri: VfsUri, append: Boolean): OutputStream {
        val rawUri = Uri.parse(uri.serialized())
        val targetUri = toDocumentUri(rawUri)
        val mode = if (append) "wa" else "w"

        try {
            val stream = context.contentResolver.openOutputStream(targetUri, mode)
            if (stream != null) return stream
        } catch (_: Exception) {
            // Target document may not exist yet, attempt creation below
        }

        val queryName = rawUri.getQueryParameter("displayName")
        val displayName = queryName
            ?: rawUri.lastPathSegment?.substringAfterLast('/')
            ?: "new_file"

        val parentUri = if (queryName != null) {
            toDocumentUri(rawUri.buildUpon().clearQuery().build())
        } else {
            val rawUriStr = rawUri.toString()
            val parentUriStr = if (displayName.isNotEmpty() && rawUriStr.endsWith("/$displayName")) {
                rawUriStr.substring(0, rawUriStr.length - displayName.length - 1)
            } else {
                rawUriStr.substringBeforeLast('/')
            }
            toDocumentUri(Uri.parse(parentUriStr))
        }

        val extension = displayName.substringAfterLast('.', "")
        val mimeType = if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "application/octet-stream"
        } else {
            "application/octet-stream"
        }

        val createdUri = DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            mimeType,
            displayName
        ) ?: throw IOException("Failed to create document: ${uri.serialized()}")

        return context.contentResolver.openOutputStream(createdUri, mode)
            ?: throw IOException("Failed to open output stream for created document: $createdUri")
    }

    override suspend fun delete(uri: VfsUri, recursive: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetUri = toDocumentUri(uri)
            DocumentsContract.deleteDocument(context.contentResolver, targetUri)
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun mkdir(uri: VfsUri): Boolean = withContext(Dispatchers.IO) {
        try {
            val rawUri = Uri.parse(uri.serialized())
            val queryName = rawUri.getQueryParameter("displayName")
            val displayName = queryName
                ?: rawUri.lastPathSegment?.substringAfterLast('/')
                ?: return@withContext false

            if (displayName.isEmpty()) return@withContext false

            val parentUri = if (queryName != null) {
                toDocumentUri(rawUri.buildUpon().clearQuery().build())
            } else {
                val rawUriStr = rawUri.toString()
                val parentUriStr = if (rawUriStr.endsWith("/$displayName")) {
                    rawUriStr.substring(0, rawUriStr.length - displayName.length - 1)
                } else {
                    rawUriStr.substringBeforeLast('/')
                }
                toDocumentUri(Uri.parse(parentUriStr))
            }

            val createdUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                displayName
            )
            createdUri != null
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun rename(uri: VfsUri, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetUri = toDocumentUri(uri)
            val resultUri = DocumentsContract.renameDocument(context.contentResolver, targetUri, newName)
            resultUri != null
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun chmod(uri: VfsUri, mode: String): Boolean = false

    override suspend fun chown(uri: VfsUri, owner: String): Boolean = false

    override fun close() {}

    private fun toDocumentUri(uri: Uri): Uri {
        if (!DocumentsContract.isTreeUri(uri)) {
            return uri
        }
        val path = uri.path ?: return uri
        if (path.contains("/document/")) {
            return uri
        }
        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(uri)
            DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId)
        } catch (_: Exception) {
            uri
        }
    }

    private fun toDocumentUri(vfsUri: VfsUri): Uri {
        return toDocumentUri(Uri.parse(vfsUri.serialized()))
    }
}

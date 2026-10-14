package com.polymath.fs.data.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.polymath.fs.data.db.AppDatabase
import com.polymath.fs.data.db.dao.SafGrantDao
import com.polymath.fs.data.db.entities.SafGrantEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafPermissionManager @Inject constructor(
    private val context: Context,
    private val safGrantDao: SafGrantDao
) {

    constructor(context: Context) : this(
        context,
        AppDatabase.getDatabase(context).safGrantDao()
    )

    fun getAllGrants(): Flow<List<SafGrantEntity>> = safGrantDao.getAllGrants()

    fun getAllGrantsSync(): List<SafGrantEntity> = safGrantDao.getAllGrantsSync()

    suspend fun takePersistablePermission(
        treeUri: Uri,
        customLabel: String? = null,
        writable: Boolean = true
    ): SafGrantEntity = withContext(Dispatchers.IO) {
        var isWritable = writable
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            if (writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0

        try {
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
        } catch (e: SecurityException) {
            if (writable) {
                // Fall back to read-only permission if write grant is rejected
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                isWritable = false
            } else {
                throw e
            }
        }

        val authority = treeUri.authority ?: ""
        val treeDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            ""
        }

        val label = when {
            !customLabel.isNullOrBlank() -> customLabel
            else -> {
                val docName = try {
                    DocumentFile.fromTreeUri(context, treeUri)?.name
                } catch (_: Exception) {
                    null
                }
                docName?.ifBlank { null }
                    ?: treeDocId.substringAfterLast(':').substringAfterLast('/').ifBlank { null }
                    ?: authority.ifBlank { "Storage" }
            }
        }

        val grantEntity = SafGrantEntity(
            treeUri = treeUri.toString(),
            authority = authority,
            treeDocId = treeDocId,
            label = label,
            writable = isWritable,
            addedAt = System.currentTimeMillis()
        )

        safGrantDao.insertGrant(grantEntity)
        grantEntity
    }

    suspend fun onTreeSelected(
        intent: Intent?,
        customLabel: String? = null
    ): SafGrantEntity? = withContext(Dispatchers.IO) {
        val treeUri = intent?.data ?: return@withContext null
        val flags = intent.flags
        val writable = (flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0
        takePersistablePermission(treeUri, customLabel, writable = writable)
    }

    suspend fun releasePermission(treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val releaseFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.releasePersistableUriPermission(treeUri, releaseFlags)
            } catch (_: Exception) {
                try {
                    context.contentResolver.releasePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }
            safGrantDao.deleteGrant(treeUri.toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun releasePermission(treeUriString: String): Boolean {
        return releasePermission(Uri.parse(treeUriString))
    }
}

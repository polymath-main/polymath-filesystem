package com.polymath.fs.core

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import javax.inject.Inject
import javax.inject.Singleton

data class StorageVolumeInfo(
    val name: String,
    val path: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val state: String
)

@Singleton
class VolumeManager @Inject constructor(
    private val context: Context
) {

    fun getVolumes(): List<StorageVolumeInfo> {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val volumes = mutableListOf<StorageVolumeInfo>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val storageVolumes: List<StorageVolume> = storageManager.storageVolumes
            for (volume in storageVolumes) {
                // To get the path reliably across versions, we can use reflection or if it's API 30+ we can use directory
                val path = getVolumePath(volume)
                val state = volume.state
                if (path != null && state == Environment.MEDIA_MOUNTED) {
                    volumes.add(
                        StorageVolumeInfo(
                            name = volume.getDescription(context) ?: "Unknown",
                            path = path,
                            isPrimary = volume.isPrimary,
                            isRemovable = volume.isRemovable,
                            state = state
                        )
                    )
                }
            }
        } else {
            // Fallback for older devices, although Android 10 is API 29. 
            // We can assume at least API 24 (N)
        }

        // Always add root
        volumes.add(
            StorageVolumeInfo(
                name = "Root",
                path = "/",
                isPrimary = false,
                isRemovable = false,
                state = Environment.MEDIA_MOUNTED
            )
        )

        return volumes
    }

    private fun getVolumePath(volume: StorageVolume): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volume.directory?.absolutePath
        } else {
            try {
                val method = volume.javaClass.getMethod("getPath")
                method.invoke(volume) as String
            } catch (e: Exception) {
                null
            }
        }
    }
}

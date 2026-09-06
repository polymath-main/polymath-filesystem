package com.polymath.fs.core

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

data class StoragePartitionInfo(
    val id: String,
    val name: String,
    val mountPoint: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val usedPercent: Int,
    val isReadOnly: Boolean,
    val description: String
)

data class StorageTelemetryReport(
    val primaryTotalBytes: Long,
    val primaryFreeBytes: Long,
    val primaryUsedBytes: Long,
    val primaryUsedPercent: Int,
    val internalTotalBytes: Long,
    val internalFreeBytes: Long,
    val internalUsedBytes: Long,
    val systemTotalBytes: Long,
    val systemFreeBytes: Long,
    val systemIsReadOnly: Boolean,
    val partitions: List<StoragePartitionInfo>
)

object StorageTelemetryManager {

    fun getStorageTelemetry(context: Context): StorageTelemetryReport {
        // 1. PRIMARY SHARED STORAGE (e.g. /storage/emulated/0 or StorageStatsManager)
        var primaryTotal = 0L
        var primaryFree = 0L

        // Attempt Tier 1: StorageStatsManager (API 26+) for hardware-exact storage size
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val statsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
                if (statsManager != null) {
                    val total = statsManager.getTotalBytes(StorageManager.UUID_DEFAULT)
                    val free = statsManager.getFreeBytes(StorageManager.UUID_DEFAULT)
                    if (total > 0L) {
                        primaryTotal = total
                        primaryFree = free
                    }
                }
            } catch (ignored: Exception) {
                // StorageStats query restriction or SecurityException fallback
            }
        }

        // Tier 2: StatFs on External Storage Directory (/storage/emulated/0)
        val externalDir = Environment.getExternalStorageDirectory()
        val externalStat = try {
            if (externalDir.exists()) StatFs(externalDir.absolutePath) else null
        } catch (e: Exception) {
            null
        }

        if (primaryTotal <= 0L && externalStat != null) {
            val blockSize = externalStat.blockSizeLong
            primaryTotal = externalStat.blockCountLong * blockSize
            primaryFree = externalStat.availableBlocksLong * blockSize
        }

        // Tier 3: If still 0 or unavailable, check /data
        val dataDir = Environment.getDataDirectory()
        val dataStat = try {
            if (dataDir.exists()) StatFs(dataDir.absolutePath) else null
        } catch (e: Exception) {
            null
        }

        if (primaryTotal <= 0L && dataStat != null) {
            val blockSize = dataStat.blockSizeLong
            primaryTotal = dataStat.blockCountLong * blockSize
            primaryFree = dataStat.availableBlocksLong * blockSize
        }

        val primaryUsed = (primaryTotal - primaryFree).coerceAtLeast(0L)
        val primaryPercent = if (primaryTotal > 0L) {
            ((primaryUsed * 100L) / primaryTotal).toInt().coerceIn(0, 100)
        } else 0

        // 2. INTERNAL DATA PARTITION (/data)
        var internalTotal = 0L
        var internalFree = 0L
        if (dataStat != null) {
            val blockSize = dataStat.blockSizeLong
            internalTotal = dataStat.blockCountLong * blockSize
            internalFree = dataStat.availableBlocksLong * blockSize
        }
        val internalUsed = (internalTotal - internalFree).coerceAtLeast(0L)

        // 3. SYSTEM PARTITION (/system or /)
        var systemTotal = 0L
        var systemFree = 0L
        var systemReadOnly = true

        val systemDir = File("/system")
        val systemStat = try {
            if (systemDir.exists()) StatFs(systemDir.absolutePath) else StatFs("/")
        } catch (e: Exception) {
            null
        }

        if (systemStat != null) {
            val blockSize = systemStat.blockSizeLong
            systemTotal = systemStat.blockCountLong * blockSize
            systemFree = systemStat.availableBlocksLong * blockSize
            systemReadOnly = systemFree <= 0L || !systemDir.canWrite()
        }

        val partitions = mutableListOf<StoragePartitionInfo>()

        // Primary Shared Volume
        partitions.add(
            StoragePartitionInfo(
                id = "shared_primary",
                name = "Primary Storage",
                mountPoint = externalDir.absolutePath,
                totalBytes = primaryTotal,
                freeBytes = primaryFree,
                usedBytes = primaryUsed,
                usedPercent = primaryPercent,
                isReadOnly = false,
                description = "User files, media, downloads and documents"
            )
        )

        // Internal App Data Partition
        val internalPercent = if (internalTotal > 0L) ((internalUsed * 100L) / internalTotal).toInt().coerceIn(0, 100) else 0
        partitions.add(
            StoragePartitionInfo(
                id = "internal_data",
                name = "Internal (/data)",
                mountPoint = dataDir.absolutePath,
                totalBytes = internalTotal,
                freeBytes = internalFree,
                usedBytes = internalUsed,
                usedPercent = internalPercent,
                isReadOnly = false,
                description = "Private app data, databases and system state"
            )
        )

        // System Root Mount
        val systemUsed = (systemTotal - systemFree).coerceAtLeast(0L)
        val systemPercent = if (systemTotal > 0L) ((systemUsed * 100L) / systemTotal).toInt().coerceIn(0, 100) else 0
        partitions.add(
            StoragePartitionInfo(
                id = "system_root",
                name = "System Root",
                mountPoint = if (systemDir.exists()) "/system" else "/",
                totalBytes = systemTotal,
                freeBytes = systemFree,
                usedBytes = systemUsed,
                usedPercent = systemPercent,
                isReadOnly = systemReadOnly,
                description = if (systemReadOnly) "Read-Only Linux System Image" else "Read-Write System Partition"
            )
        )

        return StorageTelemetryReport(
            primaryTotalBytes = primaryTotal,
            primaryFreeBytes = primaryFree,
            primaryUsedBytes = primaryUsed,
            primaryUsedPercent = primaryPercent,
            internalTotalBytes = internalTotal,
            internalFreeBytes = internalFree,
            internalUsedBytes = internalUsed,
            systemTotalBytes = systemTotal,
            systemFreeBytes = systemFree,
            systemIsReadOnly = systemReadOnly,
            partitions = partitions
        )
    }

    /**
     * Formats bytes to standard human-readable sizes (GB / MB) with precision.
     */
    fun formatBytes(bytes: Long): String {
        val gb = 1024L * 1024L * 1024L
        val mb = 1024L * 1024L
        val kb = 1024L
        return when {
            bytes >= gb -> String.format(java.util.Locale.US, "%.1f GB", bytes.toDouble() / gb)
            bytes >= mb -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / mb)
            bytes >= kb -> String.format(java.util.Locale.US, "%.1f KB", bytes.toDouble() / kb)
            else -> "$bytes B"
        }
    }
}

package com.polymath.fs.core

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object BuiltInScriptManager {

    data class ScriptInfo(
        val file: File,
        val category: String,
        val displayName: String,
        val description: String
    )

    fun getScriptsDirectory(context: Context): File {
        val dir = File(context.filesDir, "scripts")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun extractAllBuiltInScripts(context: Context, forceOverwrite: Boolean = false) {
        val scriptsDir = getScriptsDirectory(context)
        val extensionsDir = File(context.filesDir, "extensions").apply { mkdirs() }

        try {
            val categories = context.assets.list("extensions") ?: return
            for (category in categories) {
                val catFiles = context.assets.list("extensions/$category") ?: continue
                val catDir = File(extensionsDir, category).apply { mkdirs() }

                for (scriptFile in catFiles) {
                    if (!scriptFile.endsWith(".js")) continue

                    // Copy to extensions/Category/file.js
                    val targetInExt = File(catDir, scriptFile)
                    if (forceOverwrite || !targetInExt.exists()) {
                        copyAssetToFile(context, "extensions/$category/$scriptFile", targetInExt)
                    }

                    // Also copy to scripts/Category_Filename.js for easy browsing and execution
                    val flatName = if (scriptFile.equals("index.js", ignoreCase = true)) {
                        "$category.js"
                    } else {
                        "${category}_$scriptFile"
                    }
                    val targetInScripts = File(scriptsDir, flatName)
                    if (forceOverwrite || !targetInScripts.exists()) {
                        copyAssetToFile(context, "extensions/$category/$scriptFile", targetInScripts)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyAssetToFile(context: Context, assetPath: String, outFile: File) {
        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = bytearray(1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun bytearray(size: Int) = ByteArray(size)

    fun getScriptMetadata(file: File): ScriptInfo {
        val name = file.name
        val category = when {
            name.startsWith("Organizer_") || name.equals("Organizer.js", ignoreCase = true) -> "Organizer"
            name.startsWith("Security_") || name.equals("Security.js", ignoreCase = true) -> "Security"
            name.startsWith("Themes_") || name.equals("Themes.js", ignoreCase = true) -> "Themes"
            name.startsWith("Network_") || name.equals("Network.js", ignoreCase = true) -> "Network"
            name.startsWith("Utils_") || name.equals("Utils.js", ignoreCase = true) -> "Utils"
            name.startsWith("Core_") || name.equals("Core.js", ignoreCase = true) -> "Core"
            name.startsWith("AutoOrganizer") -> "AutoOrganizer"
            name.startsWith("GhostVault") -> "GhostVault"
            name.startsWith("SystemAnalytics") -> "SystemAnalytics"
            else -> "Custom"
        }

        val displayName = name.removeSuffix(".js")
            .replace("_", " ")
            .replace("index", "")
            .trim()

        val description = when {
            name.contains("AutoOrganizer", ignoreCase = true) -> "Categorizes downloads into Images, Videos, Documents, Audio, and Archives"
            name.contains("AutomationDaemon", ignoreCase = true) -> "Background watcher daemon that auto-sorts new incoming files"
            name.contains("ThemeEngine", ignoreCase = true) -> "Applies modular dynamic dark and light UI color schemes"
            name.contains("GhostVault", ignoreCase = true) -> "Creates isolated, encrypted vault container with forensic stealth flags"
            name.contains("FtpManager", ignoreCase = true) -> "FTP client endpoint validator and connection tester"
            name.contains("SmbManager", ignoreCase = true) -> "SMB / CIFS network share connection tester and manager"
            name.contains("AutoArchive", ignoreCase = true) -> "Monitors and automatically archives logs and temporary files"
            name.contains("CacheCleaner", ignoreCase = true) -> "Purges application cache buffers, temporary thumbnails, and junk files"
            name.contains("Deduplicator", ignoreCase = true) -> "Scans directory tree and identifies duplicate files"
            name.contains("CloakManager", ignoreCase = true) -> "Stealth masks sensitive files and hides them from media scanners"
            name.contains("Shredder", ignoreCase = true) -> "Multi-pass cryptographic data shredder with secure overwrite"
            name.contains("SystemAnalytics", ignoreCase = true) -> "Real-time memory, storage distribution, and kernel statistics"
            name.contains("AmoledBlack", ignoreCase = true) -> "Applies high-contrast pitch black AMOLED theme"
            name.contains("CyberpunkTheme", ignoreCase = true) -> "Applies vibrant high-contrast cyberpunk neon palette"
            name.contains("MatrixTheme", ignoreCase = true) -> "Applies phosphor green terminal matrix color theme"
            name.contains("ChronosBackup", ignoreCase = true) -> "Point-in-time forensic snapshot backup for directories"
            name.contains("RamDiskMounter", ignoreCase = true) -> "Allocates low-latency high-speed volatile memory RAM disk"
            else -> "Custom JavaScript automation script"
        }

        return ScriptInfo(file, category, displayName, description)
    }
}

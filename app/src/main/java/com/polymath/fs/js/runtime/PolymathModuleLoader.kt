package com.polymath.fs.js.runtime

import android.content.Context
import java.io.File

class PolymathModuleLoader(
    private val context: Context
) : PolymathModuleLoaderInterface {

    override fun loadModuleSource(specifier: String, currentDir: String): String {
        // 1. Check relative or absolute file paths
        val targetFile = when {
            specifier.startsWith("/") -> File(specifier)
            specifier.startsWith("./") || specifier.startsWith("../") -> {
                val base = if (currentDir.isNotEmpty()) File(currentDir) else File("/storage/emulated/0")
                File(base, specifier).canonicalFile
            }
            else -> {
                // Try relative to currentDir or standard script search directories
                val candidate = File(currentDir, specifier)
                if (candidate.exists()) candidate else File("/storage/emulated/0", specifier)
            }
        }

        // Try exact match, or match with .js appended
        val fileToLoad = when {
            targetFile.exists() && targetFile.isFile -> targetFile
            File(targetFile.absolutePath + ".js").exists() -> File(targetFile.absolutePath + ".js")
            File(targetFile, "index.js").exists() -> File(targetFile, "index.js")
            else -> null
        }

        if (fileToLoad != null) {
            return fileToLoad.readText(Charsets.UTF_8)
        }

        // 2. Try loading from assets/extensions/
        try {
            val assetPath = "extensions/$specifier/index.js"
            val inputStream = context.assets.open(assetPath)
            return inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (ignored: Exception) {}

        try {
            val assetPath = "extensions/$specifier.js"
            val inputStream = context.assets.open(assetPath)
            return inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (ignored: Exception) {}

        throw IllegalArgumentException("Cannot find module '$specifier'")
    }
}

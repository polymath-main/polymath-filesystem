package com.polymath.fs.core

import com.polymath.fs.models.FileNode
import java.io.File

object StatParser {

    /**
     * Parses a line output from `stat -c "%A|%s|%Y|%n" <file>`
     * Example line: "drwxr-xr-x|4096|1699999999|/data/data/com.polymath.fs/cache"
     */
    fun parseStatLine(line: String): FileNode.RootFile? {
        val parts = line.split("|", limit = 4)
        if (parts.size < 4) return null

        val permissions = parts[0]
        val sizeStr = parts[1]
        val timeStr = parts[2]
        val pathStr = parts[3]

        val isDirectory = permissions.startsWith("d")
        val size = sizeStr.toLongOrNull() ?: 0L
        // stat %Y is in seconds since epoch, convert to milliseconds
        val lastModified = (timeStr.toLongOrNull() ?: 0L) * 1000L
        
        val name = File(pathStr).name

        return FileNode.RootFile(
            name = name,
            path = pathStr,
            size = size,
            lastModified = lastModified,
            isDirectory = isDirectory,
            permissions = permissions
        )
    }
}

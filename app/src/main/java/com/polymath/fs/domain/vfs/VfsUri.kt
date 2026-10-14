package com.polymath.fs.domain.vfs

data class VfsUri(
    val scheme: String,
    val authority: String?,
    val path: String
) {
    fun serialized(): String = when (scheme) {
        "file" -> path
        "content" -> {
            val formattedPath = if (path.startsWith("/")) path else "/$path"
            if (authority != null) "content://$authority$formattedPath" else "content:$formattedPath"
        }
        else -> {
            val formattedPath = if (path.startsWith("/")) path else if (path.isNotEmpty()) "/$path" else ""
            if (authority != null) "$scheme://$authority$formattedPath" else "$scheme:$formattedPath"
        }
    }

    companion object {
        private val SCHEMES = listOf("content", "sftp", "ftp", "smb", "synapse")

        fun parse(raw: String): VfsUri {
            val trimmed = raw.trim()
            for (s in SCHEMES) {
                val prefix = "$s://"
                if (trimmed.startsWith(prefix, ignoreCase = true)) {
                    val remainder = trimmed.substring(prefix.length)
                    val slashIndex = remainder.indexOf('/')
                    return if (slashIndex != -1) {
                        val auth = remainder.substring(0, slashIndex).ifEmpty { null }
                        val path = remainder.substring(slashIndex)
                        VfsUri(scheme = s, authority = auth, path = path)
                    } else {
                        val auth = remainder.ifEmpty { null }
                        VfsUri(scheme = s, authority = auth, path = "")
                    }
                }
            }

            if (trimmed.startsWith("file://", ignoreCase = true)) {
                val p = trimmed.substring("file://".length)
                return VfsUri(scheme = "file", authority = null, path = if (p.startsWith("/")) p else "/$p")
            }

            return VfsUri(scheme = "file", authority = null, path = trimmed)
        }
    }
}

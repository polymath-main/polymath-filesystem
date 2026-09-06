package com.polymath.fs.js.runtime.modules

import android.os.Build
import com.polymath.fs.js.runtime.PolymathJSPOSIXInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.Base64

class FsNativeModule : PolymathJSPOSIXInterface {

    override fun readFileSync(path: String, encoding: String): String {
        val file = File(path)
        if (!file.exists()) {
            throw IllegalArgumentException("ENOENT: no such file or directory, open '$path'")
        }
        if (file.isDirectory) {
            throw IllegalArgumentException("EISDIR: illegal operation on a directory, read '$path'")
        }

        return when (encoding.lowercase()) {
            "base64" -> {
                val bytes = file.readBytes()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Base64.getEncoder().encodeToString(bytes)
                } else {
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                }
            }
            "hex" -> {
                file.readBytes().joinToString("") { "%02x".format(it) }
            }
            else -> {
                file.readText(Charset.forName("UTF-8"))
            }
        }
    }

    override fun writeFileSync(path: String, content: String): Boolean {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(content, Charset.forName("UTF-8"))
        return true
    }

    override fun appendFileSync(path: String, content: String): Boolean {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.appendText(content, Charset.forName("UTF-8"))
        return true
    }

    override fun existsSync(path: String): Boolean {
        return File(path).exists()
    }

    override fun readdirSync(path: String): String {
        val dir = File(path)
        if (!dir.exists()) {
            throw IllegalArgumentException("ENOENT: no such file or directory, scandir '$path'")
        }
        if (!dir.isDirectory) {
            throw IllegalArgumentException("ENOTDIR: not a directory, scandir '$path'")
        }
        val names = dir.list() ?: emptyArray()
        return JSONArray(names.toList()).toString()
    }

    override fun statSync(path: String): String {
        val file = File(path)
        if (!file.exists()) {
            throw IllegalArgumentException("ENOENT: no such file or directory, stat '$path'")
        }
        val obj = JSONObject().apply {
            put("size", file.length())
            put("isDirectory", file.isDirectory)
            put("isFile", file.isFile)
            put("mtimeMs", file.lastModified())
            put("ctimeMs", file.lastModified())
            put("canRead", file.canRead())
            put("canWrite", file.canWrite())
            put("canExecute", file.canExecute())
            put("path", file.absolutePath)
            put("name", file.name)
        }
        return obj.toString()
    }

    override fun mkdirSync(path: String, recursive: Boolean): Boolean {
        val dir = File(path)
        return if (recursive) dir.mkdirs() else dir.mkdir()
    }

    override fun unlinkSync(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) {
            throw IllegalArgumentException("ENOENT: no such file or directory, unlink '$path'")
        }
        if (file.isDirectory) {
            throw IllegalArgumentException("EPERM: operation not permitted, unlink directory '$path'")
        }
        return file.delete()
    }

    override fun rmdirSync(path: String, recursive: Boolean): Boolean {
        val dir = File(path)
        if (!dir.exists()) {
            throw IllegalArgumentException("ENOENT: no such file or directory, rmdir '$path'")
        }
        return if (recursive) dir.deleteRecursively() else dir.delete()
    }

    override fun copyFileSync(src: String, dest: String): Boolean {
        val srcFile = File(src)
        if (!srcFile.exists()) {
            throw IllegalArgumentException("ENOENT: no such file or directory, copyfile '$src'")
        }
        val destFile = File(dest)
        destFile.parentFile?.mkdirs()
        srcFile.copyTo(destFile, overwrite = true)
        return true
    }

    override fun renameSync(oldPath: String, newPath: String): Boolean {
        val oldFile = File(oldPath)
        if (!oldFile.exists()) {
            throw IllegalArgumentException("ENOENT: no such file or directory, rename '$oldPath'")
        }
        val newFile = File(newPath)
        newFile.parentFile?.mkdirs()
        return oldFile.renameTo(newFile)
    }

    override fun chmodSync(path: String, mode: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        return try {
            Runtime.getRuntime().exec(arrayOf("chmod", mode, file.absolutePath)).waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}

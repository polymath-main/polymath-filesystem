package com.polymath.fs.js

import android.content.Context
import android.widget.Toast
import app.cash.quickjs.QuickJs
import com.polymath.fs.data.repository.FileSystemRepository
import com.polymath.fs.models.FileNode
import com.polymath.fs.core.RootShellHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class ExtensionManifest(
    val id: String,
    val name: String,
    val version: String,
    val permissions: List<String>
) {
    companion object {
        fun fromJson(json: String): ExtensionManifest {
            val obj = JSONObject(json)
            val permsArray = obj.optJSONArray("permissions")
            val perms = mutableListOf<String>()
            if (permsArray != null) {
                for (i in 0 until permsArray.length()) {
                    perms.add(permsArray.getString(i))
                }
            }
            return ExtensionManifest(
                id = obj.getString("id"),
                name = obj.getString("name"),
                version = obj.getString("version"),
                permissions = perms
            )
        }
    }
}

interface PolymathFS {
    fun listDir(path: String): String
    fun copy(srcJson: String, dest: String): Boolean
    fun move(srcJson: String, dest: String): Boolean
    fun delete(pathsJson: String): Boolean
    fun mkdir(path: String): Boolean
    fun rename(oldPath: String, newName: String): Boolean
}

interface PolymathUI {
    fun showToast(message: String)
}

class SecurityException(msg: String) : RuntimeException(msg)

@Singleton
class PolymathJSBridge @Inject constructor(
    private val repository: FileSystemRepository,
    private val context: Context,
    private val shellHolder: RootShellHolder = RootShellHolder(),
    val jsRuntime: com.polymath.fs.js.runtime.PolymathJSRuntime = com.polymath.fs.js.runtime.PolymathJSRuntime(context, repository, shellHolder)
) {

    fun executeExtension(
        manifestJson: String,
        script: String,
        onAlert: ((String, String) -> Unit)? = null,
        onConsoleLog: ((String, String) -> Unit)? = null,
        selectedFiles: List<String>? = null
    ): String {
        val manifest = try { ExtensionManifest.fromJson(manifestJson) } catch (e: Exception) { null }
        val scriptName = manifest?.name ?: "extension.js"
        return jsRuntime.execute(
            script = script,
            scriptName = scriptName,
            workingDir = "/storage/emulated/0",
            selectedFiles = selectedFiles,
            onAlert = onAlert,
            onConsoleLog = onConsoleLog
        )
    }

    fun executeScript(
        script: String,
        scriptName: String = "script.js",
        onAlert: ((String, String) -> Unit)? = null,
        onConsoleLog: ((String, String) -> Unit)? = null,
        selectedFiles: List<String>? = null
    ): String {
        return jsRuntime.execute(
            script = script,
            scriptName = scriptName,
            workingDir = "/storage/emulated/0",
            selectedFiles = selectedFiles,
            onAlert = onAlert,
            onConsoleLog = onConsoleLog
        )
    }
}


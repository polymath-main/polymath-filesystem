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
    private val shellHolder: RootShellHolder = RootShellHolder()
) {

    private fun checkPermission(manifest: ExtensionManifest, permission: String) {
        if (!manifest.permissions.contains(permission) && !manifest.permissions.contains("all")) {
            throw SecurityException("Extension ${manifest.id} lacks permission: $permission")
        }
    }

    private fun parseStringArray(jsonArrayStr: String): List<String> {
        val arr = JSONArray(jsonArrayStr)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            list.add(arr.getString(i))
        }
        return list
    }

    fun executeExtension(
        manifestJson: String,
        script: String,
        onAlert: ((String, String) -> Unit)? = null,
        onConsoleLog: ((String, String) -> Unit)? = null,
        selectedFiles: List<String>? = null
    ): String {
        val manifest = ExtensionManifest.fromJson(manifestJson)
        var result = ""
        QuickJs.create().use { quickJs ->
            
            val fsInterface = object : PolymathFS {
                override fun listDir(path: String): String {
                    checkPermission(manifest, "fs.read")
                    val nodes = runBlocking { repository.listDir(path) }
                    val array = JSONArray()
                    nodes.forEach { node ->
                        val obj = JSONObject()
                        obj.put("name", node.name)
                        obj.put("path", node.path)
                        obj.put("size", node.size)
                        obj.put("lastModified", node.lastModified)
                        obj.put("isDirectory", node.isDirectory)
                        array.put(obj)
                    }
                    return array.toString()
                }

                override fun copy(srcJson: String, dest: String): Boolean {
                    checkPermission(manifest, "fs.write")
                    val src = parseStringArray(srcJson)
                    runBlocking {
                        repository.copy(src, dest).collect { }
                    }
                    return true
                }

                override fun move(srcJson: String, dest: String): Boolean {
                    checkPermission(manifest, "fs.write")
                    val src = parseStringArray(srcJson)
                    runBlocking {
                        repository.move(src, dest).collect { }
                    }
                    return true
                }

                override fun delete(pathsJson: String): Boolean {
                    checkPermission(manifest, "fs.write")
                    val paths = parseStringArray(pathsJson)
                    return runBlocking { repository.delete(paths) }
                }

                override fun mkdir(path: String): Boolean {
                    checkPermission(manifest, "fs.write")
                    return runBlocking { repository.mkdir(path) }
                }

                override fun rename(oldPath: String, newName: String): Boolean {
                    checkPermission(manifest, "fs.write")
                    return runBlocking { repository.rename(oldPath, newName) }
                }
            }

            val uiInterface = object : PolymathUI {
                override fun showToast(message: String) {
                    checkPermission(manifest, "ui.toast")
                    runBlocking(Dispatchers.Main) {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val osNativeImpl = PolymathOSNativeImpl(context, repository, shellHolder, onAlert, onConsoleLog)

            quickJs.set("PolymathFS", PolymathFS::class.java, fsInterface)
            quickJs.set("PolymathUI", PolymathUI::class.java, uiInterface)
            quickJs.set("PolymathOSNative", PolymathOSNativeInterface::class.java, osNativeImpl)
            
            // Format selectedFiles array as JSON string
            val selectedFilesJson = if (selectedFiles != null) {
                JSONArray(selectedFiles).toString()
            } else {
                "[]"
            }

            // Set up Polymath & PolymathOS namespaces, console logging, and global polyfills
            quickJs.evaluate("""
                var Polymath = {
                    fs: PolymathFS,
                    ui: PolymathUI
                };

                var window = this;
                var global = this;
                var selectedFiles = $selectedFilesJson;

                var PolymathOS = {
                    toast: function(msg) { PolymathOSNative.toast(String(msg)); },
                    alert: function(arg1, arg2) {
                        if (arg2 !== undefined) {
                            return PolymathOSNative.alert2(String(arg1), String(arg2));
                        } else {
                            return PolymathOSNative.alert2("Polymath Alert", String(arg1));
                        }
                    },
                    prompt: function(title, callbackName) {
                        var res = PolymathOSNative.prompt2(String(title), callbackName ? String(callbackName) : "");
                        if (callbackName && typeof window[callbackName] === 'function') {
                            try { window[callbackName](res); } catch(e) {}
                        }
                        return res;
                    },
                    setTheme: function(json) { return PolymathOSNative.setTheme(String(json)); },
                    daemonCommand: function(action, payload) { return PolymathOSNative.daemonCommand(String(action), String(payload || "")); },
                    listen: function(event, path, cb) { return PolymathOSNative.listen(String(event), String(path), String(cb || "")); },
                    readFile: function(path) { return PolymathOSNative.readFile(String(path)); },
                    writeFile: function(path, content) { return PolymathOSNative.writeFile(String(path), String(content)); },
                    ftpRequest: function(host, port, user, pass, action, path) {
                        return PolymathOSNative.ftpRequest(String(host), parseInt(port)||21, String(user), String(pass), String(action), String(path));
                    },
                    smbRequest: function(host, port, user, pass, action, path) {
                        return PolymathOSNative.smbRequest(String(host), parseInt(port)||445, String(user), String(pass), String(action), String(path));
                    }
                };

                var console = {
                    log: function() {
                        var msg = Array.prototype.slice.call(arguments).map(function(a) {
                            return typeof a === 'object' ? JSON.stringify(a) : String(a);
                        }).join(" ");
                        PolymathOSNative.consoleLog("LOG", msg);
                    },
                    info: function() {
                        var msg = Array.prototype.slice.call(arguments).map(function(a) {
                            return typeof a === 'object' ? JSON.stringify(a) : String(a);
                        }).join(" ");
                        PolymathOSNative.consoleLog("INFO", msg);
                    },
                    warn: function() {
                        var msg = Array.prototype.slice.call(arguments).map(function(a) {
                            return typeof a === 'object' ? JSON.stringify(a) : String(a);
                        }).join(" ");
                        PolymathOSNative.consoleLog("WARN", msg);
                    },
                    error: function() {
                        var msg = Array.prototype.slice.call(arguments).map(function(a) {
                            return typeof a === 'object' ? JSON.stringify(a) : String(a);
                        }).join(" ");
                        PolymathOSNative.consoleLog("ERROR", msg);
                    }
                };
            """.trimIndent())

            val res = quickJs.evaluate(script)
            result = res?.toString() ?: ""
        }
        return result
    }

    fun executeScript(
        script: String,
        scriptName: String = "script.js",
        onAlert: ((String, String) -> Unit)? = null,
        onConsoleLog: ((String, String) -> Unit)? = null,
        selectedFiles: List<String>? = null
    ): String {
        val defaultManifestJson = JSONObject().apply {
            put("id", scriptName)
            put("name", scriptName)
            put("version", "1.0.0")
            put("permissions", JSONArray().apply {
                put("all")
                put("fs.read")
                put("fs.write")
                put("ui.toast")
            })
        }.toString()
        return executeExtension(defaultManifestJson, script, onAlert, onConsoleLog, selectedFiles)
    }
}

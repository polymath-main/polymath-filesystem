package com.polymath.fs.core

import android.content.Context
import android.widget.Toast
import com.polymath.fs.data.repository.IFileSystemRepository
import com.polymath.fs.js.PolymathJSBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RiftEngine @Inject constructor() {

    suspend fun cast(
        riftFile: File,
        context: Context,
        repository: IFileSystemRepository,
        onOutput: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val content = riftFile.readText()
            val json = JSONObject(content)
            
            val script = json.optString("script", "")
            val targetDir = json.optString("target_dir", "/sdcard")
            val riftName = json.optString("rift_name", riftFile.nameWithoutExtension)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Casting Rift: $riftName 🌌", Toast.LENGTH_SHORT).show()
            }
            
            val bridge = PolymathJSBridge(repository, context)
            val result = bridge.jsRuntime.execute(
                script = script,
                scriptName = riftFile.name,
                workingDir = targetDir,
                onConsoleLog = { level, msg -> 
                    onOutput("[$level] $msg")
                },
                onAlert = { title, msg -> 
                    onOutput("[ALERT: $title] $msg")
                }
            )
            
            if (result.isNotBlank()) {
                onOutput("Rift Result: $result")
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Rift Collapse: ${e.message}", Toast.LENGTH_LONG).show()
            }
            onOutput("Rift Collapse: ${e.message}")
        }
    }
}

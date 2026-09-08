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
            val targetDir = riftFile.parent ?: "/sdcard"
            val riftName = riftFile.name
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Engaging Rift Sandbox: $riftName 🌌", Toast.LENGTH_SHORT).show()
            }
            
            val bridge = PolymathJSBridge(repository, context)
            
            // Execute in true Zero-Storage Sandbox mode (isDryRun = true)
            // The script simply initializes the sandbox context for the user to interact via Terminal
            val sandboxScript = """
                console.log('🌌 Zero-Storage Sandbox Engaged');
                console.log('Target: $riftName');
                console.log('Any file operations performed here will run against the Virtual File System overlay.');
            """.trimIndent()
            
            val result = bridge.jsRuntime.execute(
                script = sandboxScript,
                scriptName = "RiftInit",
                workingDir = targetDir,
                onConsoleLog = { level, msg -> 
                    onOutput("[$level] $msg")
                },
                onAlert = { title, msg -> 
                    onOutput("[ALERT: $title] $msg")
                },
                isDryRun = true
            )
            
            if (result.isNotBlank()) {
                onOutput("Sandbox Result: $result")
            }
        } catch (e: Exception) {
            onOutput("[Rift Engine Error]: ${e.message}")
        }
    }
}

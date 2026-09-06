package com.polymath.fs.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.polymath.fs.data.repository.FileSystemRepository
import com.polymath.fs.js.runtime.PolymathJSRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class JSTesterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.polymath.fs.ACTION_TEST_SCRIPT") {
            val scriptPath = intent.getStringExtra("scriptPath") ?: return
            val outputPath = intent.getStringExtra("outputPath") ?: return
            val selectedFilesStr = intent.getStringExtra("selectedFiles") ?: "[]"

            val repo = FileSystemRepository(context)
            val shellHolder = RootShellHolder()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val scriptFile = File(scriptPath)
                    if (!scriptFile.exists()) {
                        File(outputPath).writeText("Error: Script file not found at $scriptPath")
                        return@launch
                    }

                    val script = scriptFile.readText()
                    val runtime = PolymathJSRuntime(context, repo, shellHolder)
                    
                    val selectedFilesList = try {
                        val array = org.json.JSONArray(selectedFilesStr)
                        val list = mutableListOf<String>()
                        for (i in 0 until array.length()) list.add(array.getString(i))
                        list
                    } catch (e: Exception) {
                        emptyList<String>()
                    }

                    val logBuilder = java.lang.StringBuilder()
                    
                    val result = runtime.execute(
                        script = script,
                        scriptName = scriptFile.name,
                        selectedFiles = selectedFilesList,
                        actionId = "test_run",
                        onConsoleLog = { level, message -> logBuilder.append("[$level] $message\n") },
                        onAlert = { title, message -> logBuilder.append("[ALERT: $title] $message\n") }
                    )
                    
                    logBuilder.append("\n=== EXECUTION RESULT ===\n").append(result)
                    File(outputPath).writeText(logBuilder.toString())
                } catch (e: Exception) {
                    val errorLog = "Exception: ${e.message}\n${Log.getStackTraceString(e)}"
                    File(outputPath).writeText(errorLog)
                }
            }
        }
    }
}

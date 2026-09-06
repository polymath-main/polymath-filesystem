package com.polymath.fs.core

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.polymath.fs.PolymathApp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScriptSchedulerWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val scheduleId = inputData.getString("schedule_id") ?: ""
        val scriptPath = inputData.getString("script_path") ?: ""
        val scriptName = inputData.getString("script_name") ?: "script.js"

        if (scriptPath.isEmpty()) {
            return Result.failure()
        }

        val scriptFile = File(scriptPath)
        if (!scriptFile.exists() || !scriptFile.canRead()) {
            ScriptScheduleManager.updateExecutionResult(
                appContext,
                scheduleId,
                "Error: Script file not found or unreadable"
            )
            return Result.failure()
        }

        return try {
            val scriptContent = scriptFile.readText()
            val app = appContext.applicationContext as? PolymathApp
            val bridge = app?.jsBridge

            if (bridge != null) {
                val output = bridge.executeScript(
                    script = scriptContent,
                    scriptName = scriptName,
                    onAlert = null
                )
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val summary = if (output.isNotBlank() && output != "undefined") {
                    "Success at $timeStr: $output"
                } else {
                    "Success at $timeStr (executed normally)"
                }
                ScriptScheduleManager.updateExecutionResult(appContext, scheduleId, summary)
                Result.success()
            } else {
                ScriptScheduleManager.updateExecutionResult(
                    appContext,
                    scheduleId,
                    "Error: Application context unavailable"
                )
                Result.retry()
            }
        } catch (e: Exception) {
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            ScriptScheduleManager.updateExecutionResult(
                appContext,
                scheduleId,
                "Failed at $timeStr: ${e.message}"
            )
            Result.failure()
        }
    }
}

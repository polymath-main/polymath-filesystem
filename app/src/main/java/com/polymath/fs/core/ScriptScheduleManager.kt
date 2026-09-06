package com.polymath.fs.core

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ScriptScheduleItem(
    val id: String,
    val scriptName: String,
    val scriptPath: String,
    val intervalMinutes: Long,
    val isPeriodic: Boolean,
    val isEnabled: Boolean,
    val lastRunTime: Long = 0L,
    val lastResult: String = ""
)

object ScriptScheduleManager {

    private const val PREFS_NAME = "script_schedules"
    private const val KEY_SCHEDULES = "schedules_json"

    fun getAllSchedules(context: Context): List<ScriptScheduleItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SCHEDULES, "[]") ?: "[]"
        val list = mutableListOf<ScriptScheduleItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ScriptScheduleItem(
                        id = obj.getString("id"),
                        scriptName = obj.getString("scriptName"),
                        scriptPath = obj.getString("scriptPath"),
                        intervalMinutes = obj.optLong("intervalMinutes", 60L),
                        isPeriodic = obj.optBoolean("isPeriodic", true),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        lastRunTime = obj.optLong("lastRunTime", 0L),
                        lastResult = obj.optString("lastResult", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun saveSchedules(context: Context, schedules: List<ScriptScheduleItem>) {
        val array = JSONArray()
        for (item in schedules) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("scriptName", item.scriptName)
                put("scriptPath", item.scriptPath)
                put("intervalMinutes", item.intervalMinutes)
                put("isPeriodic", item.isPeriodic)
                put("isEnabled", item.isEnabled)
                put("lastRunTime", item.lastRunTime)
                put("lastResult", item.lastResult)
            }
            array.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SCHEDULES, array.toString()).apply()
    }

    fun scheduleScript(
        context: Context,
        scriptFile: File,
        intervalMinutes: Long,
        isPeriodic: Boolean = true
    ): ScriptScheduleItem {
        val id = UUID.randomUUID().toString().substring(0, 8)
        val item = ScriptScheduleItem(
            id = id,
            scriptName = scriptFile.name,
            scriptPath = scriptFile.absolutePath,
            intervalMinutes = intervalMinutes,
            isPeriodic = isPeriodic,
            isEnabled = true,
            lastRunTime = 0L,
            lastResult = "Scheduled"
        )

        val workManager = WorkManager.getInstance(context)
        val inputData = workDataOf(
            "schedule_id" to id,
            "script_path" to scriptFile.absolutePath,
            "script_name" to scriptFile.name
        )

        val uniqueWorkName = "polymath_script_$id"

        if (isPeriodic) {
            // PeriodicWorkRequest requires minimum 15 minutes interval in Android WorkManager
            val effectiveMinutes = if (intervalMinutes < 15L) 15L else intervalMinutes
            val workRequest = PeriodicWorkRequestBuilder<ScriptSchedulerWorker>(
                effectiveMinutes, TimeUnit.MINUTES
            )
                .setInputData(inputData)
                .addTag("polymath_script")
                .addTag("script_$id")
                .build()

            workManager.enqueueUniquePeriodicWork(
                uniqueWorkName,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        } else {
            val workRequest = OneTimeWorkRequestBuilder<ScriptSchedulerWorker>()
                .setInputData(inputData)
                .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
                .addTag("polymath_script")
                .addTag("script_$id")
                .build()

            workManager.enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        val all = getAllSchedules(context).toMutableList()
        all.removeAll { it.scriptPath == scriptFile.absolutePath }
        all.add(0, item)
        saveSchedules(context, all)

        return item
    }

    fun cancelSchedule(context: Context, id: String) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork("polymath_script_$id")
        workManager.cancelAllWorkByTag("script_$id")

        val all = getAllSchedules(context).toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index >= 0) {
            all[index] = all[index].copy(isEnabled = false, lastResult = "Cancelled")
            saveSchedules(context, all)
        }
    }

    fun removeSchedule(context: Context, id: String) {
        cancelSchedule(context, id)
        val all = getAllSchedules(context).filter { it.id != id }
        saveSchedules(context, all)
    }

    fun updateExecutionResult(context: Context, id: String, result: String) {
        val all = getAllSchedules(context).toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index >= 0) {
            all[index] = all[index].copy(
                lastRunTime = System.currentTimeMillis(),
                lastResult = result
            )
            saveSchedules(context, all)
        }
    }
}

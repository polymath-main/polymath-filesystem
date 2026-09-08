package com.polymath.fs.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class IntentLog(
    val timestamp: Long,
    val action: String, // e.g., "opened", "modified", "casted_rift"
    val filePath: String,
    val contextTags: List<String>
)

@Singleton
class IntentEngine @Inject constructor(private val context: Context) {

    private val intentLogFile by lazy { File(context.filesDir, "rahman_intent_logs.json") }
    private val logs = mutableListOf<IntentLog>()

    init {
        loadLogs()
    }

    private fun loadLogs() {
        if (!intentLogFile.exists()) return
        try {
            val content = intentLogFile.readText()
            val array = JSONArray(content)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val tagsArray = obj.optJSONArray("tags") ?: JSONArray()
                val tags = mutableListOf<String>()
                for (j in 0 until tagsArray.length()) {
                    tags.add(tagsArray.getString(j))
                }
                logs.add(
                    IntentLog(
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        action = obj.optString("action", "accessed"),
                        filePath = obj.optString("filePath", ""),
                        contextTags = tags
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun logIntent(action: String, filePath: String, contextRaw: String = "") = withContext(Dispatchers.IO) {
        val tags = contextRaw.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() && it.length > 2 }
        logs.add(0, IntentLog(System.currentTimeMillis(), action, filePath, tags))
        
        // Keep only last 1000 logs to prevent bloat
        if (logs.size > 1000) {
            logs.removeAt(logs.lastIndex)
        }
        
        try {
            val array = JSONArray()
            for (log in logs) {
                val obj = JSONObject()
                obj.put("timestamp", log.timestamp)
                obj.put("action", log.action)
                obj.put("filePath", log.filePath)
                val tagsArray = JSONArray()
                log.contextTags.forEach { tagsArray.put(it) }
                obj.put("tags", tagsArray)
                array.put(obj)
            }
            intentLogFile.writeText(array.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Parses a human intent query (e.g. "modified tuesday", "antigravity project")
     * and returns a map of matching file paths to their context reasons ordered by relevance.
     */
    fun resolveIntent(query: String): Map<String, List<String>> {
        val q = query.lowercase()
        val terms = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        
        // Semantic Time Heuristics
        val now = Calendar.getInstance()
        var targetTimeRange: LongRange? = null
        
        if (q.contains("today")) {
            val startOfDay = now.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
            targetTimeRange = startOfDay..System.currentTimeMillis()
        } else if (q.contains("yesterday")) {
            val startOfYesterday = now.apply { add(Calendar.DAY_OF_YEAR, -1); set(Calendar.HOUR_OF_DAY, 0) }.timeInMillis
            val endOfYesterday = now.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
            targetTimeRange = startOfYesterday..endOfYesterday
        } else if (q.contains("tuesday")) {
            // Simplified: Just matches the word "tuesday" via tags or looks back to the last Tuesday.
            val pastTuesday = now.apply {
                while (get(Calendar.DAY_OF_WEEK) != Calendar.TUESDAY) {
                    add(Calendar.DAY_OF_YEAR, -1)
                }
                set(Calendar.HOUR_OF_DAY, 0)
            }.timeInMillis
            targetTimeRange = pastTuesday..(pastTuesday + 86400000)
        }

        val scoredPaths = mutableMapOf<String, Int>()
        val pathReasons = mutableMapOf<String, MutableList<String>>()

        for (log in logs) {
            var score = 0
            val reasons = mutableListOf<String>()
            
            // Temporal match
            if (targetTimeRange != null && log.timestamp in targetTimeRange) {
                score += 50
                if (q.contains("today")) reasons.add("Temporal: Today")
                else if (q.contains("yesterday")) reasons.add("Temporal: Yesterday")
                else if (q.contains("tuesday")) reasons.add("Temporal: Tuesday")
                else reasons.add("Temporal: Match")
            }
            
            // Contextual Tag match
            val matchingTags = terms.filter { term -> log.contextTags.any { tag -> tag.contains(term) } || log.filePath.lowercase().contains(term) }
            val tagMatchCount = matchingTags.size
            if (tagMatchCount > 0) {
                score += (tagMatchCount * 10)
                reasons.add("Tag: ${matchingTags.first()}")
            }
            
            // Action match
            val matchingActions = terms.filter { log.action.contains(it) }
            if (matchingActions.isNotEmpty()) {
                score += 20
                reasons.add("Action: ${log.action}")
            }

            if (score > 0) {
                val existing = scoredPaths[log.filePath] ?: 0
                scoredPaths[log.filePath] = existing + score
                val existingReasons = pathReasons.getOrPut(log.filePath) { mutableListOf() }
                reasons.forEach { if (!existingReasons.contains(it)) existingReasons.add(it) }
            }
        }

        return scoredPaths.entries.sortedByDescending { it.value }
            .associate { it.key to (pathReasons[it.key] ?: emptyList()) }
    }
}

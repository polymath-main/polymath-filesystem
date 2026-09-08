package com.polymath.fs.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class TabFlowState(
    val id: String,
    val path: String,
    val scrollPosition: Int,
    val terminalHistory: String
)

data class RahmanFlowState(
    val tabs: List<TabFlowState>,
    val activeTabId: String
)

@Singleton
class FlowStateManager @Inject constructor(private val context: Context) {
    
    private val stateFile by lazy { File(context.filesDir, "rahman_flow_state.json") }
    
    suspend fun saveState(state: RahmanFlowState) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject()
            val tabsArray = JSONArray()
            for (tab in state.tabs) {
                val tabObj = JSONObject().apply {
                    put("id", tab.id)
                    put("path", tab.path)
                    put("scrollPosition", tab.scrollPosition)
                    put("terminalHistory", tab.terminalHistory)
                }
                tabsArray.put(tabObj)
            }
            json.put("tabs", tabsArray)
            json.put("activeTabId", state.activeTabId)
            
            stateFile.writeText(json.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun loadState(): RahmanFlowState? = withContext(Dispatchers.IO) {
        if (!stateFile.exists()) return@withContext null
        
        try {
            val content = stateFile.readText()
            val json = JSONObject(content)
            val tabsArray = json.optJSONArray("tabs") ?: JSONArray()
            
            val tabs = mutableListOf<TabFlowState>()
            for (i in 0 until tabsArray.length()) {
                val tabObj = tabsArray.getJSONObject(i)
                tabs.add(
                    TabFlowState(
                        id = tabObj.optString("id", java.util.UUID.randomUUID().toString()),
                        path = tabObj.optString("path", "/sdcard"),
                        scrollPosition = tabObj.optInt("scrollPosition", 0),
                        terminalHistory = tabObj.optString("terminalHistory", "")
                    )
                )
            }
            val activeTabId = json.optString("activeTabId", "")
            return@withContext RahmanFlowState(tabs, activeTabId)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}

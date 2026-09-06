package com.polymath.fs.core

import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object ExtensionManager {
    private val hooks = ConcurrentHashMap<String, ContextHook>()

    fun registerContextHook(id: String, displayName: String, filterRegex: String, jsCallbackId: String) {
        hooks[id] = ContextHook(id, displayName, filterRegex, jsCallbackId)
    }

    fun unregisterContextHook(id: String) {
        hooks.remove(id)
    }

    fun getApplicableHooks(selectedFilePaths: List<String>): List<ContextHook> {
        if (selectedFilePaths.isEmpty()) return emptyList()

        return hooks.values.filter { hook ->
            try {
                val pattern = Pattern.compile(hook.filterRegex, Pattern.CASE_INSENSITIVE)
                // Applicable if AT LEAST ONE selected file matches the regex, or ALL? 
                // Let's say all selected files must match the regex to show the action.
                selectedFilePaths.all { path -> pattern.matcher(path).find() }
            } catch (e: Exception) {
                false
            }
        }
    }
}

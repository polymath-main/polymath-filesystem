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
                selectedFilePaths.all { path -> hook.compiledRegex.matcher(path).find() }
            } catch (e: Exception) {
                false
            }
        }
    }
}

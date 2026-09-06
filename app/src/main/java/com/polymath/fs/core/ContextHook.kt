package com.polymath.fs.core

data class ContextHook(
    val id: String,
    val displayName: String,
    val filterRegex: String,
    val jsCallbackId: String
) {
    val compiledRegex: java.util.regex.Pattern by lazy {
        java.util.regex.Pattern.compile(filterRegex, java.util.regex.Pattern.CASE_INSENSITIVE)
    }
}

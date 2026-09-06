package com.polymath.fs.core

data class ContextHook(
    val id: String,
    val displayName: String,
    val filterRegex: String,
    val jsCallbackId: String
)

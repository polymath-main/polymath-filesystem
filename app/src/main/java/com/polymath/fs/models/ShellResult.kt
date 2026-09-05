package com.polymath.fs.models

data class ShellResult(
    val exitCode: Int,
    val output: List<String>,
    val error: List<String>
) {
    val isSuccess: Boolean
        get() = exitCode == 0
}

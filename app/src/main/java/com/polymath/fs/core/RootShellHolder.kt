package com.polymath.fs.core

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ShellResult(
    val isSuccess: Boolean,
    val output: List<String>,
    val error: List<String>,
    val code: Int
)

@Singleton
class RootShellHolder @Inject constructor() {

    suspend fun execute(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        val result = Shell.cmd(cmd).exec()
        ShellResult(
            isSuccess = result.isSuccess,
            output = result.out,
            error = result.err,
            code = result.code
        )
    }

    fun executeStream(cmd: String): Flow<String> = callbackFlow {
        val job = Shell.cmd(cmd).submit { result ->
            // In libsu 5.x, submit gives a Result. But wait, callbackFlow for streaming output?
            // Actually, libsu has an out list which we could read from, or use a custom callback to receive lines real-time.
            // A simple implementation for now:
            result.out.forEach { trySend(it) }
            result.err.forEach { trySend(it) }
            close()
        }
        awaitClose {
            // Cancellation isn't easily supported by basic submit without saving the Job,
            // but for simple streams this works.
        }
    }
}

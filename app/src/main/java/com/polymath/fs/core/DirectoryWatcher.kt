package com.polymath.fs.core

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.InputStreamReader
import java.io.BufferedReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectoryWatcher @Inject constructor() {

    fun watchDirectory(path: String): Flow<String> = callbackFlow {
        // Fallback or use inotifywait if available.
        // inotifywait -m -r -e create,delete,modify,move path
        val cmd = "inotifywait -m -r -e create,delete,modify,move \"$path\""
        
// Using libsu for persistent shell
        val job = Shell.cmd(cmd).submit { result ->
            result.out.forEach { trySend(it) }
        }
        
        awaitClose {
            // libsu doesn't have a direct cancel for jobs, but the shell lifecycle handles it.
        }
    }.flowOn(Dispatchers.IO)
}

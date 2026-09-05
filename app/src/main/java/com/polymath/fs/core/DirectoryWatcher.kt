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
        
        // To read continuously from a shell command in libsu, we can create a shell process,
        // but for libsu Shell, we can't easily stream stdout continuously through the standard API unless we use custom Output streams.
        val shell = Shell.getShell()
        
        // We can execute a background task using sh
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        
        var line: String?
        while (true) {
            line = reader.readLine()
            if (line == null) break
            trySend(line)
        }
        
        process.waitFor()
        close()
        
        awaitClose {
            process.destroy()
        }
    }.flowOn(Dispatchers.IO)
}

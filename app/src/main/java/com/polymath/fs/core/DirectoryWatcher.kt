package com.polymath.fs.core

import android.os.Build
import android.os.FileObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectoryWatcher @Inject constructor() {

    fun watchDirectory(path: String): Flow<String> = callbackFlow {
        val file = File(path)
        if (!file.exists()) {
            close()
            return@callbackFlow
        }

        val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(file, ALL_EVENTS) {
                override fun onEvent(event: Int, eventPath: String?) {
                    if (eventPath != null) {
                        trySend("$event:$eventPath")
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(path, ALL_EVENTS) {
                override fun onEvent(event: Int, eventPath: String?) {
                    if (eventPath != null) {
                        trySend("$event:$eventPath")
                    }
                }
            }
        }

        try {
            observer.startWatching()
        } catch (ignored: Exception) {}

        awaitClose {
            try {
                observer.stopWatching()
            } catch (ignored: Exception) {}
        }
    }.flowOn(Dispatchers.IO)
}


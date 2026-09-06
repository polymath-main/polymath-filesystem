package com.polymath.fs.js.runtime.modules

import android.content.Context
import android.os.SystemClock
import com.polymath.fs.core.RootShellHolder
import com.polymath.fs.js.runtime.PolymathJSProcessInterface
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File

class ProcessNativeModule(
    private val context: Context,
    private val shellHolder: RootShellHolder = RootShellHolder()
) : PolymathJSProcessInterface {

    private var currentWorkingDirectory: String = "/storage/emulated/0"
    private val startTime = SystemClock.elapsedRealtime()

    override fun getEnv(): String {
        return try {
            val env = System.getenv()
            val obj = JSONObject()
            env.forEach { (k, v) -> obj.put(k, v) }
            obj.put("PWD", currentWorkingDirectory)
            obj.put("HOME", "/storage/emulated/0")
            obj.put("TMPDIR", context.cacheDir.absolutePath)
            obj.put("ANDROID_APP_DIR", context.applicationInfo.dataDir)
            obj.toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    override fun getPid(): Int {
        return android.os.Process.myPid()
    }

    override fun getCwd(): String {
        return currentWorkingDirectory
    }

    override fun setCwd(path: String): Boolean {
        val target = File(path)
        if (target.exists() && target.isDirectory) {
            currentWorkingDirectory = target.canonicalPath
            return true
        }
        return false
    }

    override fun execSync(command: String): String {
        return try {
            val result = runBlocking { shellHolder.execute("cd '$currentWorkingDirectory' && $command") }
            val obj = JSONObject().apply {
                put("stdout", result.output.joinToString("\n"))
                put("stderr", result.error.joinToString("\n"))
                put("code", result.code)
                put("success", result.isSuccess)
            }
            obj.toString()
        } catch (e: Exception) {
            val obj = JSONObject().apply {
                put("stdout", "")
                put("stderr", e.message ?: "Execution failed")
                put("code", -1)
                put("success", false)
            }
            obj.toString()
        }
    }

    override fun getUptime(): Double {
        return (SystemClock.elapsedRealtime() - startTime) / 1000.0
    }
}

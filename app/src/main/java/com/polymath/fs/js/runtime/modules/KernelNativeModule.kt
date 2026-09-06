package com.polymath.fs.js.runtime.modules

import com.polymath.fs.core.KernelEngineController
import com.polymath.fs.core.RootShellHolder
import com.polymath.fs.js.runtime.PolymathJSKernelInterface
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class KernelNativeModule(
    private val kernelController: KernelEngineController = KernelEngineController(),
    private val shellHolder: RootShellHolder = RootShellHolder()
) : PolymathJSKernelInterface {

    override fun getKernelReport(): String {
        return try {
            val t = runBlocking { kernelController.getKernelTelemetry() }
            val json = JSONObject().apply {
                put("release", t.kernelRelease)
                put("version", t.kernelVersion)
                put("uptime", t.uptimeSeconds)
                put("loadAverages", JSONArray(listOf(t.loadAverages.first, t.loadAverages.second, t.loadAverages.third)))
                put("processCount", t.processCount)
                put("isSeLinuxEnforcing", t.isSeLinuxEnforcing)
                put("zramCompAlgorithm", t.zramCompAlgorithm)
                put("cpu", JSONObject().apply {
                    put("arch", t.cpuInfo.architecture)
                    put("cores", t.cpuInfo.cores)
                    put("governor", t.cpuInfo.governor)
                    put("curFreqMhz", t.cpuInfo.curFreqMhz)
                    put("maxFreqMhz", t.cpuInfo.maxFreqMhz)
                    put("minFreqMhz", t.cpuInfo.minFreqMhz)
                })
                put("memory", JSONObject().apply {
                    put("totalKb", t.memInfo.totalMemKb)
                    put("freeKb", t.memInfo.freeMemKb)
                    put("availableKb", t.memInfo.availableMemKb)
                    put("cachedKb", t.memInfo.cachedKb)
                    put("buffersKb", t.memInfo.buffersKb)
                })
            }
            json.toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "Failed to query kernel").toString()
        }
    }

    override fun readVirtualFile(path: String): String {
        return try {
            val f = File(path)
            if (f.exists() && f.canRead()) {
                f.readText()
            } else {
                val res = runBlocking { shellHolder.execute("cat '$path' 2>/dev/null") }
                if (res.isSuccess) res.output.joinToString("\n") else ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    override fun setCpuGovernor(governor: String): Boolean {
        return try {
            val cores = Runtime.getRuntime().availableProcessors()
            val cmd = buildString {
                for (i in 0 until cores) {
                    append("echo '$governor' > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor 2>/dev/null; ")
                }
            }
            val res = runBlocking { shellHolder.execute(cmd) }
            res.isSuccess
        } catch (e: Exception) {
            false
        }
    }
}

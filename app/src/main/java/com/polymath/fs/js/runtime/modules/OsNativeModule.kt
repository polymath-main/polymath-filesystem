package com.polymath.fs.js.runtime.modules

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.polymath.fs.core.KernelEngineController
import com.polymath.fs.js.runtime.PolymathJSOSInterface
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.net.NetworkInterface

class OsNativeModule(
    private val context: Context,
    private val kernelController: KernelEngineController = KernelEngineController()
) : PolymathJSOSInterface {

    override fun getArch(): String {
        return System.getProperty("os.arch") ?: "aarch64"
    }

    override fun getPlatform(): String {
        return "android"
    }

    override fun getHostname(): String {
        return Build.DEVICE ?: "localhost"
    }

    override fun getCpus(): String {
        return try {
            val telemetry = runBlocking { kernelController.getKernelTelemetry() }
            val cpuInfo = telemetry.cpuInfo
            val arr = JSONArray()
            for (i in 0 until cpuInfo.cores) {
                val obj = JSONObject().apply {
                    put("model", "Linux Processor Core $i")
                    val freq = cpuInfo.scalingCurFreqList.getOrNull(i) ?: cpuInfo.curFreqMhz
                    put("speed", freq)
                    put("governor", cpuInfo.governor)
                }
                arr.put(obj)
            }
            arr.toString()
        } catch (e: Exception) {
            "[]"
        }
    }

    override fun getTotalMem(): Double {
        return try {
            val telemetry = runBlocking { kernelController.getKernelTelemetry() }
            telemetry.memInfo.totalMemKb * 1024.0
        } catch (e: Exception) {
            Runtime.getRuntime().totalMemory().toDouble()
        }
    }

    override fun getFreeMem(): Double {
        return try {
            val telemetry = runBlocking { kernelController.getKernelTelemetry() }
            telemetry.memInfo.availableMemKb * 1024.0
        } catch (e: Exception) {
            Runtime.getRuntime().freeMemory().toDouble()
        }
    }

    override fun getUptime(): Double {
        return (SystemClock.elapsedRealtime() / 1000.0)
    }

    override fun getLoadAvg(): String {
        return try {
            val telemetry = runBlocking { kernelController.getKernelTelemetry() }
            val (l1, l5, l15) = telemetry.loadAverages
            JSONArray(listOf(l1, l5, l15)).toString()
        } catch (e: Exception) {
            "[0.0, 0.0, 0.0]"
        }
    }

    override fun getHomeDir(): String {
        return "/storage/emulated/0"
    }

    override fun getTmpDir(): String {
        return context.cacheDir.absolutePath
    }

    override fun getNetworkInterfaces(): String {
        return try {
            val root = JSONObject()
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val list = JSONArray()
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    val obj = JSONObject().apply {
                        put("address", addr.hostAddress)
                        put("family", if (addr.address.size == 4) "IPv4" else "IPv6")
                        put("internal", addr.isLoopbackAddress)
                    }
                    list.put(obj)
                }
                root.put(iface.name, list)
            }
            root.toString()
        } catch (e: Exception) {
            "{}"
        }
    }
}

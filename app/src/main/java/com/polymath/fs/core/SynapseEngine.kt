package com.polymath.fs.core

import android.content.Context
import com.polymath.fs.data.repository.IFileSystemRepository
import com.polymath.fs.models.FileNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SynapseEngine @Inject constructor(
    private val context: Context,
    private val repository: IFileSystemRepository
) {
    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null
    val port = 8888

    fun startSynapse() {
        if (serverJob?.isActive == true) return
        
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(port)
                while (true) {
                    val client = serverSocket?.accept() ?: break
                    launch { handleClient(client) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopSynapse() {
        serverJob?.cancel()
        serverSocket?.close()
        serverSocket = null
    }

    private suspend fun handleClient(client: Socket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = PrintWriter(client.getOutputStream(), true)
            
            val requestLine = reader.readLine() ?: return@withContext
            if (requestLine.startsWith("GET /list")) {
                // Parse path: GET /list?path=/sdcard HTTP/1.1
                val path = requestLine.substringAfter("path=").substringBefore(" ")
                val decodedPath = java.net.URLDecoder.decode(path, "UTF-8")
                
                val files = kotlinx.coroutines.runBlocking { repository.listDir(decodedPath) }
                val jsonArray = JSONArray()
                files.forEach { fileNode ->
                    val obj = JSONObject()
                    obj.put("name", fileNode.name)
                    obj.put("path", fileNode.path)
                    obj.put("isDirectory", fileNode.isDirectory)
                    obj.put("size", fileNode.size)
                    obj.put("lastModified", fileNode.lastModified)
                    jsonArray.put(obj)
                }
                
                val responseBody = jsonArray.toString()
                writer.println("HTTP/1.1 200 OK")
                writer.println("Content-Type: application/json")
                writer.println("Content-Length: ${responseBody.length}")
                writer.println("Access-Control-Allow-Origin: *")
                writer.println("")
                writer.println(responseBody)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            client.close()
        }
    }

    suspend fun fetchRemoteDirectory(ip: String, path: String): List<FileNode> = withContext(Dispatchers.IO) {
        val remoteFiles = mutableListOf<FileNode>()
        try {
            val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
            val socket = Socket(ip, port)
            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            
            writer.println("GET /list?path=$encodedPath HTTP/1.1")
            writer.println("Host: $ip")
            writer.println("")
            
            var line: String?
            var isBody = false
            val bodyBuilder = java.lang.StringBuilder()
            
            while (reader.readLine().also { line = it } != null) {
                if (isBody) {
                    bodyBuilder.append(line)
                } else if (line!!.isEmpty()) {
                    isBody = true
                }
            }
            socket.close()
            
            val jsonArray = JSONArray(bodyBuilder.toString())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                remoteFiles.add(
                    com.polymath.fs.models.FileNode.NetworkFile(
                        name = obj.getString("name"),
                        path = obj.getString("path"),
                        isDirectory = obj.getBoolean("isDirectory"),
                        size = obj.getLong("size"),
                        lastModified = obj.getLong("lastModified"),
                        url = "synapse://$ip${obj.getString("path")}"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext remoteFiles
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in java.util.Collections.list(interfaces)) {
                val addrs = intf.inetAddresses
                for (addr in java.util.Collections.list(addrs)) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }
}

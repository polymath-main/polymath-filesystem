package com.polymath.fs.core

import android.content.Context
import com.polymath.fs.data.repository.IFileSystemRepository
import com.polymath.fs.models.FileNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SynapseEngine @Inject constructor(
    private val context: Context,
    private val repository: IFileSystemRepository
) {
    data class SynapseClip(
        val content: String,
        val senderIp: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null
    val port = 8888

    val isRunning: Boolean
        get() = serverSocket != null && !serverSocket!!.isClosed && serverJob?.isActive == true

    private val _serverState = MutableStateFlow(false)
    val serverState: StateFlow<Boolean> = _serverState.asStateFlow()

    private val _latestClip = MutableStateFlow<SynapseClip?>(null)
    val latestClip: StateFlow<SynapseClip?> = _latestClip.asStateFlow()

    private val _clipHistory = MutableStateFlow<List<SynapseClip>>(emptyList())
    val clipHistory: StateFlow<List<SynapseClip>> = _clipHistory.asStateFlow()

    fun startSynapse(customPort: Int = port) {
        if (serverJob?.isActive == true && serverSocket?.isClosed == false) {
            _serverState.value = true
            return
        }

        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(customPort)
                _serverState.value = true
                while (true) {
                    val client = serverSocket?.accept() ?: break
                    launch { handleClient(client) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _serverState.value = false
            }
        }
    }

    fun stopSynapse() {
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob = null
        _serverState.value = false
    }

    private suspend fun handleClient(client: Socket) = withContext(Dispatchers.IO) {
        try {
            val inputStream = client.getInputStream()
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val writer = PrintWriter(client.getOutputStream(), true)

            val requestLine = reader.readLine() ?: return@withContext

            if (requestLine.startsWith("GET /list")) {
                // Parse path: GET /list?path=/sdcard HTTP/1.1
                val rawPath = if (requestLine.contains("path=")) {
                    requestLine.substringAfter("path=").substringBefore(" ")
                } else {
                    "/storage/emulated/0"
                }
                val decodedPath = URLDecoder.decode(rawPath, "UTF-8")

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

                val responseBytes = jsonArray.toString().toByteArray(Charsets.UTF_8)
                writer.print("HTTP/1.1 200 OK\r\n")
                writer.print("Content-Type: application/json; charset=utf-8\r\n")
                writer.print("Content-Length: ${responseBytes.size}\r\n")
                writer.print("Access-Control-Allow-Origin: *\r\n")
                writer.print("\r\n")
                writer.flush()
                client.getOutputStream().write(responseBytes)
                client.getOutputStream().flush()

            } else if (requestLine.startsWith("GET /download") || requestLine.startsWith("GET /file")) {
                val rawPath = requestLine.substringAfter("path=").substringBefore(" ")
                val decodedPath = URLDecoder.decode(rawPath, "UTF-8")
                val localFile = File(decodedPath)

                if (localFile.exists() && localFile.isFile && localFile.canRead()) {
                    val length = localFile.length()
                    writer.print("HTTP/1.1 200 OK\r\n")
                    writer.print("Content-Type: application/octet-stream\r\n")
                    writer.print("Content-Length: $length\r\n")
                    writer.print("Content-Disposition: attachment; filename=\"${localFile.name}\"\r\n")
                    writer.print("Access-Control-Allow-Origin: *\r\n")
                    writer.print("\r\n")
                    writer.flush()

                    val outStream = client.getOutputStream()
                    localFile.inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            outStream.write(buffer, 0, bytesRead)
                        }
                        outStream.flush()
                    }
                } else {
                    try {
                        val input = repository.openRead(decodedPath)
                        writer.print("HTTP/1.1 200 OK\r\n")
                        writer.print("Content-Type: application/octet-stream\r\n")
                        writer.print("Access-Control-Allow-Origin: *\r\n")
                        writer.print("\r\n")
                        writer.flush()

                        val outStream = client.getOutputStream()
                        input.use { inStream ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                                outStream.write(buffer, 0, bytesRead)
                            }
                            outStream.flush()
                        }
                    } catch (e: Exception) {
                        writer.print("HTTP/1.1 404 Not Found\r\n")
                        writer.print("Content-Length: 0\r\n")
                        writer.print("\r\n")
                        writer.flush()
                    }
                }

            } else if (requestLine.startsWith("POST /clip")) {
                var contentLength = 0
                var headerLine = reader.readLine()
                while (!headerLine.isNullOrEmpty()) {
                    if (headerLine.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = headerLine.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                    headerLine = reader.readLine()
                }

                val clipContent = if (contentLength > 0) {
                    val bodyChars = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val read = reader.read(bodyChars, readTotal, contentLength - readTotal)
                        if (read == -1) break
                        readTotal += read
                    }
                    String(bodyChars, 0, readTotal)
                } else {
                    ""
                }

                val senderIp = client.inetAddress.hostAddress ?: "mesh-peer"
                val clip = SynapseClip(clipContent, senderIp)
                _latestClip.value = clip
                val current = _clipHistory.value.toMutableList()
                current.add(0, clip)
                if (current.size > 50) current.removeAt(current.lastIndex)
                _clipHistory.value = current

                writer.print("HTTP/1.1 200 OK\r\n")
                writer.print("Content-Type: text/plain; charset=utf-8\r\n")
                writer.print("Content-Length: 2\r\n")
                writer.print("\r\n")
                writer.print("OK\r\n")
                writer.flush()

            } else if (requestLine.startsWith("GET /clip")) {
                val clip = _latestClip.value
                val obj = JSONObject()
                obj.put("content", clip?.content ?: "")
                obj.put("senderIp", clip?.senderIp ?: "")
                obj.put("timestamp", clip?.timestamp ?: 0L)
                val responseBytes = obj.toString().toByteArray(Charsets.UTF_8)

                writer.print("HTTP/1.1 200 OK\r\n")
                writer.print("Content-Type: application/json; charset=utf-8\r\n")
                writer.print("Content-Length: ${responseBytes.size}\r\n")
                writer.print("Access-Control-Allow-Origin: *\r\n")
                writer.print("\r\n")
                writer.flush()
                client.getOutputStream().write(responseBytes)
                client.getOutputStream().flush()

            } else {
                writer.print("HTTP/1.1 400 Bad Request\r\n")
                writer.print("Content-Length: 0\r\n")
                writer.print("\r\n")
                writer.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                client.close()
            } catch (_: Exception) {}
        }
    }

    suspend fun fetchRemoteDirectory(ip: String, path: String, targetPort: Int = port): List<FileNode> = withContext(Dispatchers.IO) {
        val remoteFiles = mutableListOf<FileNode>()
        try {
            val encodedPath = URLEncoder.encode(path, "UTF-8")
            val socket = Socket(ip, targetPort)
            socket.soTimeout = 8000
            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            writer.print("GET /list?path=$encodedPath HTTP/1.1\r\n")
            writer.print("Host: $ip\r\n")
            writer.print("Connection: close\r\n")
            writer.print("\r\n")
            writer.flush()

            var line: String?
            var isBody = false
            val bodyBuilder = StringBuilder()

            while (reader.readLine().also { line = it } != null) {
                if (isBody) {
                    bodyBuilder.append(line)
                } else if (line.isNullOrEmpty()) {
                    isBody = true
                }
            }
            socket.close()

            val jsonArray = JSONArray(bodyBuilder.toString())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                remoteFiles.add(
                    FileNode.NetworkFile(
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

    suspend fun downloadRemoteFile(
        ip: String,
        remotePath: String,
        destFile: File,
        targetPort: Int = port,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val encodedPath = URLEncoder.encode(remotePath, "UTF-8")
            val socket = Socket(ip, targetPort)
            socket.soTimeout = 15000
            val writer = PrintWriter(socket.getOutputStream(), true)
            val rawIn = BufferedInputStream(socket.getInputStream())

            writer.print("GET /download?path=$encodedPath HTTP/1.1\r\n")
            writer.print("Host: $ip\r\n")
            writer.print("Connection: close\r\n")
            writer.print("\r\n")
            writer.flush()

            // Parse HTTP header
            val headerBuilder = StringBuilder()
            var prevByte = -1
            var prevPrevByte = -1
            var prev3Byte = -1

            while (true) {
                val b = rawIn.read()
                if (b == -1) break
                headerBuilder.append(b.toChar())
                if (prev3Byte == '\r'.code && prevPrevByte == '\n'.code && prevByte == '\r'.code && b == '\n'.code) {
                    break
                }
                prev3Byte = prevPrevByte
                prevPrevByte = prevByte
                prevByte = b
            }

            val headerText = headerBuilder.toString()
            if (!headerText.startsWith("HTTP/1.1 200") && !headerText.startsWith("HTTP/1.0 200")) {
                socket.close()
                return@withContext false
            }

            var contentLength: Long = -1L
            for (hLine in headerText.split("\r\n")) {
                if (hLine.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = hLine.substringAfter(":").trim().toLongOrNull() ?: -1L
                }
            }

            destFile.parentFile?.mkdirs()
            val tempFile = File(destFile.parentFile, "${destFile.name}.part_${System.currentTimeMillis()}")
            val out = BufferedOutputStream(FileOutputStream(tempFile))

            val buffer = ByteArray(64 * 1024)
            var downloaded: Long = 0
            var bytesRead: Int

            while (rawIn.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
                downloaded += bytesRead
                onProgress?.invoke(downloaded, contentLength)
            }

            out.flush()
            out.close()
            socket.close()

            if (tempFile.exists()) {
                if (destFile.exists()) destFile.delete()
                return@withContext tempFile.renameTo(destFile)
            }
            return@withContext false
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun sendClip(ip: String, text: String, targetPort: Int = port): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = Socket(ip, targetPort)
            socket.soTimeout = 6000
            val bytes = text.toByteArray(Charsets.UTF_8)
            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            writer.print("POST /clip HTTP/1.1\r\n")
            writer.print("Host: $ip\r\n")
            writer.print("Content-Type: text/plain; charset=utf-8\r\n")
            writer.print("Content-Length: ${bytes.size}\r\n")
            writer.print("Connection: close\r\n")
            writer.print("\r\n")
            writer.flush()

            socket.getOutputStream().write(bytes)
            socket.getOutputStream().flush()

            val statusLine = reader.readLine()
            socket.close()

            val success = statusLine?.startsWith("HTTP/1.1 200") == true
            if (success) {
                val sentClip = SynapseClip(text, "me -> $ip")
                _latestClip.value = sentClip
                val current = _clipHistory.value.toMutableList()
                current.add(0, sentClip)
                if (current.size > 50) current.removeAt(current.lastIndex)
                _clipHistory.value = current
            }
            return@withContext success
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun fetchRemoteClip(ip: String, targetPort: Int = port): SynapseClip? = withContext(Dispatchers.IO) {
        try {
            val socket = Socket(ip, targetPort)
            socket.soTimeout = 6000
            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            writer.print("GET /clip HTTP/1.1\r\n")
            writer.print("Host: $ip\r\n")
            writer.print("Connection: close\r\n")
            writer.print("\r\n")
            writer.flush()

            var line: String?
            var isBody = false
            val bodyBuilder = StringBuilder()

            while (reader.readLine().also { line = it } != null) {
                if (isBody) {
                    bodyBuilder.append(line)
                } else if (line.isNullOrEmpty()) {
                    isBody = true
                }
            }
            socket.close()

            val obj = JSONObject(bodyBuilder.toString())
            val clipText = obj.optString("content", "")
            val senderIp = obj.optString("senderIp", ip)
            val timestamp = obj.optLong("timestamp", System.currentTimeMillis())

            if (clipText.isNotEmpty()) {
                val clip = SynapseClip(clipText, senderIp, timestamp)
                _latestClip.value = clip
                return@withContext clip
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
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

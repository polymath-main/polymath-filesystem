package com.polymath.fs.js.runtime.modules

import com.polymath.fs.js.runtime.PolymathJSHttpInterface
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class HttpNativeModule : PolymathJSHttpInterface {

    override fun fetchSync(url: String, method: String, headersJson: String, body: String): String {
        var connection: HttpURLConnection? = null
        return try {
            val targetUrl = URL(url)
            connection = targetUrl.openConnection() as HttpURLConnection
            connection.requestMethod = method.uppercase()
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.instanceFollowRedirects = true

            // Apply custom headers
            if (headersJson.isNotBlank() && headersJson != "{}") {
                try {
                    val headersObj = JSONObject(headersJson)
                    val keys = headersObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        connection.setRequestProperty(key, headersObj.getString(key))
                    }
                } catch (ignored: Exception) {}
            }

            // Write body for methods that allow it
            val hasBody = (method.equals("POST", ignoreCase = true) ||
                    method.equals("PUT", ignoreCase = true) ||
                    method.equals("PATCH", ignoreCase = true)) && body.isNotEmpty()

            if (hasBody) {
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                    writer.write(body)
                    writer.flush()
                }
            }

            val statusCode = connection.responseCode
            val statusText = connection.responseMessage ?: ""
            val isSuccess = statusCode in 200..299

            val inputStream = if (isSuccess) connection.inputStream else connection.errorStream
            val responseBody = if (inputStream != null) {
                BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { it.readText() }
            } else ""

            val respHeaders = JSONObject()
            connection.headerFields?.forEach { (k, v) ->
                if (k != null) {
                    respHeaders.put(k, v.joinToString(", "))
                }
            }

            val result = JSONObject().apply {
                put("status", statusCode)
                put("statusText", statusText)
                put("ok", isSuccess)
                put("headers", respHeaders)
                put("body", responseBody)
            }
            result.toString()
        } catch (e: Exception) {
            val errorResult = JSONObject().apply {
                put("status", 0)
                put("statusText", e.message ?: "Network error")
                put("ok", false)
                put("headers", JSONObject())
                put("body", "")
                put("error", e.javaClass.simpleName + ": " + e.message)
            }
            errorResult.toString()
        } finally {
            connection?.disconnect()
        }
    }
}

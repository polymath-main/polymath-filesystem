package com.polymath.fs.ui

import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatActivity
import com.polymath.fs.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.ViewGroup
import android.widget.FrameLayout
import java.io.File
import android.os.Environment

class JsDashboardActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup layout programmatically
        val layout = FrameLayout(this)
        layout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        
        layout.addView(webView)
        setContentView(layout)

        setupWebView()
        webView.loadUrl("file:///android_asset/dashboard.html")
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Initialize metrics immediately upon loading
                syncData()
            }
        }

        webView.addJavascriptInterface(DashboardInterface(), "AndroidInterface")
    }
    
    private fun syncData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get real storage metrics
                val internalStat = android.os.StatFs(Environment.getExternalStorageDirectory().path)
                val totalBytes = internalStat.totalBytes
                val freeBytes = internalStat.availableBytes
                val usedBytes = totalBytes - freeBytes
                
                val usedGb = (usedBytes / (1024 * 1024 * 1024)).toInt()
                val totalGb = (totalBytes / (1024 * 1024 * 1024)).toInt()
                
                // Get approx file count from somewhere, here we'll mock it based on total files in some dir or just random for demonstration
                val fileCount = countFilesInDir(Environment.getExternalStorageDirectory(), 0, 500)
                
                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript("updateMetrics($usedGb, $totalGb, $fileCount)", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun countFilesInDir(dir: File, currentDepth: Int, maxDepth: Int): Int {
        if (!dir.exists() || !dir.isDirectory || currentDepth > maxDepth) return 0
        var count = 0
        try {
            val files = dir.listFiles()
            if (files != null) {
                count += files.size
                for (file in files) {
                    if (file.isDirectory) {
                        count += countFilesInDir(file, currentDepth + 1, maxDepth)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignored
        }
        return count
    }

    inner class DashboardInterface {
        @JavascriptInterface
        fun requestSync() {
            syncData()
        }
    }
}

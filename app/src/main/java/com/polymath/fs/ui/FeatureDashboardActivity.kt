package com.polymath.fs.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.polymath.fs.core.SystemBarHelper
import com.polymath.fs.core.ThemeManager

class FeatureDashboardActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var featureId: String = "workbench"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        
        // Edge to edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        featureId = intent.getStringExtra("EXTRA_FEATURE_ID") ?: "workbench"

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            
            addJavascriptInterface(DashboardBridge(), "AndroidBridge")
        }

        setContentView(webView)

        // Handle insets so webview draws under status/nav bars but content is padded
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
            )
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        webView.loadUrl("file:///android_asset/feature_dashboard.html")
    }

    inner class DashboardBridge {
        @JavascriptInterface
        fun getFeatureId(): String {
            return featureId
        }

        @JavascriptInterface
        fun executePrimaryAction() {
            runOnUiThread {
                Toast.makeText(this@FeatureDashboardActivity, "Executing Protocol for $featureId...", Toast.LENGTH_SHORT).show()
                // Future expansion: Actually trigger intent engines or swarm connections here!
            }
        }
    }
}

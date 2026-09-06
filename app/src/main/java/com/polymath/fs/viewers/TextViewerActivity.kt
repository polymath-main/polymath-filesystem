package com.polymath.fs.viewers

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.polymath.fs.core.BaseDynamicActivity

import com.polymath.fs.R
import java.io.File

class TextViewerActivity : BaseDynamicActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_viewer)
        
        val webView: WebView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        
        val filePath = intent.getStringExtra("FILE_PATH")
        if (filePath != null) {
            val file = File(filePath)
            if (file.exists()) {
                val extension = file.extension.lowercase()
                val code = file.readText().replace("<", "&lt;").replace(">", "&gt;")
                
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0">
                        <link href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/themes/prism-tomorrow.min.css" rel="stylesheet" />
                        <link href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/plugins/line-numbers/prism-line-numbers.min.css" rel="stylesheet" />
                        <style>
                            body { margin: 0; background-color: #2d2d2d; padding: 8px; }
                            pre[class*="language-"].line-numbers { padding-left: 3.8em; }
                        </style>
                    </head>
                    <body class="line-numbers">
                        <pre><code class="language-$extension">$code</code></pre>
                        <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/prism.min.js"></script>
                        <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/plugins/autoloader/prism-autoloader.min.js"></script>
                        <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/plugins/line-numbers/prism-line-numbers.min.js"></script>
                    </body>
                    </html>
                """.trimIndent()
                
                webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            } else {
                webView.loadDataWithBaseURL(null, "<html><body style='color:white;background:#1E1E1E'>File does not exist.</body></html>", "text/html", "utf-8", null)
            }
        } else {
            webView.loadDataWithBaseURL(null, "<html><body style='color:white;background:#1E1E1E'>No file provided.</body></html>", "text/html", "utf-8", null)
        }
    }
}

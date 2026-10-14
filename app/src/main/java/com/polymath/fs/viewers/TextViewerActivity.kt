package com.polymath.fs.viewers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.polymath.fs.PolymathApp
import com.polymath.fs.R
import com.polymath.fs.core.BaseDynamicActivity
import com.polymath.fs.core.SystemBarHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TextViewerActivity : BaseDynamicActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var filePath: String? = null
    private var fileContent: String = ""
    private var fileName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_viewer)

        SystemBarHelper.updateSystemBarAppearance(this)

        toolbar = findViewById(R.id.toolbar)
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        webView.setBackgroundColor(android.graphics.Color.parseColor("#0b0f19"))

        filePath = intent.getStringExtra("path")
            ?: intent.getStringExtra("filePath")
            ?: intent.getStringExtra("FILE_PATH")
            ?: intent.dataString

        if (!filePath.isNullOrBlank()) {
            fileName = filePath!!.substringAfterLast('/').ifEmpty { "Document" }
            toolbar.title = fileName
            toolbar.subtitle = filePath
            loadDocument(filePath!!)
        } else {
            toolbar.title = "Document"
            webView.loadDataWithBaseURL(null, generateEmptyHtml("No document path provided."), "text/html", "utf-8", null)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Edit").apply {
            setIcon(R.drawable.ic_edit_note)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        menu.add(0, 2, 1, "Copy All").apply {
            setIcon(R.drawable.ic_copy)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        menu.add(0, 3, 2, "Share").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> {
                // Open in full editor
                if (!filePath.isNullOrBlank()) {
                    val intent = Intent(this, EditorActivity::class.java).apply {
                        putExtra("FILE_PATH", filePath)
                        putExtra("path", filePath)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Cannot edit unsaved file", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            2 -> {
                if (fileContent.isNotEmpty()) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(fileName, fileContent))
                    Toast.makeText(this, "Copied ${fileContent.length} characters", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            3 -> {
                if (fileContent.isNotEmpty()) {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, fileContent)
                        type = "text/plain"
                    }
                    startActivity(Intent.createChooser(sendIntent, "Share Document"))
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadDocument(targetPath: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val content = try {
                when {
                    targetPath.startsWith("content://") -> {
                        contentResolver.openInputStream(Uri.parse(targetPath))?.use {
                            it.bufferedReader().readText()
                        } ?: "Unable to read SAF content stream."
                    }
                    targetPath.startsWith("sftp://") -> {
                        val app = application as? PolymathApp
                        if (app != null) {
                            val uri = com.polymath.fs.domain.vfs.VfsUri.parse(targetPath)
                            val isStream = app.vfsRegistry.providerFor(uri).openRead(uri)
                            isStream.bufferedReader().readText()
                        } else {
                            "VFS SFTP stream unavailable."
                        }
                    }
                    else -> {
                        val f = File(targetPath)
                        if (f.exists()) f.readText() else "File not found: $targetPath"
                    }
                }
            } catch (e: Exception) {
                "Error loading document: ${e.message}"
            }

            fileContent = content
            val ext = targetPath.substringAfterLast('.', "").lowercase()
            val renderedHtml = if (ext == "md" || ext == "markdown") {
                renderMarkdownToHtml(fileName, content)
            } else {
                renderCodeToHtml(fileName, content, ext)
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                webView.loadDataWithBaseURL(null, renderedHtml, "text/html", "utf-8", null)
            }
        }
    }

    private fun renderMarkdownToHtml(title: String, markdown: String): String {
        // Fast offline Markdown to HTML converter
        val lines = markdown.split("\n")
        val sb = StringBuilder()
        var inCodeBlock = false
        var inList = false
        var inNumberedList = false

        for (rawLine in lines) {
            val line = rawLine.trimEnd()

            if (line.startsWith("```")) {
                if (inCodeBlock) {
                    sb.append("</code></pre>\n")
                    inCodeBlock = false
                } else {
                    if (inList) { sb.append("</ul>\n"); inList = false }
                    if (inNumberedList) { sb.append("</ol>\n"); inNumberedList = false }
                    val lang = line.removePrefix("```").trim()
                    sb.append("<pre><code class=\"lang-$lang\">")
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                sb.append(escapeHtml(line)).append("\n")
                continue
            }

            // Headers
            if (line.startsWith("# ")) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<h1>").append(formatInline(line.substring(2))).append("</h1>\n")
            } else if (line.startsWith("## ")) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<h2>").append(formatInline(line.substring(3))).append("</h2>\n")
            } else if (line.startsWith("### ")) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<h3>").append(formatInline(line.substring(4))).append("</h3>\n")
            } else if (line.startsWith("#### ")) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<h4>").append(formatInline(line.substring(5))).append("</h4>\n")
            } else if (line.startsWith("---") || line.startsWith("***") || line.startsWith("___")) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<hr/>\n")
            } else if (line.startsWith("> ")) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<blockquote>").append(formatInline(line.substring(2))).append("</blockquote>\n")
            } else if (line.startsWith("- [ ] ") || line.startsWith("* [ ] ")) {
                if (!inList) { sb.append("<ul class=\"task-list\">\n"); inList = true }
                sb.append("<li><input type=\"checkbox\" disabled/> ").append(formatInline(line.substring(6))).append("</li>\n")
            } else if (line.startsWith("- [x] ") || line.startsWith("* [x] ") || line.startsWith("- [X] ")) {
                if (!inList) { sb.append("<ul class=\"task-list\">\n"); inList = true }
                sb.append("<li><input type=\"checkbox\" checked disabled/> ").append(formatInline(line.substring(6))).append("</li>\n")
            } else if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")) {
                if (!inList) { sb.append("<ul>\n"); inList = true }
                sb.append("<li>").append(formatInline(line.substring(2))).append("</li>\n")
            } else if (Regex("^\\d+\\.\\s+").containsMatchIn(line)) {
                if (!inNumberedList) { sb.append("<ol>\n"); inNumberedList = true }
                val content = line.replaceFirst(Regex("^\\d+\\.\\s+"), "")
                sb.append("<li>").append(formatInline(content)).append("</li>\n")
            } else if (line.isBlank()) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                if (inNumberedList) { sb.append("</ol>\n"); inNumberedList = false }
                sb.append("<br/>\n")
            } else {
                if (inList) { sb.append("</ul>\n"); inList = false }
                if (inNumberedList) { sb.append("</ol>\n"); inNumberedList = false }
                sb.append("<p>").append(formatInline(line)).append("</p>\n")
            }
        }

        if (inList) sb.append("</ul>\n")
        if (inNumberedList) sb.append("</ol>\n")
        if (inCodeBlock) sb.append("</code></pre>\n")

        return wrapHtml(title, sb.toString())
    }

    private fun renderCodeToHtml(title: String, code: String, ext: String): String {
        val lines = code.split("\n")
        val sb = StringBuilder()
        sb.append("<div class=\"code-container\"><table class=\"code-table\">")
        for ((idx, line) in lines.withIndex()) {
            val lineNum = idx + 1
            sb.append("<tr>")
            sb.append("<td class=\"line-num\">").append(lineNum).append("</td>")
            sb.append("<td class=\"line-content\"><pre>").append(escapeHtml(line)).append("</pre></td>")
            sb.append("</tr>")
        }
        sb.append("</table></div>")
        return wrapHtml(title, sb.toString())
    }

    private fun formatInline(text: String): String {
        var res = escapeHtml(text)
        // Inline code `code`
        res = res.replace(Regex("`([^`]+)`"), "<code>$1</code>")
        // Bold **text**
        res = res.replace(Regex("\\*\\*([^*]+)\\*\\*"), "<strong>$1</strong>")
        // Italic *text*
        res = res.replace(Regex("\\*([^*]+)\\*"), "<em>$1</em>")
        // Strikethrough ~~text~~
        res = res.replace(Regex("~~([^~]+)~~"), "<del>$1</del>")
        // Links [text](url)
        res = res.replace(Regex("\\[([^\\]]+)\\]\\(([^\\)]+)\\)"), "<a href=\"$2\">$1</a>")
        return res
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun generateEmptyHtml(message: String): String {
        return wrapHtml("Empty", "<div style=\"text-align:center;padding:40px;color:#94a3b8;\"><h3>$message</h3></div>")
    }

    private fun wrapHtml(title: String, bodyContent: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
                <style>
                    * { box-sizing: border-box; }
                    body {
                        margin: 0;
                        padding: 18px;
                        background-color: #0b0f19;
                        color: #f1f5f9;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        font-size: 15px;
                        line-height: 1.6;
                        word-wrap: break-word;
                    }
                    h1, h2, h3, h4 { color: #38bdf8; font-weight: 700; margin-top: 1.2em; margin-bottom: 0.4em; }
                    h1 { font-size: 22px; border-bottom: 1px solid #1e293b; padding-bottom: 8px; }
                    h2 { font-size: 19px; border-bottom: 1px solid #1e293b; padding-bottom: 6px; }
                    h3 { font-size: 17px; }
                    h4 { font-size: 15px; }
                    p { margin: 0.6em 0; }
                    a { color: #38bdf8; text-decoration: none; }
                    a:hover { text-decoration: underline; }
                    hr { border: none; border-top: 1px solid #334155; margin: 20px 0; }
                    blockquote {
                        margin: 12px 0;
                        padding: 8px 16px;
                        border-left: 4px solid #38bdf8;
                        background: #0f172a;
                        color: #cbd5e1;
                        border-radius: 0 8px 8px 0;
                    }
                    code {
                        background: #1e293b;
                        color: #38bdf8;
                        padding: 2px 6px;
                        border-radius: 4px;
                        font-family: monospace;
                        font-size: 13px;
                    }
                    pre {
                        background: #0f172a;
                        border: 1px solid #1e293b;
                        border-radius: 8px;
                        padding: 12px;
                        overflow-x: auto;
                        margin: 12px 0;
                    }
                    pre code {
                        background: transparent;
                        color: #e2e8f0;
                        padding: 0;
                        font-size: 13px;
                    }
                    ul, ol { padding-left: 24px; margin: 8px 0; }
                    li { margin: 4px 0; }
                    .task-list { list-style: none; padding-left: 4px; }
                    .task-list li { display: flex; align-items: center; gap: 8px; }
                    .code-container {
                        background: #0f172a;
                        border-radius: 8px;
                        overflow-x: auto;
                        border: 1px solid #1e293b;
                    }
                    .code-table { width: 100%; border-collapse: collapse; font-family: monospace; font-size: 13px; }
                    .line-num {
                        width: 40px;
                        text-align: right;
                        padding: 2px 10px 2px 4px;
                        color: #475569;
                        border-right: 1px solid #1e293b;
                        user-select: none;
                    }
                    .line-content { padding: 2px 8px; }
                    .line-content pre { margin: 0; padding: 0; border: none; background: transparent; }
                </style>
            </head>
            <body>
                $bodyContent
            </body>
            </html>
        """.trimIndent()
    }
}

package com.polymath.fs.viewers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.fs.PolymathApp
import com.polymath.fs.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditorActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var etCode: EditText
    private lateinit var tvLineNumbers: TextView
    private lateinit var tvConsoleOutput: TextView
    private lateinit var tvConsoleCount: TextView
    private lateinit var consolePanel: LinearLayout
    private lateinit var scrollConsole: ScrollView

    private var currentFile: File? = null
    private var isModified = false
    private var indentString = "  " // 2 spaces default
    private val consoleLogs = mutableListOf<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val highlightHandler = Handler(Looper.getMainLooper())
    private var highlightRunnable: Runnable? = null
    private var isFormatting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        initViews()
        setupAccessoryBar()
        setupEditorWatcher()

        val path = intent.getStringExtra("path")
            ?: intent.getStringExtra("filePath")
            ?: intent.getStringExtra("FILE_PATH")

        if (!path.isNullOrBlank()) {
            currentFile = File(path)
            loadFile(currentFile!!)
        } else {
            toolbar.title = "Untitled.js"
            updateLineNumbers()
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        etCode = findViewById(R.id.et_code)
        tvLineNumbers = findViewById(R.id.tv_line_numbers)
        tvConsoleOutput = findViewById(R.id.tv_console_output)
        tvConsoleCount = findViewById(R.id.tv_console_count)
        consolePanel = findViewById(R.id.console_panel)
        scrollConsole = findViewById(R.id.scroll_console)

        findViewById<TextView>(R.id.btn_console_clear).setOnClickListener {
            clearConsole()
        }

        findViewById<TextView>(R.id.btn_console_copy).setOnClickListener {
            val fullLogs = consoleLogs.joinToString("\n")
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Polymath Console Logs", fullLogs))
            Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        var isConsoleExpanded = true
        val btnToggle = findViewById<TextView>(R.id.btn_console_toggle)
        btnToggle.setOnClickListener {
            isConsoleExpanded = !isConsoleExpanded
            if (isConsoleExpanded) {
                consolePanel.layoutParams.height = (180 * resources.displayMetrics.density).toInt()
                btnToggle.text = "▼"
            } else {
                consolePanel.layoutParams.height = (36 * resources.displayMetrics.density).toInt()
                btnToggle.text = "▲"
            }
            consolePanel.requestLayout()
        }
    }

    private fun setupAccessoryBar() {
        val symbols = mapOf(
            R.id.btn_sym_tab to indentString,
            R.id.btn_sym_brace_open to "{\n$indentString\n}",
            R.id.btn_sym_brace_close to "}",
            R.id.btn_sym_paren_open to "(",
            R.id.btn_sym_paren_close to ")",
            R.id.btn_sym_bracket_open to "[",
            R.id.btn_sym_bracket_close to "]",
            R.id.btn_sym_semi to ";",
            R.id.btn_sym_equal to " = ",
            R.id.btn_sym_quote to "\"\"",
            R.id.btn_sym_squote to "''",
            R.id.btn_sym_slash to "/",
            R.id.btn_sym_dollar to "$",
            R.id.btn_sym_dot to "."
        )

        for ((viewId, text) in symbols) {
            findViewById<TextView>(viewId)?.setOnClickListener {
                insertTextAtCursor(text)
            }
        }
    }

    private fun insertTextAtCursor(text: String) {
        val start = etCode.selectionStart.coerceAtLeast(0)
        val end = etCode.selectionEnd.coerceAtLeast(0)
        etCode.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), text, 0, text.length)
        val cursorTarget = if (text == "\"\"" || text == "''") start + 1 else start + text.length
        etCode.setSelection(cursorTarget.coerceAtMost(etCode.text.length))
    }

    private fun setupEditorWatcher() {
        etCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isFormatting) return
                isModified = true
                updateLineNumbers()
            }

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                highlightRunnable?.let { highlightHandler.removeCallbacks(it) }
                highlightRunnable = Runnable {
                    val ext = currentFile?.extension ?: "js"
                    isFormatting = true
                    CodeSyntaxHighlighter.highlight(s, ext)
                    isFormatting = false
                }
                highlightHandler.postDelayed(highlightRunnable!!, 300)
            }
        })
    }

    private fun updateLineNumbers() {
        val lineCount = etCode.lineCount.coerceAtLeast(1)
        val sb = StringBuilder()
        for (i in 1..lineCount) {
            sb.append(i).append("\n")
        }
        tvLineNumbers.text = sb.toString()
    }

    private fun loadFile(file: File) {
        try {
            val content = file.readText()
            isFormatting = true
            etCode.setText(content)
            isFormatting = false
            toolbar.title = file.name
            toolbar.subtitle = file.parent
            isModified = false
            updateLineNumbers()

            highlightHandler.postDelayed({
                isFormatting = true
                CodeSyntaxHighlighter.highlight(etCode.text, file.extension)
                isFormatting = false
            }, 100)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveFile(targetFile: File? = currentFile) {
        if (targetFile == null) {
            showSaveAsDialog()
            return
        }
        try {
            targetFile.writeText(etCode.text.toString())
            currentFile = targetFile
            isModified = false
            toolbar.title = targetFile.name
            toolbar.subtitle = targetFile.parent
            Toast.makeText(this, "Saved ${targetFile.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSaveAsDialog() {
        val input = EditText(this).apply {
            hint = "e.g. MyScript.js"
            currentFile?.let { setText(it.name) }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Save As")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    val parentDir = currentFile?.parentFile ?: File(filesDir, "scripts").apply { mkdirs() }
                    val newTarget = File(parentDir, name)
                    saveFile(newTarget)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNewFileDialog() {
        val input = EditText(this).apply {
            hint = "NewFileName.js"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Create New File")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    val dir = File(filesDir, "scripts").apply { mkdirs() }
                    val newFile = File(dir, name)
                    newFile.createNewFile()
                    currentFile = newFile
                    loadFile(newFile)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executeCurrentScript() {
        val scriptContent = etCode.text.toString()
        val scriptName = currentFile?.name ?: "EditorScript.js"

        appendConsoleLog("SYSTEM", "Starting execution of $scriptName...")
        val app = application as PolymathApp

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = app.jsBridge.executeScript(
                    script = scriptContent,
                    scriptName = scriptName,
                    onAlert = { alertTitle, alertMsg ->
                        runOnUiThread {
                            MaterialAlertDialogBuilder(this@EditorActivity)
                                .setTitle(if (alertTitle.isNotEmpty()) alertTitle else "Script Alert")
                                .setMessage(alertMsg)
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    },
                    onConsoleLog = { level, logMsg ->
                        runOnUiThread {
                            appendConsoleLog(level, logMsg)
                        }
                    }
                )

                withContext(Dispatchers.Main) {
                    appendConsoleLog("RETURN", result)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendConsoleLog("ERROR", e.message ?: "Script execution error")
                }
            }
        }
    }

    private fun appendConsoleLog(level: String, message: String) {
        val timestamp = timeFormat.format(Date())
        val formattedLine = "[$timestamp] $level: $message"
        consoleLogs.add(formattedLine)

        tvConsoleCount.text = "${consoleLogs.size} logs"
        tvConsoleOutput.text = consoleLogs.joinToString("\n")
        scrollConsole.post {
            scrollConsole.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun clearConsole() {
        consoleLogs.clear()
        tvConsoleCount.text = "0 logs"
        tvConsoleOutput.text = "Console cleared."
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                handleExit()
                true
            }
            R.id.action_run -> {
                executeCurrentScript()
                true
            }
            R.id.action_save -> {
                saveFile()
                true
            }
            R.id.action_new_file -> {
                showNewFileDialog()
                true
            }
            R.id.action_save_as -> {
                showSaveAsDialog()
                true
            }
            R.id.action_toggle_console -> {
                consolePanel.visibility = if (consolePanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                true
            }
            R.id.action_indent_2 -> {
                indentString = "  "
                Toast.makeText(this, "Indent set to 2 spaces", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_indent_4 -> {
                indentString = "    "
                Toast.makeText(this, "Indent set to 4 spaces", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun handleExit() {
        if (isModified) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Unsaved Changes")
                .setMessage("You have unsaved changes. Would you like to save before exiting?")
                .setPositiveButton("Save & Exit") { _, _ ->
                    saveFile()
                    finish()
                }
                .setNegativeButton("Discard") { _, _ ->
                    finish()
                }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            finish()
        }
    }

    override fun onBackPressed() {
        handleExit()
    }
}

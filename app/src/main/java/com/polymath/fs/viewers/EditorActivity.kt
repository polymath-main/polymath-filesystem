package com.polymath.fs.viewers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.polymath.fs.core.BaseDynamicActivity

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

class EditorActivity : BaseDynamicActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var etCode: EditText
    private lateinit var tvLineNumbers: TextView
    private lateinit var tvConsoleOutput: TextView
    private lateinit var tvConsoleCount: TextView
    private lateinit var consolePanel: LinearLayout
    private lateinit var scrollConsole: ScrollView

    // Search & Replace UI
    private lateinit var panelSearchReplace: LinearLayout
    private lateinit var etFind: EditText
    private lateinit var etReplace: EditText
    private lateinit var tvFindMatchCount: TextView
    private lateinit var btnFindPrev: TextView
    private lateinit var btnFindNext: TextView
    private lateinit var btnCloseSearch: TextView
    private lateinit var btnReplaceOne: TextView
    private lateinit var btnReplaceAll: TextView

    private val searchMatches = mutableListOf<Int>()
    private var currentMatchIndex = -1

    // Undo / Redo History
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()
    private var isUndoOrRedo = false
    private val maxHistorySize = 50

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

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExit()
            }
        })

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

        // Find & Replace Views
        panelSearchReplace = findViewById(R.id.panel_search_replace)
        etFind = findViewById(R.id.et_find)
        etReplace = findViewById(R.id.et_replace)
        tvFindMatchCount = findViewById(R.id.tv_find_match_count)
        btnFindPrev = findViewById(R.id.btn_find_prev)
        btnFindNext = findViewById(R.id.btn_find_next)
        btnCloseSearch = findViewById(R.id.btn_close_search)
        btnReplaceOne = findViewById(R.id.btn_replace_one)
        btnReplaceAll = findViewById(R.id.btn_replace_all)

        setupSearchReplaceLogic()

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
        findViewById<TextView>(R.id.btn_action_undo)?.setOnClickListener {
            performUndo()
        }
        findViewById<TextView>(R.id.btn_action_redo)?.setOnClickListener {
            performRedo()
        }
        findViewById<TextView>(R.id.btn_sym_comment)?.setOnClickListener {
            toggleCommentAtSelection()
        }

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

    private fun pushUndoSnapshot(text: String) {
        if (undoStack.isEmpty() || undoStack.last() != text) {
            undoStack.add(text)
            if (undoStack.size > maxHistorySize) {
                undoStack.removeAt(0)
            }
        }
    }

    private fun performUndo() {
        if (undoStack.isEmpty()) {
            Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show()
            return
        }
        val currentText = etCode.text.toString()
        redoStack.add(currentText)
        val prevText = undoStack.removeAt(undoStack.lastIndex)
        isUndoOrRedo = true
        isFormatting = true
        etCode.setText(prevText)
        etCode.setSelection(prevText.length.coerceAtMost(etCode.text.length))
        isFormatting = false
        isUndoOrRedo = false
        updateLineNumbers()
        highlightHandler.postDelayed({
            isFormatting = true
            CodeSyntaxHighlighter.highlight(etCode.text, currentFile?.extension ?: "js")
            isFormatting = false
        }, 100)
    }

    private fun performRedo() {
        if (redoStack.isEmpty()) {
            Toast.makeText(this, "Nothing to redo", Toast.LENGTH_SHORT).show()
            return
        }
        val currentText = etCode.text.toString()
        undoStack.add(currentText)
        val nextText = redoStack.removeAt(redoStack.lastIndex)
        isUndoOrRedo = true
        isFormatting = true
        etCode.setText(nextText)
        etCode.setSelection(nextText.length.coerceAtMost(etCode.text.length))
        isFormatting = false
        isUndoOrRedo = false
        updateLineNumbers()
        highlightHandler.postDelayed({
            isFormatting = true
            CodeSyntaxHighlighter.highlight(etCode.text, currentFile?.extension ?: "js")
            isFormatting = false
        }, 100)
    }

    private fun toggleCommentAtSelection() {
        val start = etCode.selectionStart.coerceAtLeast(0)
        val end = etCode.selectionEnd.coerceAtLeast(0)
        val text = etCode.text.toString()

        if (start == end) {
            // Comment current line
            val lineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
            val lineEnd = text.indexOf('\n', start).let { if (it == -1) text.length else it }
            val line = text.substring(lineStart, lineEnd)

            val newLine = if (line.trimStart().startsWith("//")) {
                line.replaceFirst("//", "")
            } else {
                "// $line"
            }
            etCode.text.replace(lineStart, lineEnd, newLine)
        } else {
            // Wrap selection in block comment or line comments
            val selected = text.substring(start, end)
            val replaced = if (selected.startsWith("/*") && selected.endsWith("*/")) {
                selected.removeSurrounding("/*", "*/").trim()
            } else {
                "/* $selected */"
            }
            etCode.text.replace(start, end, replaced)
        }
    }

    private fun setupSearchReplaceLogic() {
        btnCloseSearch.setOnClickListener {
            panelSearchReplace.visibility = View.GONE
            clearSearchHighlights()
        }

        etFind.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performSearch(s?.toString() ?: "")
            }
        })

        btnFindNext.setOnClickListener {
            navigateSearchMatch(1)
        }

        btnFindPrev.setOnClickListener {
            navigateSearchMatch(-1)
        }

        btnReplaceOne.setOnClickListener {
            val query = etFind.text.toString()
            val replacement = etReplace.text.toString()
            if (query.isNotEmpty() && searchMatches.isNotEmpty() && currentMatchIndex in searchMatches.indices) {
                val matchPos = searchMatches[currentMatchIndex]
                etCode.text.replace(matchPos, matchPos + query.length, replacement)
                performSearch(query)
            }
        }

        btnReplaceAll.setOnClickListener {
            val query = etFind.text.toString()
            val replacement = etReplace.text.toString()
            if (query.isNotEmpty()) {
                val original = etCode.text.toString()
                if (original.contains(query)) {
                    pushUndoSnapshot(original)
                    val replaced = original.replace(query, replacement)
                    isFormatting = true
                    etCode.setText(replaced)
                    isFormatting = false
                    performSearch(query)
                    Toast.makeText(this, "Replaced all occurrences", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun performSearch(query: String) {
        searchMatches.clear()
        currentMatchIndex = -1

        if (query.isEmpty()) {
            tvFindMatchCount.text = "0/0"
            clearSearchHighlights()
            return
        }

        val text = etCode.text.toString()
        var index = text.indexOf(query, 0, ignoreCase = true)
        while (index >= 0) {
            searchMatches.add(index)
            index = text.indexOf(query, index + query.length.coerceAtLeast(1), ignoreCase = true)
        }

        if (searchMatches.isNotEmpty()) {
            currentMatchIndex = 0
            tvFindMatchCount.text = "1/${searchMatches.size}"
            highlightCurrentMatch(query)
        } else {
            tvFindMatchCount.text = "0/0"
            clearSearchHighlights()
        }
    }

    private fun navigateSearchMatch(direction: Int) {
        if (searchMatches.isEmpty()) return
        currentMatchIndex = (currentMatchIndex + direction + searchMatches.size) % searchMatches.size
        tvFindMatchCount.text = "${currentMatchIndex + 1}/${searchMatches.size}"
        highlightCurrentMatch(etFind.text.toString())
    }

    private fun highlightCurrentMatch(query: String) {
        if (currentMatchIndex !in searchMatches.indices) return
        val pos = searchMatches[currentMatchIndex]
        etCode.setSelection(pos, (pos + query.length).coerceAtMost(etCode.text.length))
    }

    private fun clearSearchHighlights() {
        val spans = etCode.text.getSpans(0, etCode.text.length, BackgroundColorSpan::class.java)
        for (span in spans) {
            etCode.text.removeSpan(span)
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
        var beforeText = ""
        etCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!isFormatting && !isUndoOrRedo && s != null) {
                    beforeText = s.toString()
                }
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isFormatting) return
                isModified = true
                updateLineNumbers()
            }

            override fun afterTextChanged(s: Editable?) {
                if (!isFormatting && !isUndoOrRedo && beforeText.isNotEmpty()) {
                    pushUndoSnapshot(beforeText)
                    beforeText = ""
                }
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
            R.id.action_search -> {
                panelSearchReplace.visibility = if (panelSearchReplace.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                if (panelSearchReplace.visibility == View.VISIBLE) {
                    etFind.requestFocus()
                } else {
                    clearSearchHighlights()
                }
                true
            }
            R.id.action_templates -> {
                showCodeTemplatesDialog()
                true
            }
            R.id.action_format_code -> {
                formatCodeIndentation()
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

    private fun showCodeTemplatesDialog() {
        val templates = arrayOf(
            "Quick File Search (PolymathOS.findFiles)",
            "Storage Analyzer (PolymathOS.getDiskStats)",
            "Text File Transformer (PolymathOS.readFile & writeFile)",
            "Execute Shell Command (PolymathOS.executeShell)",
            "Display Custom Alert (Polymath.alert)",
            "Async Promise Batch Operation"
        )

        val snippets = arrayOf(
            """// Polymath File Search
const files = PolymathOS.findFiles("/sdcard", ".apk");
console.log("Found APKs count: " + files.length);
files.forEach((file, idx) => {
  console.log(`[${'$'}{idx + 1}] ${'$'}{file}`);
});
""",
            """// Polymath Storage Analyzer
const stats = PolymathOS.getDiskStats();
console.log("=== Disk Usage Summary ===");
console.log("Total: " + stats.total);
console.log("Available: " + stats.available);
console.log("Used: " + stats.used + " (" + stats.percentage + "%)");
""",
            """// Read & Transform File
const samplePath = "/sdcard/Download/test.txt";
if (PolymathOS.exists(samplePath)) {
  const content = PolymathOS.readFile(samplePath);
  console.log("Original content length: " + content.length);
  const upper = content.toUpperCase();
  PolymathOS.writeFile(samplePath + ".bak", upper);
  Polymath.alert("Success", "Transformed file saved to " + samplePath + ".bak");
} else {
  console.log("File not found at " + samplePath);
}
""",
            """// Shell Execution
const res = PolymathOS.executeShell("ls -la /sdcard");
console.log("Shell Output:");
console.log(res);
""",
            """// Interactive User Alert
Polymath.alert("Polymath Notification", "Automated file processing completed successfully!");
""",
            """// Async Batch Flow
async function runBatch() {
  console.log("Starting batch processing...");
  await new Promise(r => setTimeout(r, 500));
  console.log("Step 1 done");
  await new Promise(r => setTimeout(r, 500));
  console.log("Step 2 done");
  console.log("Batch complete!");
}
runBatch();
"""
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("Insert JS Code Template")
            .setItems(templates) { _, which ->
                val snippet = snippets[which]
                insertTextAtCursor(snippet)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatCodeIndentation() {
        val original = etCode.text.toString()
        if (original.isEmpty()) return
        pushUndoSnapshot(original)

        val lines = original.split("\n")
        val formatted = StringBuilder()
        var currentIndent = 0

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                formatted.append("\n")
                continue
            }

            // Decrease indent before this line if it starts with closing brace/bracket
            var startsWithClosing = 0
            for (ch in trimmed) {
                if (ch == '}' || ch == ']' || ch == ')') startsWithClosing++
                else break
            }
            val lineIndent = (currentIndent - startsWithClosing).coerceAtLeast(0)

            for (i in 0 until lineIndent) {
                formatted.append(indentString)
            }
            formatted.append(trimmed).append("\n")

            // Count openings and closings to update currentIndent
            var openings = 0
            var closings = 0
            for (ch in trimmed) {
                if (ch == '{' || ch == '[' || ch == '(') openings++
                if (ch == '}' || ch == ']' || ch == ')') closings++
            }
            currentIndent = (currentIndent + openings - closings).coerceAtLeast(0)
        }

        isFormatting = true
        etCode.setText(formatted.toString().trimEnd())
        isFormatting = false
        updateLineNumbers()
        highlightHandler.postDelayed({
            isFormatting = true
            CodeSyntaxHighlighter.highlight(etCode.text, currentFile?.extension ?: "js")
            isFormatting = false
        }, 100)
        Toast.makeText(this, "Formatted code", Toast.LENGTH_SHORT).show()
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
}

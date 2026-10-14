package com.polymath.fs.ui

import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.polymath.fs.R
import com.polymath.fs.core.BaseDynamicActivity
import com.polymath.fs.core.SystemBarHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class TerminalActivity : BaseDynamicActivity() {

    private lateinit var tvCurrentPath: TextView
    private lateinit var tvShellBadge: TextView
    private lateinit var tvOutput: TextView
    private lateinit var svTerminal: ScrollView
    private lateinit var etInput: EditText
    private lateinit var btnSend: MaterialButton

    private lateinit var btnCtrl: Button
    private lateinit var btnAlt: Button
    private lateinit var btnEsc: Button
    private lateinit var btnTab: Button
    private lateinit var btnExit: Button

    private lateinit var chipLs: MaterialButton
    private lateinit var chipPwd: MaterialButton
    private lateinit var chipDf: MaterialButton
    private lateinit var chipUname: MaterialButton
    private lateinit var chipGitStatus: MaterialButton
    private lateinit var chipNodeOsPing: MaterialButton
    private lateinit var chipClear: MaterialButton

    private var isCtrlPressed = false
    private var isAltPressed = false

    private var activeProcess: Process? = null
    private var executionJob: Job? = null
    private lateinit var currentDirectory: File
    private var shellExecutable: String = "/system/bin/sh"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        val appBar = findViewById<AppBarLayout>(R.id.terminalAppBarLayout)
        if (appBar != null) {
            SystemBarHelper.applyDynamicStatusBarInsets(appBar)
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.terminalToolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        initViews()
        setupListeners()
        resolveShellAndWorkingDir()
        printInitialBanner()
    }

    private fun initViews() {
        tvCurrentPath = findViewById(R.id.tvCurrentPath)
        tvShellBadge = findViewById(R.id.tvShellBadge)
        tvOutput = findViewById(R.id.tvOutput)
        svTerminal = findViewById(R.id.svTerminal)
        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)

        btnCtrl = findViewById(R.id.btnCtrl)
        btnAlt = findViewById(R.id.btnAlt)
        btnEsc = findViewById(R.id.btnEsc)
        btnTab = findViewById(R.id.btnTab)
        btnExit = findViewById(R.id.btnExit)

        chipLs = findViewById(R.id.chipLs)
        chipPwd = findViewById(R.id.chipPwd)
        chipDf = findViewById(R.id.chipDf)
        chipUname = findViewById(R.id.chipUname)
        chipGitStatus = findViewById(R.id.chipGitStatus)
        chipNodeOsPing = findViewById(R.id.chipNodeOsPing)
        chipClear = findViewById(R.id.chipClear)
    }

    private fun setupListeners() {
        btnCtrl.setOnClickListener {
            isCtrlPressed = !isCtrlPressed
            btnCtrl.setTextColor(if (isCtrlPressed) Color.GREEN else Color.WHITE)
            if (isCtrlPressed && activeProcess != null) {
                // Emulate SIGINT / terminate current running process
                activeProcess?.destroy()
                appendOutput("^C\n[Interrupted]")
                isCtrlPressed = false
                btnCtrl.setTextColor(Color.WHITE)
            }
        }

        btnAlt.setOnClickListener {
            isAltPressed = !isAltPressed
            btnAlt.setTextColor(if (isAltPressed) Color.GREEN else Color.WHITE)
        }

        btnEsc.setOnClickListener {
            etInput.text.clear()
        }

        btnTab.setOnClickListener {
            val current = etInput.text.toString()
            if (current.isNotEmpty()) {
                val completed = completeFilename(current)
                etInput.setText(completed)
                etInput.setSelection(completed.length)
            }
        }

        btnExit.setOnClickListener {
            finish()
        }

        // Quick Action Chips
        chipLs.setOnClickListener { executeCommand("ls -la") }
        chipPwd.setOnClickListener { executeCommand("pwd") }
        chipDf.setOnClickListener { executeCommand("df -h") }
        chipUname.setOnClickListener { executeCommand("uname -a") }
        chipGitStatus.setOnClickListener { executeCommand("git status") }
        chipNodeOsPing.setOnClickListener { executeCommand("nodeos -c ping") }
        chipClear.setOnClickListener { clearOutput() }

        btnSend.setOnClickListener {
            submitInput()
        }

        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                submitInput()
                true
            } else {
                false
            }
        }
    }

    private fun submitInput() {
        val cmd = etInput.text.toString().trim()
        if (cmd.isNotEmpty()) {
            executeCommand(cmd)
            etInput.text.clear()
        }
    }

    private fun resolveShellAndWorkingDir() {
        val termuxBash = File("/data/data/com.termux/files/usr/bin/bash")
        if (termuxBash.exists() && termuxBash.canExecute()) {
            shellExecutable = termuxBash.absolutePath
            tvShellBadge.text = "BASH"
        } else {
            shellExecutable = "/system/bin/sh"
            tvShellBadge.text = "POSIX SH"
        }

        val projectWorkspace = File("/data/data/com.termux/files/home/Projects/polymath-filesystem")
        currentDirectory = when {
            projectWorkspace.exists() && projectWorkspace.isDirectory -> projectWorkspace
            Environment.getExternalStorageDirectory() != null && Environment.getExternalStorageDirectory().exists() ->
                Environment.getExternalStorageDirectory()
            else -> filesDir
        }
        tvCurrentPath.text = currentDirectory.absolutePath
    }

    private fun printInitialBanner() {
        val banner = buildString {
            append("═══════════════════════════════════════════════════════════════\n")
            append("  POLYMATH OMNI-TERMINAL [Runtime Console v2.0]\n")
            append("  Subsystem: POSIX ProcessBuilder Native Transport\n")
            append("  Shell: $shellExecutable\n")
            append("  Environment: TERM=xterm-256color, PREFIX=/data/data/com.termux/files/usr\n")
            append("═══════════════════════════════════════════════════════════════\n\n")
        }
        tvOutput.text = banner
    }

    private fun clearOutput() {
        printInitialBanner()
    }

    private fun executeCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return

        appendOutput("$ $trimmed")

        // 1. Built-in 'cd' handling to change directory
        if (trimmed == "cd" || trimmed.startsWith("cd ")) {
            handleCdCommand(trimmed)
            return
        }

        // 2. Built-in 'clear' handling
        if (trimmed == "clear") {
            clearOutput()
            return
        }

        // 3. Built-in 'exit' handling
        if (trimmed == "exit") {
            finish()
            return
        }

        // 4. Interactive shell execution via ProcessBuilder
        executionJob?.cancel()
        executionJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder(shellExecutable, "-c", trimmed)
                pb.directory(currentDirectory)

                val env = pb.environment()
                val sysPath = System.getenv("PATH") ?: "/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin"
                val termuxBin = "/data/data/com.termux/files/usr/bin"
                env["PATH"] = "$termuxBin:$sysPath"
                env["TERM"] = "xterm-256color"
                env["PREFIX"] = "/data/data/com.termux/files/usr"
                env["HOME"] = System.getenv("HOME") ?: "/data/data/com.termux/files/home"
                env["TMPDIR"] = "/data/data/com.termux/files/usr/tmp"
                env["PWD"] = currentDirectory.absolutePath

                pb.redirectErrorStream(true)

                val process = pb.start()
                activeProcess = process

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val outLine = line ?: break
                    withContext(Dispatchers.Main) {
                        appendOutput(outLine)
                    }
                }

                val exitCode = process.waitFor()
                activeProcess = null

                if (exitCode != 0) {
                    withContext(Dispatchers.Main) {
                        appendOutput("[Command exited with code $exitCode]")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendOutput("Error executing command: ${e.message}")
                }
            }
        }
    }

    private fun handleCdCommand(cmd: String) {
        val targetArg = if (cmd == "cd") "" else cmd.substring(2).trim()
        val targetDir = when {
            targetArg.isEmpty() || targetArg == "~" -> {
                val home = System.getenv("HOME")
                if (home != null && File(home).exists()) File(home) else currentDirectory
            }
            targetArg.startsWith("/") -> File(targetArg)
            else -> File(currentDirectory, targetArg)
        }

        val canonical = try {
            targetDir.canonicalFile
        } catch (_: Exception) {
            targetDir
        }

        if (canonical.exists() && canonical.isDirectory) {
            currentDirectory = canonical
            tvCurrentPath.text = currentDirectory.absolutePath
        } else {
            appendOutput("cd: ${targetArg}: No such file or directory")
        }
    }

    private fun completeFilename(prefix: String): String {
        val parts = prefix.split("\\s+".toRegex())
        val lastPart = parts.lastOrNull() ?: return prefix
        val searchDir = if (lastPart.contains("/")) {
            val dirPath = lastPart.substringBeforeLast("/")
            val d = if (dirPath.startsWith("/")) File(dirPath) else File(currentDirectory, dirPath)
            if (d.exists() && d.isDirectory) d else currentDirectory
        } else {
            currentDirectory
        }

        val partialName = lastPart.substringAfterLast("/")
        val matches = searchDir.listFiles()?.filter { it.name.startsWith(partialName) }
        if (!matches.isNullOrEmpty()) {
            val completion = matches.first().name
            val prefixWithoutLast = prefix.substring(0, prefix.length - partialName.length)
            return prefixWithoutLast + completion
        }
        return prefix
    }

    private fun appendOutput(text: String) {
        tvOutput.append(text + "\n")
        svTerminal.post {
            svTerminal.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executionJob?.cancel()
        activeProcess?.destroy()
        activeProcess = null
    }
}

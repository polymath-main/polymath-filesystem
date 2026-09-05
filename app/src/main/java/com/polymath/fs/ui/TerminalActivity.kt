package com.polymath.fs.ui

import android.os.Bundle
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.polymath.fs.R
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter

@AndroidEntryPoint
class TerminalActivity : AppCompatActivity() {

    private lateinit var tvOutput: TextView
    private lateinit var etInput: EditText
    private lateinit var svTerminal: ScrollView

    private var process: Process? = null
    private var writer: PrintWriter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        tvOutput = findViewById(R.id.tvOutput)
        etInput = findViewById(R.id.etInput)
        svTerminal = findViewById(R.id.svTerminal)

        etInput.setOnEditorActionListener { _, _, _ ->
            val cmd = etInput.text.toString()
            if (cmd.isNotBlank()) {
                sendCommand(cmd)
                etInput.text.clear()
            }
            true
        }

        startShell()
    }

    private fun startShell() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Using standard process for interactive shell
                val p = Runtime.getRuntime().exec("su")
                process = p
                writer = PrintWriter(OutputStreamWriter(p.outputStream), true)
                
                val reader = BufferedReader(InputStreamReader(p.inputStream))
                val errReader = BufferedReader(InputStreamReader(p.errorStream))
                
                launch(Dispatchers.IO) {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        appendOutput(line!!)
                    }
                }
                launch(Dispatchers.IO) {
                    var line: String?
                    while (errReader.readLine().also { line = it } != null) {
                        appendOutput("ERROR: $line")
                    }
                }
            } catch (e: Exception) {
                appendOutput("Failed to start shell: ${e.message}")
            }
        }
    }

    private fun sendCommand(cmd: String) {
        appendOutput("$ $cmd")
        lifecycleScope.launch(Dispatchers.IO) {
            writer?.println(cmd)
        }
    }

    private suspend fun appendOutput(text: String) {
        withContext(Dispatchers.Main) {
            tvOutput.append(text + "\n")
            svTerminal.post {
                svTerminal.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        writer?.close()
        process?.destroy()
    }
}

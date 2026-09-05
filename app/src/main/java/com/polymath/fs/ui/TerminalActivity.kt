package com.polymath.fs.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
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
    private lateinit var btnCtrl: Button
    private lateinit var btnAlt: Button
    private lateinit var btnEsc: Button
    private lateinit var btnTab: Button
    private lateinit var btnExit: Button

    private var process: Process? = null
    private var writer: PrintWriter? = null

    private var isCtrlPressed = false
    private var isAltPressed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        tvOutput = findViewById(R.id.tvOutput)
        etInput = findViewById(R.id.etInput)
        svTerminal = findViewById(R.id.svTerminal)
        btnCtrl = findViewById(R.id.btnCtrl)
        btnAlt = findViewById(R.id.btnAlt)
        btnEsc = findViewById(R.id.btnEsc)
        btnTab = findViewById(R.id.btnTab)
        btnExit = findViewById(R.id.btnExit)

        btnCtrl.setOnClickListener {
            isCtrlPressed = !isCtrlPressed
            btnCtrl.setTextColor(if (isCtrlPressed) Color.GREEN else Color.WHITE)
        }
        
        btnAlt.setOnClickListener {
            isAltPressed = !isAltPressed
            btnAlt.setTextColor(if (isAltPressed) Color.GREEN else Color.WHITE)
        }
        
        btnEsc.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                writer?.print("\u001B")
                writer?.flush()
            }
        }
        
        btnTab.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                writer?.print("\t")
                writer?.flush()
            }
        }
        
        btnExit.setOnClickListener {
            finish()
        }

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
                    while (true) {
                        val line = reader.readLine() ?: break
                        appendOutput(line)
                    }
                }
                launch(Dispatchers.IO) {
                    while (true) {
                        val line = errReader.readLine() ?: break
                        appendOutput("ERROR: $line")
                    }
                }
            } catch (e: Exception) {
                appendOutput("Failed to start shell: ${e.message}")
            }
        }
    }

    private fun sendCommand(cmd: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            appendOutput("$ $cmd")
            var finalCmd = cmd
            
            if (isCtrlPressed && finalCmd.isNotEmpty()) {
                val firstChar = finalCmd[0]
                val ctrlChar = if (firstChar.isLetter()) {
                    (firstChar.uppercaseChar().code - 64).toChar().toString()
                } else {
                    firstChar.toString()
                }
                finalCmd = ctrlChar + finalCmd.substring(1)
                isCtrlPressed = false
                withContext(Dispatchers.Main) { btnCtrl.setTextColor(Color.WHITE) }
            }
            
            if (isAltPressed) {
                finalCmd = "\u001B$finalCmd"
                isAltPressed = false
                withContext(Dispatchers.Main) { btnAlt.setTextColor(Color.WHITE) }
            }
            
            writer?.println(finalCmd)
            writer?.flush()
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

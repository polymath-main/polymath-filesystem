package com.polymath.fs.viewers

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.polymath.fs.R
import java.io.File

class TextViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_viewer)
        
        val lineNumbers: TextView = findViewById(R.id.line_numbers)
        val textContent: TextView = findViewById(R.id.text_content)
        
        val filePath = intent.getStringExtra("FILE_PATH")
        if (filePath != null) {
            val file = File(filePath)
            if (file.exists()) {
                val lines = file.readLines()
                val sbLines = java.lang.StringBuilder()
                val sbContent = java.lang.StringBuilder()
                
                for ((index, line) in lines.withIndex()) {
                    sbLines.append("${index + 1}\n")
                    sbContent.append(line).append("\n")
                }
                
                lineNumbers.text = sbLines.toString()
                textContent.text = sbContent.toString()
            } else {
                textContent.text = "File does not exist."
            }
        } else {
            textContent.text = "No file provided."
        }
    }
}

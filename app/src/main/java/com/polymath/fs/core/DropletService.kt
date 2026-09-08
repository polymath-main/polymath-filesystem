package com.polymath.fs.core

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.polymath.fs.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DropletService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var dropletView: View
    private lateinit var layoutParams: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Inflate the Droplet UI
        dropletView = LayoutInflater.from(this).inflate(R.layout.layout_droplet, null)
        
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 0
        layoutParams.y = 300 // Initial vertical position

        windowManager.addView(dropletView, layoutParams)
        
        setupDropletInteractions()
    }

    private fun setupDropletInteractions() {
        val dropletCard = dropletView.findViewById<View>(R.id.droplet_card)
        
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        dropletCard.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isDragging = true
                        layoutParams.x = initialX + deltaX.toInt()
                        layoutParams.y = initialY + deltaY.toInt()
                        windowManager.updateViewLayout(dropletView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        view.performClick()
                        handleDropletClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun handleDropletClick() {
        // Capture clipboard and save it into a new file in a special Droplet Inbox folder
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData: ClipData? = clipboard.primaryClip
        
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text
            if (!text.isNullOrEmpty()) {
                saveToDropletInbox(text.toString())
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No clipboard data to capture", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToDropletInbox(data: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inboxDir = File("/storage/emulated/0/Polymath/Droplet_Inbox")
                if (!inboxDir.exists()) {
                    inboxDir.mkdirs()
                }
                
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val newFile = File(inboxDir, "DropletCapture_$timestamp.txt")
                newFile.writeText(data)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DropletService, "Captured to Polymath/Droplet_Inbox", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DropletService, "Failed to capture data", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::dropletView.isInitialized) {
            windowManager.removeView(dropletView)
        }
    }
}

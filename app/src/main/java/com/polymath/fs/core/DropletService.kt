package com.polymath.fs.core

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import com.polymath.fs.R
import com.polymath.fs.data.db.AppDatabase
import com.polymath.fs.data.db.entities.CognitiveClipEntity
import com.polymath.fs.ui.clips.CognitiveClipsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DropletService : Service() {

    companion object {
        private val _isRunning = kotlinx.coroutines.flow.MutableStateFlow(false)
        val isRunning: kotlinx.coroutines.flow.StateFlow<Boolean> = _isRunning
        private const val DOUBLE_TAP_TIMEOUT_MS = 280L
    }

    private lateinit var windowManager: WindowManager
    private lateinit var dropletView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tapCount = 0
    private val singleTapRunnable = Runnable {
        tapCount = 0
        launchCognitiveClipsActivity()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Inflate the Droplet UI with themed context to prevent MaterialCardView crash in Service
        val themedContext = android.view.ContextThemeWrapper(this, com.polymath.fs.R.style.Theme_PolymathFileSystem)
        dropletView = LayoutInflater.from(themedContext).inflate(R.layout.layout_droplet, null)

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

        try {
            windowManager.addView(dropletView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return
        }

        setupDropletInteractions()
    }

    private fun setupDropletInteractions() {
        val dropletCard = dropletView.findViewById<View>(R.id.droplet_card)
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

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
                    val distanceSq = deltaX * deltaX + deltaY * deltaY

                    if (distanceSq > touchSlop * touchSlop) {
                        if (!isDragging) {
                            isDragging = true
                            mainHandler.removeCallbacks(singleTapRunnable)
                            tapCount = 0
                        }
                        layoutParams.x = initialX + deltaX.toInt()
                        layoutParams.y = initialY + deltaY.toInt()
                        windowManager.updateViewLayout(dropletView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        isDragging = false
                    } else {
                        view.performClick()
                        tapCount++
                        if (tapCount == 1) {
                            mainHandler.postDelayed(singleTapRunnable, DOUBLE_TAP_TIMEOUT_MS)
                        } else if (tapCount >= 2) {
                            mainHandler.removeCallbacks(singleTapRunnable)
                            tapCount = 0
                            handleDoubleTapCapture(dropletCard)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    mainHandler.removeCallbacks(singleTapRunnable)
                    tapCount = 0
                    true
                }
                else -> false
            }
        }
    }

    private fun launchCognitiveClipsActivity() {
        dropletView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val intent = Intent(this, CognitiveClipsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun handleDoubleTapCapture(dropletCard: View) {
        dropletCard.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

        // Micro-pulse animation to acknowledge capture
        dropletCard.animate()
            .scaleX(1.22f)
            .scaleY(1.22f)
            .setDuration(90)
            .withEndAction {
                dropletCard.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(90)
                    .start()
            }
            .start()

        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData: ClipData? = clipboard.primaryClip

            if (clipData != null && clipData.itemCount > 0) {
                val item = clipData.getItemAt(0)
                val rawText = item.coerceToText(this)?.toString() ?: ""
                val text = rawText.trim()

                if (text.isNotEmpty()) {
                    val firstLine = text.lines().firstOrNull { it.isNotBlank() } ?: "Untitled Clip"
                    val title = if (firstLine.length > 50) firstLine.take(47) + "..." else firstLine
                    val charCount = text.length

                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val clipEntity = CognitiveClipEntity(
                                content = text,
                                title = title,
                                charCount = charCount,
                                timestamp = System.currentTimeMillis()
                            )
                            AppDatabase.getDatabase(applicationContext).cognitiveClipDao().insert(clipEntity)

                            // Optional file backup in Droplet Inbox
                            saveToDropletInbox(text)

                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@DropletService,
                                    "Saved to Cognitive Clips (#$charCount chars)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@DropletService,
                                    "Failed to save clip: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot read clipboard: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToDropletInbox(data: String) {
        try {
            val inboxDir = File("/storage/emulated/0/Polymath/Droplet_Inbox")
            if (!inboxDir.exists()) {
                inboxDir.mkdirs()
            }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val newFile = File(inboxDir, "DropletCapture_$timestamp.txt")
            newFile.writeText(data)
        } catch (e: Exception) {
            // Non-critical background backup
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        mainHandler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        if (::dropletView.isInitialized && dropletView.isAttachedToWindow) {
            runCatching { windowManager.removeView(dropletView) }
        }
    }
}

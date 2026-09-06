package com.polymath.fs.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.polymath.fs.PolymathApp
import com.polymath.fs.domain.usecase.CopyFilesUseCase
import com.polymath.fs.domain.usecase.MoveFilesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FileOperationService : Service() {

    private val copyFilesUseCase: CopyFilesUseCase by lazy { (application as PolymathApp).copyFilesUseCase }
    private val moveFilesUseCase: MoveFilesUseCase by lazy { (application as PolymathApp).moveFilesUseCase }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val CHANNEL_ID = "FileOperationChannel"
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("ACTION") ?: return START_NOT_STICKY
        val src = intent.getStringArrayListExtra("SRC")?.toList() ?: return START_NOT_STICKY
        val dest = intent.getStringExtra("DEST") ?: return START_NOT_STICKY

        startForeground(NOTIFICATION_ID, createNotification(0, 100, "Starting operation..."))

        serviceScope.launch {
            when (action) {
                "COPY" -> {
                    copyFilesUseCase(src, dest).collectLatest { progress ->
                        updateNotification(progress.currentBytes.toInt(), progress.totalBytes.toInt(), "Copying files...")
                        if (progress.isComplete) {
                            ServiceCompat.stopForeground(this@FileOperationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
                "MOVE" -> {
                    moveFilesUseCase(src, dest).collectLatest { progress ->
                        updateNotification(progress.currentBytes.toInt(), progress.totalBytes.toInt(), "Moving files...")
                        if (progress.isComplete) {
                            ServiceCompat.stopForeground(this@FileOperationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Operations",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(current: Int, total: Int, title: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(total, current, false)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(current: Int, total: Int, title: String) {
        val notification = createNotification(current, total, title)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }
}

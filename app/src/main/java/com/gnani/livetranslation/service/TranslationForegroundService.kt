package com.gnani.livetranslation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gnani.livetranslation.MainActivity
import com.gnani.livetranslation.R

class TranslationForegroundService : Service() {

    private var sessionMode: String = MODE_WEBRTC

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val roomId = intent?.getStringExtra(EXTRA_ROOM_ID) ?: "unknown"
        sessionMode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_WEBRTC
        createNotificationChannel()
        val notification = buildNotification(roomId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(roomId: String): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ROOM_ID, roomId)
            putExtra(EXTRA_MODE, sessionMode)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = when (sessionMode) {
            MODE_PHONE, "twilio" -> "Call translation active"
            else -> getString(R.string.notification_title)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("${getString(R.string.notification_text)} — tap for captions")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .build()
    }

    companion object {
        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_MODE = "mode"
        const val MODE_PHONE = "phone"
        const val MODE_WEBRTC = "webrtc"
        private const val CHANNEL_ID = "translation_active"
        private const val NOTIFICATION_ID = 1001
    }
}

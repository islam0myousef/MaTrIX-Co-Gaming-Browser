package com.matrixco.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Keeps the browser's media playback session eligible to continue while the
 * Activity is backgrounded. The foreground notification is promoted in
 * onCreate(), before any other service work can delay the FGS deadline.
 */
class AudioService : Service() {

    companion object {
        private const val TAG = "MaTRIX.AudioService"
        private const val CHANNEL_ID = "mx_audio"
        private const val NOTIFICATION_ID = 7731
    }

    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        promoteToForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Defensive fallback for service recreation / unusual platform ordering.
        if (!foregroundStarted) {
            promoteToForeground()
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MaTRIX Audio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار تشغيل الصوت في الخلفية"
                setShowBadge(false)
            }

            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }

        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            pendingFlags
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("MaTRIX Co")
            .setContentText("الصوت شغّال في الخلفية — اضغط للرجوع")
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun promoteToForeground() {
        if (foregroundStarted) return

        try {
            val notification = buildNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            foregroundStarted = true
        } catch (error: Exception) {
            // Stop explicitly instead of leaving a started service waiting for
            // the system's foreground-service timeout.
            Log.e(TAG, "Unable to promote AudioService to foreground", error)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (foregroundStarted) {
            stopForeground(true)
            foregroundStarted = false
        }
        super.onDestroy()
    }
}

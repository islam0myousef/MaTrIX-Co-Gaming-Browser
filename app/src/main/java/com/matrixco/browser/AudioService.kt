package com.matrixco.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

/** خدمة خفيفة تبقي التطبيق حيًّا لتكملة الصوت والشاشة مقفولة */
class AudioService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("mx_audio", "MaTrIX Audio", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "mx_audio") else @Suppress("DEPRECATION") Notification.Builder(this)
        val n = b.setContentTitle("MaTrIX Co").setContentText("الصوت شغّال في الخلفية — اضغط للرجوع")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(open).setOngoing(true).build()
        startForeground(7731, n)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(true)
        super.onDestroy()
    }
}

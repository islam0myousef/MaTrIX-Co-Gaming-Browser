package com.matrixco.browser

import android.app.Application
import android.util.Log
import java.io.File

/** يسجّل أي كراش في ملف عشان نعرضه في التشغيل التالي */
class MxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // DevTools is available only in debug builds; release builds must not
        // expose WebView inspection endpoints to local USB/ADB sessions.
        android.webkit.WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        // Periodic security-feed refresh; WorkManager waits for network and
        // may defer execution under Android battery optimizations.
        try {
            ThreatFeedWorker.schedule(this)
        } catch (t: Throwable) {
            Log.w("MaTRIX.ThreatFeeds", "Could not schedule periodic feed refresh", t)
        }
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                File(filesDir, "crash.txt").writeText(
                    "Thread: " + t.name + "\nAndroid: " + android.os.Build.VERSION.SDK_INT + " / " + android.os.Build.MODEL + "\n\n" + Log.getStackTraceString(e)
                )
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }
}

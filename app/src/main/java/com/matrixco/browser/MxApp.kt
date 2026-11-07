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

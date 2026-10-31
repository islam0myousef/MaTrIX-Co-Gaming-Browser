package com.matrixco.browser

import android.app.Application
import android.util.Log
import java.io.File

/** يسجّل أي كراش في ملف عشان نعرضه في التشغيل التالي */
class MxApp : Application() {
    override fun onCreate() {
        super.onCreate()
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

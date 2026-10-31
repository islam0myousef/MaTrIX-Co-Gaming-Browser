package com.matrixco.browser

/**
 * كاشف التجسس: يجمع إشارات سلوكية من الصفحة (بصمة المتصفح، تسجيل الجلسات، WebRTC ...)
 * ويحسب نسبة ثقة. ≥90% = حجب تلقائي ، 50–89% = يسأل المستخدم ، أقل = يسجّل فقط.
 */
object SpyGuard {
    const val AUTO = 90
    const val ASK = 50

    private val labels = mapOf(
        "canvas_fp" to "بصمة المتصفح (Canvas)",
        "audio_fp" to "بصمة المتصفح (Audio)",
        "webrtc" to "كشف عنوانك الحقيقي (WebRTC)",
        "clipboard_read" to "قراءة الحافظة (Clipboard)",
        "geolocation" to "طلب موقعك الجغرافي",
        "media" to "طلب الكاميرا/المايك",
        "hidden_iframe" to "إطار خفي لجهة خارجية",
        "session_replay" to "تسجيل حركاتك على الصفحة",
        "key_listener" to "مراقبة لوحة المفاتيح",
        "sensor" to "قراءة حساسات الجهاز",
        "known_spy" to "جهة مراقبة معروفة",
        "miner" to "تعدين عملات على جهازك"
    )

    private val base = mapOf(
        "canvas_fp" to 55, "audio_fp" to 60, "webrtc" to 45, "clipboard_read" to 65, "geolocation" to 40,
        "media" to 50, "hidden_iframe" to 55, "session_replay" to 70, "key_listener" to 30, "sensor" to 30,
        "known_spy" to 95, "miner" to 95
    )

    fun label(type: String): String = labels[type] ?: type

    /** نسبة الثقة: الأساس + (جهة خارجية) + (الجهة موجودة في قوائمنا) */
    fun confidence(type: String, srcHost: String, pageHost: String): Int {
        var p = (base[type] ?: 30).toDouble()
        val third = srcHost.isNotEmpty() && AdBlocker.isThirdParty(srcHost, pageHost)
        if (third) p += (100 - p) * 0.4
        val cat = if (third) AdBlocker.categoryOf(srcHost) else 0
        if (cat == Cat.SPY || cat == Cat.MAL) p += (100 - p) * 0.85
        else if (cat != 0) p += (100 - p) * 0.5
        return p.toInt().coerceIn(0, 99)
    }

    fun isThirdParty(srcHost: String, pageHost: String) = srcHost.isNotEmpty() && AdBlocker.isThirdParty(srcHost, pageHost)
}

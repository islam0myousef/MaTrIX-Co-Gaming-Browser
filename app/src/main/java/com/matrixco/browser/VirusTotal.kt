package com.matrixco.browser

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * الفحص السحابي لبصمات التحميلات عبر VirusTotal (بمفتاح المستخدم المجاني).
 *  بيبعت بصمة SHA-256 بس — محتوى الملف مبيروحش أبدًا.
 *  الحصة المجانية 4 طلب/دقيقة، ففيه مباعدة إجبارية بين الطلبات.
 *  أي خطأ أو 429 بيرجّع SKIPPED: الفحص المحلي هو المرجع، وممنوع نمنع ملف بسبب فشل الخدمة.
 */
object VirusTotal {
    const val CLEAN = 0     // البصمة معروفة ومفيش ولا محرك علّم عليها
    const val UNKNOWN = 1   // البصمة مش موجودة في قاعدة VirusTotal
    const val DETECTED = 2  // محرك واحد على الأقل علّم عليها
    const val SKIPPED = 3   // خطأ/معدل/مفيش مفتاح — الفحص المحلي بس

    class Verdict(val state: Int, val detections: Int = 0, val total: Int = 0, val message: String = "", val sha: String = "") {
        val link: String get() = if (sha.isEmpty()) "" else "https://www.virustotal.com/gui/file/$sha"
    }

    private var lastCall = 0L
    private const val MIN_GAP_MS = 15_500L

    @Synchronized
    private fun passGate(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCall < MIN_GAP_MS) return false
        lastCall = now
        return true
    }

    /** بيتنادى من thread خلفي فقط (الخزنة بتشتغل كده أصلًا) */
    fun lookup(apiKey: String, sha: String): Verdict {
        if (apiKey.isEmpty()) return Verdict(SKIPPED, message = "مفيش مفتاح متسجّل", sha = sha)
        if (sha.isEmpty()) return Verdict(SKIPPED, message = "البصمة غير متاحة")
        if (!passGate()) return Verdict(SKIPPED, message = "مباعدة الحصة المجانية (4 طلبات/دقيقة) — جرّب \"افحص الآن\" بعد ثواني", sha = sha)
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("https://www.virustotal.com/api/v3/files/$sha").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("x-apikey", apiKey)
            conn.setRequestProperty("accept", "application/json")
            val code = conn.responseCode
            when (code) {
                200 -> {
                    val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val stats = JSONObject(text).getJSONObject("data").getJSONObject("attributes").getJSONObject("last_analysis_stats")
                    val mal = stats.optInt("malicious") + stats.optInt("suspicious")
                    var total = 0
                    val keys = stats.keys()
                    while (keys.hasNext()) total += stats.optInt(keys.next())
                    if (mal > 0) Verdict(DETECTED, mal, total, sha = sha) else Verdict(CLEAN, 0, total, sha = sha)
                }
                401, 403 -> Verdict(SKIPPED, message = "المفتاح مرفوض ($code) — راجعه في الإعدادات ← الأمان", sha = sha)
                404 -> Verdict(UNKNOWN, sha = sha)
                429 -> Verdict(SKIPPED, message = "الحصة المجانية اتخطّت مؤقتًا — جرّب بعد دقيقة", sha = sha)
                else -> Verdict(SKIPPED, message = "رد غير متوقع من الخدمة ($code)", sha = sha)
            }
        } catch (_: Exception) {
            Verdict(SKIPPED, message = "تعذر الاتصال بالخدمة", sha = sha)
        } finally { conn?.disconnect() }
    }
}

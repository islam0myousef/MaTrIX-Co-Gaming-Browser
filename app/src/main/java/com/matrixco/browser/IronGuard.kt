package com.matrixco.browser

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import org.json.JSONTokener

/**
 * الجدار الحديدي: محرك تقييم خطر (0–100) بدون أي اتصال بسيرفر.
 * الدرجة >= العتبة = منع تلقائي بدون سؤال وبدون زر "كمل". العتبات: حديدي 5 / عالي 15 / متوازن 35.
 * القائمة البيضاء بتتعدّل من الإعدادات بس (بعد PIN).
 */
object IronGuard {
    const val ON = "iron_on"
    const val LEVEL = "iron_level"      // 0 حديدي (5) • 1 عالي (15) • 2 متوازن (35)

    class Verdict(val score: Int, val reason: String)

    fun enabled() = Cfg.bool(ON, true)
    fun threshold() = when (Cfg.int(LEVEL, 0)) { 1 -> 15; 2 -> 35; else -> 5 }
    fun levelName() = when (Cfg.int(LEVEL, 0)) { 1 -> "عالي (15)"; 2 -> "متوازن (35)"; else -> "حديدي (5)" }

    private val riskyTld = setOf("tk", "ml", "ga", "cf", "gq", "top", "click", "loan", "work", "zip", "mov", "country", "kim",
        "science", "party", "gdn", "racing", "review", "download", "stream", "men", "bid", "trade", "win", "accountant", "cricket", "faith", "date")

    private val brands: Map<String, List<Regex>> = mapOf(
        "paypal" to listOf(Regex("(^|\\.)paypal\\.(com|me)$"), Regex("(^|\\.)paypalobjects\\.com$")),
        "facebook" to listOf(Regex("(^|\\.)(facebook\\.com|fb\\.com|fbcdn\\.net|facebook\\.net|fb\\.me)$")),
        "instagram" to listOf(Regex("(^|\\.)(instagram\\.com|cdninstagram\\.com)$")),
        "whatsapp" to listOf(Regex("(^|\\.)(whatsapp\\.com|whatsapp\\.net|wa\\.me)$")),
        "apple" to listOf(Regex("(^|\\.)(apple\\.com|icloud\\.com|mzstatic\\.com)$")),
        "microsoft" to listOf(Regex("(^|\\.)(microsoft\\.com|live\\.com|office\\.com|microsoftonline\\.com|windows\\.com|bing\\.com|xbox\\.com|azure\\.com|sharepoint\\.com|msn\\.com)$")),
        "amazon" to listOf(Regex("(^|\\.)amazon\\.[a-z.]{2,6}$"), Regex("(^|\\.)(amazonaws\\.com|amazon-adsystem\\.com|media-amazon\\.com)$")),
        "binance" to listOf(Regex("(^|\\.)binance\\.(com|us|me)$")),
        "steam" to listOf(Regex("(^|\\.)(steampowered\\.com|steamcommunity\\.com|steamstatic\\.com|steamcontent\\.com)$")),
        "discord" to listOf(Regex("(^|\\.)(discord\\.com|discord\\.gg|discordapp\\.com|discordapp\\.net|discord\\.media)$")),
        "netflix" to listOf(Regex("(^|\\.)(netflix\\.com|nflxext\\.com|nflximg\\.net)$")),
        "telegram" to listOf(Regex("(^|\\.)(telegram\\.org|t\\.me|telegram\\.me|telegra\\.ph)$")),
        "google" to listOf(Regex("(^|\\.)google\\.[a-z.]{2,6}$"), Regex("(^|\\.)(gstatic\\.com|googleapis\\.com|google-analytics\\.com|googleusercontent\\.com|googlevideo\\.com|ggpht\\.com|withgoogle\\.com|googletagmanager\\.com|ytimg\\.com|youtube\\.com|goo\\.gl|g\\.co|googlesyndication\\.com|googleadservices\\.com|doubleclick\\.net)$"))
    )

    private fun isBrandHost(brand: String, host: String) = brands[brand]?.any { it.containsMatchIn(host) } == true

    private fun privateIp(h: String): Boolean =
        h == "localhost" || h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.") ||
        Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\.").containsMatchIn(h) || h.endsWith(".local") || h == "[::1]"

    private fun isIp(h: String) = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(h) || h.startsWith("[")

    private fun scriptClass(cp: Int): Int = when {
        cp < 0x250 -> 1                       // لاتيني
        cp in 0x370..0x3FF -> 2               // يوناني
        cp in 0x400..0x52F -> 3               // سيريلي
        cp in 0x600..0x6FF || cp in 0x750..0x77F -> 4   // عربي
        else -> 5
    }

    private fun mixedScripts(label: String): Boolean {
        val seen = HashSet<Int>()
        var i = 0
        while (i < label.length) {
            val cp = label.codePointAt(i)
            i += Character.charCount(cp)
            if (Character.isLetter(cp)) seen.add(scriptClass(cp))
        }
        return seen.size > 1
    }

    /** تقييم الرابط قبل التحميل. بيرجّع null لو أقل من العتبة. */
    fun scoreUrl(ctx: Context, url: String, redirects: Int): Verdict? {
        if (!enabled()) return null
        val u = try { Uri.parse(url) } catch (_: Exception) { return null }
        val scheme = u.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = (u.host ?: return null).lowercase()
        if (host == "mx.blocked" || host == Pages.HOST) return null
        if (allowed(ctx, host)) return null
        var s = 0; var why = ""; var top = 0
        fun add(p: Int, r: String) { s += p; if (p > top) { top = p; why = r } }

        val local = privateIp(host)
        if (scheme == "http" && !local) add(60, "الموقع من غير تشفير (HTTPS)")
        if (isIp(host) && !local) add(30, "العنوان IP مباشر بدل اسم موقع")
        val labels = host.split('.')
        if (labels.any { it.startsWith("xn--") }) add(40, "اسم الموقع مشفّر بـ punycode (ممكن يكون تقليد)")
        if (labels.any { mixedScripts(it) }) add(40, "اسم الموقع فيه حروف من أبجديات مختلفة")
        if (u.userInfo != null) add(45, "الرابط فيه @ (بيخبّي الوجهة الحقيقية)")
        if (url.length > 400) add(20, "الرابط طويل جدًا") else if (url.length > 200) add(10, "الرابط طويل بشكل مريب")
        if (Regex("%[0-9a-fA-F]{2}").findAll(url).count() > 12) add(10, "الرابط فيه ترميز زائد")
        if (!isIp(host) && labels.last() in riskyTld) add(8, "امتداد دومين عالي الخطورة (." + labels.last() + ")")
        if (labels.size > 5) add(8, "عدد كبير من النطاقات الفرعية")
        for ((brand, _) in brands) {
            if (host.contains(brand) && !isBrandHost(brand, host)) { add(45, "الموقع بيقلّد اسم $brand"); break }
        }
        if (redirects > 2) add(30, "سلسلة تحويلات طويلة")
        if (!local && AdBlocker.isMaliciousPage(host)) add(100, "الموقع في قوائم الخبيث/التصيد")
        return if (s >= threshold()) Verdict(minOf(s, 100), why) else null
    }

    /** يتنفّذ جوه الصفحة بعد التحميل */
    const val PROBE = "(function(){try{var d=document;var pw=!!d.querySelector('input[type=password]');" +
        "var h1=(d.querySelector('h1')||{}).innerText||'';var ifr=0,fr=d.querySelectorAll('iframe');" +
        "for(var i=0;i<fr.length;i++){var f=fr[i];var r=f.getBoundingClientRect();var cs=getComputedStyle(f);" +
        "var hid=(r.width<3&&r.height<3)||cs.display==='none'||cs.visibility==='hidden'||cs.opacity==='0';var cross=false;" +
        "try{cross=!!f.src&&new URL(f.src,location.href).origin!==location.origin}catch(e){}if(hid&&cross)ifr++}" +
        "var obf=false,sc=d.querySelectorAll('script:not([src])');for(var j=0;j<sc.length&&j<60;j++){var t=sc[j].textContent||'';" +
        "if(t.length>300&&/eval\\s*\\(\\s*(atob|unescape|String\\.fromCharCode)|eval\\(function\\(p,a,c,k,e,d\\)|new Function\\(\\s*atob/.test(t)){obf=true;break}}" +
        "return JSON.stringify({pw:pw,t:d.title||'',h:h1,ifr:ifr,obf:obf})}catch(e){return ''}})()"

    fun evaluate(ctx: Context, host: String, url: String, raw: String?): Verdict? {
        if (!enabled() || raw.isNullOrBlank() || raw == "null") return null
        if (allowed(ctx, host) || privateIp(host)) return null
        val s0 = try { JSONTokener(raw).nextValue() as? String } catch (_: Exception) { null } ?: return null
        val o = try { JSONObject(s0) } catch (_: Exception) { return null }
        var s = 0; var why = ""; var top = 0
        fun add(p: Int, r: String) { s += p; if (p > top) { top = p; why = r } }
        val pw = o.optBoolean("pw")
        val head = (o.optString("t") + " " + o.optString("h")).lowercase()
        if (pw && url.startsWith("http://")) add(60, "نموذج باسورد على اتصال غير مشفّر")
        if (pw) for ((brand, _) in brands) if (Regex("\\b$brand\\b").containsMatchIn(head) && !isBrandHost(brand, host)) { add(50, "نموذج تسجيل دخول بيقلّد $brand"); break }
        if (o.optInt("ifr") >= 3) add(20, "إطارات مخفية من مواقع تانية")
        if (o.optBoolean("obf")) add(35, "سكريبت مشفّر بشدة (eval)")
        return if (s >= threshold()) Verdict(minOf(s, 100), why) else null
    }

    // ───────── البروتوكولات ─────────
    private val blockedTyped = Regex("^(file|content|javascript|ftp|intent|vbscript|data):", RegexOption.IGNORE_CASE)
    fun blockedTypedScheme(input: String) = blockedTyped.containsMatchIn(input.trim())
    /** الروابط الخارجية المسموحة بس لو المستخدم لمسها */
    fun externalSchemeAllowed(scheme: String?) = scheme?.lowercase() in setOf("mailto", "tel", "sms")

    // ───────── القائمة البيضاء وسجل المنع ─────────
    fun allowed(ctx: Context, host: String): Boolean = try { ShieldDatabase.getInstance(ctx).riskAllowed(host) } catch (_: Exception) { false }

    fun log(ctx: Context, host: String, url: String, v: Verdict) {
        val app = ctx.applicationContext
        Thread { try { ShieldDatabase.getInstance(app).addRiskLog(host, url.take(300), v.reason, v.score) } catch (_: Exception) {} }.start()
    }

    fun blockHtml(host: String, v: Verdict): String {
        val esc = android.text.Html.escapeHtml(host)
        val why = android.text.Html.escapeHtml(v.reason)
        return "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><meta charset='utf-8'><title>اتمنع</title></head>" +
            "<body style='margin:0;background:#1a1214;color:#f3e6e6;font-family:sans-serif;display:flex;min-height:100vh;align-items:center;justify-content:center;text-align:center;direction:rtl'>" +
            "<div style='padding:28px;max-width:420px'><div style='font-size:56px'>🛡️</div><h2>الجدار الحديدي منع الصفحة دي</h2>" +
            "<p style='color:#c9a9a9;line-height:1.9'>$esc<br><b>السبب:</b> $why<br>درجة الخطر: ${v.score}/100</p>" +
            "<p style='color:#8f7676;font-size:13px;line-height:1.8'>لو الموقع سليم تقدر تضيفه يدويًا من الإعدادات ← الجدار الحديدي ← القائمة البيضاء.</p></div></body></html>"
    }
}

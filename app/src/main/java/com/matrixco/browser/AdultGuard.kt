package com.matrixco.browser

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import org.json.JSONTokener

/**
 * حجب بتحليل النص: بيقرا عنوان الصفحة ووصفها وعناوينها ونصها ورابطها وكلمات البحث،
 * وبيحجب أي صفحة إباحية حتى لو الموقع مش في أي قائمة. + حجب بكلمات مفتاحية يكتبها المستخدم.
 */
object AdultGuard {
    const val KEY = "fl_keywords"
    const val ADULT = 1
    const val KEYWORD = 2
    class Verdict(val kind: Int, val reason: String, val learn: Boolean)

    /** بيتنفّذ داخل الصفحة وبيرجّع نصوصها */
    const val PROBE = "(function(){try{var d=document;var r='';var ms=d.querySelectorAll('meta');var dm='';" +
        "for(var i=0;i<ms.length;i++){var n=(ms[i].name||'').toLowerCase();var c=ms[i].content||'';" +
        "if(n==='rating')r+=' '+c;if(n==='description'||n==='keywords'||n==='og:title')dm+=' '+c;}" +
        "var h=[].slice.call(d.querySelectorAll('h1,h2')).slice(0,8).map(function(e){return e.innerText}).join(' ');" +
        "var b=(d.body&&d.body.innerText||'').slice(0,8000);" +
        "return JSON.stringify({t:d.title||'',m:dm,r:r,h:h,b:b})}catch(e){return ''}})()"

    private fun norm(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s.lowercase()) {
            val c = ch.code
            if (c in 0x064B..0x065F || c == 0x0640) continue
            sb.append(when (ch) { 'أ', 'إ', 'آ' -> 'ا'; 'ى' -> 'ي'; 'ة' -> 'ه'; else -> ch })
        }
        return sb.toString()
    }

    private val weights: Map<String, Int> = HashMap<String, Int>().apply {
        for (w in listOf("porn", "porno", "pornos", "pornhub", "xxx", "hentai", "blowjob", "blowjobs", "handjob", "cumshot",
            "creampie", "gangbang", "bukkake", "milf", "pussy", "slut", "orgy", "camgirl", "camgirls", "bdsm", "threesome", "sexcam",
            "سكس", "ينيك", "نياكه", "بورن", "بورنو", "شرموطه", "شراميط", "محارم", "طيز", "كس", "متناكه", "متناك", "اباحي", "اباحيه", "قحبه", "سحاق")) put(norm(w), 3)
        for (w in listOf("sex", "sexy", "nude", "nudes", "naked", "erotic", "orgasm", "boobs", "tits", "horny", "cock", "anal", "whore",
            "stripper", "onlyfans", "fetish", "fuck", "fucking", "fucked", "nsfw", "escort", "escorts",
            "نيك", "زب", "عاري", "عاريه", "عاريات", "عاهره", "لواط", "سكسي")) put(norm(w), 2)
        for (w in listOf("adult", "adults", "porno", "lesbian", "milfs", "شهوه", "ساخن", "ساخنه")) if (!containsKey(norm(w))) put(norm(w), 1)
    }

    private val strongSubstr = listOf("porn", "hentai", "xvideos", "xnxx", "xhamster", "blowjob", "cumshot", "camgirl", "redtube", "youporn", "spankbang")
    private val bigGeneral = listOf("reddit.com", "twitter.com", "x.com", "tumblr.com", "wikipedia.org", "youtube.com", "facebook.com",
        "instagram.com", "telegram.org", "t.me", "quora.com", "pinterest.com", "medium.com", "blogspot.com", "wordpress.com", "github.com", "google.com", "bing.com")
    private val searchHosts = listOf("google.", "bing.com", "duckduckgo.com", "yahoo.com", "yandex.", "search.brave.com", "ecosia.org", "startpage.com")

    private val splitter = Regex("[^\\p{L}\\p{N}]+")
    private fun tokens(s: String): List<String> = norm(s).split(splitter).filter { it.isNotEmpty() }

    private fun score(text: String, cap: Int = 5): Int {
        val cnt = HashMap<String, Int>()
        for (t in tokens(text)) if (weights.containsKey(t)) cnt[t] = (cnt[t] ?: 0) + 1
        var sum = 0
        for ((k, v) in cnt) sum += (weights[k] ?: 0) * minOf(v, cap)
        return sum
    }

    private fun urlStrong(url: String): Boolean {
        val d = try { Uri.decode(url) } catch (_: Exception) { url }
        val l = d.lowercase()
        if (strongSubstr.any { l.contains(it) }) return true
        return tokens(d).any { (weights[it] ?: 0) >= 3 }
    }

    const val SCAN_KEY = "adult_text_scan"
    fun textScanOn() = FilterEngine.adultOn() && Cfg.bool(SCAN_KEY, true)

    fun keywords(): List<String> = Cfg.str(KEY, "").lines().map { norm(it.trim()) }.filter { it.length >= 2 }
    fun active(): Boolean = FilterEngine.adultOn() || keywords().isNotEmpty()
    fun isSearchHost(host: String) = searchHosts.any { host.contains(it) }

    private fun countOf(hay: String, needle: String): Int {
        var c = 0; var i = hay.indexOf(needle)
        while (i >= 0 && c < 10) { c++; i = hay.indexOf(needle, i + needle.length) }
        return c
    }

    /** فحص الرابط وكلمات البحث قبل التحميل */
    fun checkUrl(url: String): Verdict? {
        if (!active()) return null
        val u = try { Uri.parse(url) } catch (_: Exception) { return null }
        val host = u.host?.lowercase() ?: return null
        if (host == "mx.blocked" || host == Pages.HOST || AdBlocker.isSiteAllowed(host)) return null
        val kws = keywords()
        if (isSearchHost(host)) {
            val q = listOf("q", "p", "query", "text", "search").mapNotNull { try { u.getQueryParameter(it) } catch (_: Exception) { null } }.joinToString(" ")
            if (q.isBlank()) return null
            val nq = norm(q)
            for (k in kws) if (nq.contains(k)) return Verdict(KEYWORD, k, false)
            if (textScanOn() && score(q) >= 2) return Verdict(ADULT, "كلمات البحث", false)
            return null
        }
        val nu = norm(try { Uri.decode(url) } catch (_: Exception) { url })
        for (k in kws) if (nu.contains(k)) return Verdict(KEYWORD, k, false)
        if (textScanOn() && urlStrong(url)) return Verdict(ADULT, "رابط الصفحة", false)
        return null
    }

    /** تحليل محتوى الصفحة بعد التحميل */
    fun evaluate(host: String, url: String, raw: String?): Verdict? {
        if (raw.isNullOrBlank() || raw == "null") return null
        val s = try { JSONTokener(raw).nextValue() as? String } catch (_: Exception) { null } ?: return null
        if (s.isBlank()) return null
        val o = try { JSONObject(s) } catch (_: Exception) { return null }
        val head = o.optString("t") + " " + o.optString("m") + " " + o.optString("h")
        val body = o.optString("b")
        val kws = keywords()
        if (kws.isNotEmpty()) {
            val nh = norm(head + " " + url); val nb = norm(body)
            for (k in kws) if (nh.contains(k) || countOf(nb, k) >= 3) return Verdict(KEYWORD, k, false)
        }
        if (!textScanOn()) return null
        val rl = o.optString("r").lowercase()
        if (rl.contains("rta-5042-1996-1400-1577-rta") || Regex("\\badult\\b").containsMatchIn(rl)) return Verdict(ADULT, "تصنيف الموقع: للبالغين", true)
        val hs = score(head) * 3 + (if (urlStrong(url)) 9 else 0)
        val bs = score(body)
        if (hs >= 9 || bs >= 14) return Verdict(ADULT, "تحليل محتوى الصفحة", hs >= 9 && bs >= 14)
        return null
    }

    fun canLearn(host: String) = bigGeneral.none { host == it || host.endsWith(".$it") } && !isSearchHost(host)

    fun blockHtml(host: String, v: Verdict): String {
        val esc = android.text.Html.escapeHtml(host)
        val why = if (v.kind == KEYWORD) "فيها كلمة محجوبة: " + android.text.Html.escapeHtml(v.reason) else "حجبها فلتر المحتوى (" + android.text.Html.escapeHtml(v.reason) + ")"
        return "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><meta charset='utf-8'><title>صفحة محجوبة</title></head>" +
            "<body style='margin:0;background:#14162a;color:#e8eaf6;font-family:sans-serif;display:flex;min-height:100vh;align-items:center;justify-content:center;text-align:center;direction:rtl'>" +
            "<div style='padding:28px;max-width:420px'><div style='font-size:56px'>🚫</div><h2>الصفحة دي محجوبة</h2>" +
            "<p style='color:#9aa0c0;line-height:1.8'>$esc<br>$why<br>تقدر تعدّل ده من الإعدادات ← الدرع والخصوصية ← عوامل تصفية المحتوى.</p></div></body></html>"
    }
}

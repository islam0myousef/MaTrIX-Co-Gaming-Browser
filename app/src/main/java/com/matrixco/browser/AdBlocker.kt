package com.matrixco.browser

import android.content.Context
import android.net.Uri
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * MaTrIX Shield v3 — تصنيفات (إعلانات/متتبعات/خبيث/سوشيال/تجسس) + مفاتيح تشغيل لكل تصنيف.
 */
object AdBlocker {

    private val builtinAds = hashSetOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "googletagservices.com", "adnxs.com", "adsrvr.org", "adform.net", "advertising.com", "adcolony.com",
        "applovin.com", "unityads.unity3d.com", "vungle.com", "chartboost.com", "admob.com", "moatads.com",
        "taboola.com", "outbrain.com", "popads.net", "popcash.net", "propellerads.com", "exoclick.com",
        "trafficjunky.net", "zedo.com", "criteo.com", "casalemedia.com", "rubiconproject.com", "pubmatic.com",
        "openx.net", "smartadserver.com", "media.net", "mgid.com", "inmobi.com", "adsterra.com",
        "hilltopads.net", "juicyads.com", "clickadu.com", "onclickads.net", "revcontent.com", "yllix.com",
        "adskeeper.com", "monetag.com", "richpush.co", "ero-advertising.com", "amazon-adsystem.com",
        "2mdn.net", "serving-sys.com", "bidswitch.net", "contextweb.com", "lijit.com", "sharethrough.com",
        "teads.tv", "33across.com", "adsafeprotected.com", "ads-twitter.com", "an.facebook.com"
    )
    private val builtinTrackers = hashSetOf(
        "scorecardresearch.com", "quantserve.com", "mixpanel.com", "appsflyer.com", "adjust.com",
        "branch.io", "kochava.com", "segment.io", "amplitude.com", "bugsnag.com", "sentry-cdn.com"
    )
    /** جهات مراقبة سلوك/تسجيل جلسات/بصمة — ثقة عالية */
    private val builtinSpy = hashSetOf(
        "hotjar.com", "fullstory.com", "mouseflow.com", "smartlook.com", "luckyorange.com", "inspectlet.com",
        "clarity.ms", "logrocket.com", "lr-ingest.com", "lr-in.com", "crazyegg.com", "sessioncam.com",
        "fingerprintjs.com", "fpjs.io", "fpcdn.io", "coinimp.com", "coin-hive.com", "coinhive.com", "crypto-loot.com",
        "authedmine.com", "minero.cc", "webminepool.com", "jsecoin.com", "ppoi.org"
    )
    private val builtinSocial = hashSetOf(
        "platform.twitter.com", "platform.linkedin.com", "widgets.pinterest.com", "assets.pinterest.com",
        "platform.instagram.com", "static.addtoany.com", "addthis.com", "sharethis.com", "disqus.com"
    )

    private val allowed = ConcurrentHashMap.newKeySet<String>()
    private val tempAllowed = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var db: ShieldDatabase? = null

    private val cache = object : LinkedHashMap<String, Int>(2048, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?): Boolean = size > 6000
    }

    private val topCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val pending = AtomicInteger(0)
    private val pendingTrk = AtomicInteger(0)
    val sessionBlocked = AtomicInteger(0)

    private val adHostRegex = Regex("^(ad|ads|adserver|adservice|adtech|adx|banner|banners|pagead|popads|popunder)[0-9]*\\.")
    private val antiAdblock = arrayOf("fuckadblock", "blockadblock", "adblock-detect", "adblockdetect", "detectadblock", "ad-block-detect")
    private val adPatterns = arrayOf(
        "/adserver/", "/adserve/", "/ads/banner", "/pagead/", "/adframe", "/adview/", "/advert/", "/adverts/",
        "adsbygoogle", "/popunder", "/popads", "/prebid", "/ads.js", "/ad.js", "/showads", "/adsense/",
        "/banner-ads", "/sponsored-ads", "/vast.xml", "/vast?", "/ima3.js", "googleads."
    )
    private val secondLevel = setOf("co", "com", "net", "org", "gov", "edu", "ac", "or", "ne")

    fun init(context: Context) {
        Cfg.init(context)
        db = ShieldDatabase.getInstance(context)
        reloadAllowed()
        synchronized(cache) { cache.clear() }
    }

    fun refreshRules(context: Context? = null) { if (context != null) init(context) else synchronized(cache) { cache.clear() } }

    private fun reloadAllowed() {
        try { val d = db ?: return; allowed.clear(); allowed.addAll(d.getAllowedHosts()) } catch (_: Exception) {}
    }

    // ───────── مفاتيح التشغيل ─────────
    fun enabled() = Cfg.bool(Cfg.SHIELD, true)
    fun adsOn() = Cfg.bool(Cfg.ADS, true)
    fun trkOn() = Cfg.bool(Cfg.TRK, true)
    fun malOn() = Cfg.bool(Cfg.MAL, true)
    fun socOn() = Cfg.bool(Cfg.SOC, false)
    fun annoyOn() = Cfg.bool(Cfg.ANNOY, true)
    fun spyOn() = Cfg.bool(Cfg.SPY, true)

    private fun norm(host: String?): String = host.orEmpty().lowercase(Locale.ROOT).trimEnd('.')

    private fun candidates(host: String): List<String> {
        val out = ArrayList<String>(4)
        var h = host
        while (true) {
            out.add(h)
            val i = h.indexOf('.')
            if (i < 0) break
            val rest = h.substring(i + 1)
            if (!rest.contains('.')) break
            h = rest
        }
        return out
    }

    fun regDomain(host: String): String {
        val p = host.split('.')
        if (p.size <= 2) return host
        val last = p[p.size - 1]; val second = p[p.size - 2]
        return if (last.length == 2 && second in secondLevel) p.takeLast(3).joinToString(".") else p.takeLast(2).joinToString(".")
    }

    fun isThirdParty(host: String, pageHost: String?): Boolean {
        val ph = norm(pageHost)
        if (ph.isEmpty()) return false
        return regDomain(host) != regDomain(ph)
    }

    // ───────── السماح ─────────
    fun isSiteAllowed(host: String?): Boolean {
        val h = norm(host)
        if (h.isEmpty()) return false
        if (tempAllowed.contains(h)) return true
        if (allowed.isEmpty()) return false
        return candidates(h).any { allowed.contains(it) }
    }

    fun setSiteAllowed(host: String, allow: Boolean) {
        try { db?.setAllowedHost(norm(host), allow); reloadAllowed() } catch (_: Exception) {}
    }

    fun tempAllow(host: String) { tempAllowed.add(norm(host)) }

    fun blockHost(host: String, cat: Int = Cat.ADS) {
        try { db?.addLocalBlocked(norm(host), cat); synchronized(cache) { cache.clear() } } catch (_: Exception) {}
    }

    // ───────── التصنيف ─────────
    fun categoryOf(host: String?): Int {
        val h = norm(host)
        if (h.isEmpty()) return 0
        val c = candidates(h)
        for (x in c) {
            if (builtinSpy.contains(x)) return Cat.SPY
            if (builtinAds.contains(x)) return Cat.ADS
            if (builtinTrackers.contains(x)) return Cat.TRK
        }
        for (x in c) if (builtinSocial.contains(x)) return Cat.SOC
        synchronized(cache) { cache[h]?.let { return it } }
        var r = try { db?.blockedCategory(c) ?: 0 } catch (_: Exception) { 0 }
        if (r == 0) r = try { db?.flHit(c) ?: 0 } catch (_: Exception) { 0 }
        synchronized(cache) { cache[h] = r }
        return r
    }

    fun isBlockedHost(host: String?): Boolean = categoryOf(host) != 0

    private fun catOn(cat: Int, third: Boolean): Boolean = when (cat) {
        Cat.ADS -> adsOn()
        Cat.TRK -> trkOn()
        Cat.MAL -> malOn()
        Cat.SOC -> socOn() && third
        Cat.SPY -> spyOn()
        else -> false
    }

    /** يرجّع تصنيف الحجب لو الطلب لازم يتحجب، وإلا 0 */
    fun check(uri: Uri, pageHost: String?, mainFrame: Boolean): Int {
        if (!enabled() || mainFrame) return 0
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return 0
        val host = norm(uri.host)
        if (host.isEmpty()) return 0
        if (isSiteAllowed(pageHost) || isSiteAllowed(host)) return 0

        val third = isThirdParty(host, pageHost)
        val cat = categoryOf(host)
        if (cat != 0 && catOn(cat, third)) return cat

        if (adsOn()) {
            if (adHostRegex.containsMatchIn(host)) return Cat.ADS
            val full = uri.toString().lowercase(Locale.ROOT)
            if (annoyOn()) for (p in antiAdblock) if (full.contains(p)) return Cat.ADS
            for (p in adPatterns) if (full.contains(p)) return Cat.ADS
        }
        return 0
    }

    /**
     * فحص تنقّل الصفحة الرئيسية/إعادة التوجيه.
     * لا نطبّق أنماط المسارات العامة هنا حتى لا نحجب صفحات شرعية لمجرد احتواء مسارها على كلمة ads.
     */
    fun checkMainFrame(uri: Uri, pageHost: String?): Int {
        if (!enabled()) return 0
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return 0
        val host = norm(uri.host)
        if (host.isEmpty() || isSiteAllowed(host) || isSiteAllowed(pageHost)) return 0
        val third = isThirdParty(host, pageHost)
        val cat = categoryOf(host)
        if (cat != 0 && catOn(cat, third) && cat != Cat.MAL && cat != Cat.SOC) return cat
        // أسماء خوادم الإعلانات الواضحة فقط؛ تجنب الحجب اعتمادًا على كلمات عامة في URL.
        if (adsOn() && third && adHostRegex.containsMatchIn(host)) return Cat.ADS
        return 0
    }

    fun shouldBlock(uri: Uri, pageHost: String?, mainFrame: Boolean): Boolean = check(uri, pageHost, mainFrame) != 0

    /** هل الصفحة نفسها خبيثة (للتحذير قبل الفتح)؟ */
    fun isMaliciousPage(host: String?): Boolean =
        enabled() && malOn() && !isSiteAllowed(host) && categoryOf(host) == Cat.MAL

    // ───────── الإحصائيات ─────────
    fun noteBlocked(host: String?, cat: Int = Cat.ADS) {
        val h = regDomain(norm(host))
        if (h.isEmpty()) return
        sessionBlocked.incrementAndGet()
        pending.incrementAndGet()
        if (cat == Cat.TRK || cat == Cat.SPY) pendingTrk.incrementAndGet()
        topCounts.getOrPut(h) { AtomicInteger(0) }.incrementAndGet()
    }

    fun flush() {
        val d = db ?: return
        try {
            val n = pending.getAndSet(0)
            if (n > 0) d.addBlocked(n)
            val t = pendingTrk.getAndSet(0)
            if (t > 0) d.addCounter("trackers_total", t)
            if (topCounts.isNotEmpty()) {
                val snap = HashMap<String, Int>()
                for (k in topCounts.keys.toList()) { val v = topCounts.remove(k)?.get() ?: 0; if (v > 0) snap[k] = v }
                d.bumpTop(snap)
            }
        } catch (_: Exception) {}
    }
}

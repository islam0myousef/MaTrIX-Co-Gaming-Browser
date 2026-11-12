package com.matrixco.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

enum class FlKind { ADBLOCK, HOSTS }

/** group: 0 = عامة، 1 = حسب اللغة، 2 = مخصصة */
data class FilterList(
    val id: String, val title: String, val source: String, val url: String,
    val group: Int, val kind: FlKind = FlKind.ADBLOCK, val cat: Int = Cat.ADS, val defOn: Boolean = false,
    val alt: List<String> = emptyList()
)

/**
 * عوامل تصفية المحتوى (بأسلوب Brave): قوائم Adblock جاهزة + قوائم مخصصة + قواعد يكتبها المستخدم.
 * - قواعد ||domain^ وملفات hosts → حجب نطاقات (هاش في قاعدة البيانات)
 * - قواعد ##selector → إخفاء عناصر (CSS بسيط فقط، القواعد الإجرائية بتتجاهل)
 */
object FilterEngine {
    private const val CUSTOM_KEY = "fl_custom_lists"
    const val RULES_KEY = "fl_custom_rules"
    private const val RULES_ID = "custom_rules"
    private const val MAX_COSM = 60000
    private const val GENERIC_CAP = 6000
    private const val SPECIFIC_CAP = 2000

    private fun ag(n: Int) = "https://filters.adtidy.org/extension/ublock/filters/$n.txt"
    private fun gh(p: String) = "https://raw.githubusercontent.com/$p"
    private fun abp(f: String) = "https://easylist-downloads.adblockplus.org/$f"

    val catalog: List<FilterList> = listOf(
        FilterList("cookie", "Cookie notice blocker", "EasyList Cookie", "https://secure.fanboy.co.nz/fanboy-cookiemonster.txt", 0, defOn = true),
        FilterList("fb_annoy", "Annoying distractions blocker", "Fanboy's Annoyances", "https://secure.fanboy.co.nz/fanboy-annoyance.txt", 0),
        FilterList("ubo_annoy", "Annoying distractions blocker (uBO)", "uBO Annoyances", gh("uBlockOrigin/uAssets/master/filters/annoyances-others.txt"), 0),
        FilterList("fb_social", "Social media blocker", "Fanboy's Social", "https://secure.fanboy.co.nz/fanboy-social.txt", 0),
        FilterList("easylist_c", "إخفاء الإعلانات (EasyList)", "EasyList — قواعد إخفاء العناصر", "https://easylist.to/easylist/easylist.txt", 0),
        FilterList("adguard_base", "إخفاء الإعلانات (AdGuard Base)", "AdGuard Base filter", ag(2), 0),
        FilterList("ubo_filters", "إخفاء الإعلانات (uBlock filters)", "uBlock Origin filters", gh("uBlockOrigin/uAssets/master/filters/filters.txt"), 0),
        FilterList("porn", "Porn blocker", "StevenBlack Porn-only (+ بدائل)", gh("StevenBlack/hosts/master/alternates/porn-only/hosts"), 0, kind = FlKind.HOSTS,
            alt = listOf(
                "https://blocklistproject.github.io/Lists/alt-version/porn-nl.txt",
                gh("Sinfonietta/hostfiles/master/pornography-hosts"),
                gh("blocklistproject/Lists/master/porn.txt")
            )),

        FilterList("ar", "Arabic website ad blocker", "Liste AR", abp("Liste_AR.txt"), 1, defOn = true),
        FilterList("bg", "Bulgarian website ad blocker", "Bulgarian Adblock list", "https://stanev.org/abp/adblock_bg.txt", 1),
        FilterList("zh", "Chinese website ad blocker", "AdGuard Chinese", ag(224), 1),
        FilterList("zh_annoy", "Chinese website annoyances blocker", "CJX's Annoyance", gh("cjx82630/cjxlist/master/cjx-annoyance.txt"), 1),
        FilterList("cs", "Czech and Slovak website ad blocker", "EasyList Czech and Slovak", gh("tomasko126/easylistczechandslovak/master/filters.txt"), 1),
        FilterList("nl", "Dutch website ad blocker", "AdGuard Dutch", ag(8), 1),
        FilterList("et", "Estonian website ad blocker", "Eesti saitidele kohandatud filter", "https://adblock.ee/list.php", 1),
        FilterList("fi", "Finnish website ad blocker", "EasyList Finnish", gh("finnish-easylist-addition/finnish-easylist-addition/master/Finland_adb.txt"), 1),
        FilterList("fr", "French website ad blocker", "AdGuard Français", ag(16), 1),
        FilterList("de", "German website ad blocker", "EasyList Germany", "https://easylist.to/easylistgermany/easylistgermany.txt", 1),
        FilterList("el", "Greek website ad blocker", "Greek AdBlock Filter", gh("kargig/greek-adblockplus-filter/master/void-gr-filters.txt"), 1),
        FilterList("he", "Hebrew website ad blocker", "EasyList Hebrew", gh("easylist/EasyListHebrew/master/EasyListHebrew.txt"), 1),
        FilterList("hi", "Hindi website ad blocker", "IndianList", abp("indianlist.txt"), 1),
        FilterList("hu", "Hungarian website ad blocker", "Hufilter", "https://cdn.jsdelivr.net/gh/hufilter/hufilter@gh-pages/hufilter-ublock.txt", 1),
        FilterList("is", "Icelandic website ad blocker", "Icelandic ABP List", "https://adblock.gardar.net/is.abp.txt", 1),
        FilterList("id", "Indonesian website ad blocker", "ABPindo", gh("ABPindo/indonesianadblockrules/master/subscriptions/abpindo.txt"), 1),
        FilterList("it", "Italian website ad blocker", "EasyList Italy", abp("easylistitaly.txt"), 1),
        FilterList("ja", "Japanese website ad blocker", "Adguard Japanese filters", ag(7), 1),
        FilterList("lv", "Latvian website ad blocker", "Latvian List", gh("Latvian-List/adblock-latvian/master/lists/latvian-list.txt"), 1),
        FilterList("lt", "Lithuanian website ad blocker", "EasyList Lithuania", gh("EasyList-Lithuania/easylist_lithuania/master/easylistlithuania.txt"), 1),
        FilterList("mk", "Macedonian website ad blocker", "Macedonian adBlock Filters", gh("DeepSpaceHarbor/Macedonian-adBlock-Filters/master/Filters"), 1),
        FilterList("nordic", "Nordic website ad blocker", "Dandelion Sprout's Nordic filters", gh("DandelionSprout/adfilt/master/NorwegianExperimentalList%20alternate%20versions/NordicFiltersABP-Inclusion.txt"), 1),
        FilterList("fa", "Persian website ad blocker", "PersianBlocker", gh("MasterKia/PersianBlocker/main/PersianBlocker.txt"), 1),
        FilterList("pl", "Polish website ad blocker", "Oficjalne Polskie Filtry do AdBlocka", gh("MajkiIT/polish-ads-filter/master/polish-adblock-filters/adblock.txt"), 1),
        FilterList("ro", "Romanian website ad blocker", "Romanian Ad (ROad) Block List Light", gh("tcptomato/ROad-Block/master/road-block-filters-light.txt"), 1),
        FilterList("ru_ag", "Russian website ad blocker - AdGuard Russian", "AdGuard Russian filter", ag(1), 1),
        FilterList("ru", "Russian website ad blocker - RU AdList", "RU List", abp("advblock.txt"), 1),
        FilterList("sl", "Slovenian website ad blocker", "Slovenian List", gh("betterwebleon/slovenian-filter/master/filters.txt"), 1),
        FilterList("es", "Spanish website ad blocker", "EasyList Spanish", abp("easylistspanish.txt"), 1),
        FilterList("es_pt", "Spanish and Portuguese website ad blocker", "AdGuard Spanish/Portuguese", ag(9), 1),
        FilterList("sv", "Swedish website ad blocker", "Frellwit's Swedish Filter", gh("lassekongo83/Frellwits-filter-lists/master/Frellwits-Swedish-Filter.txt"), 1),
        FilterList("th", "Thai website ad blocker", "EasyList Thailand", gh("easylist-thailand/easylist-thailand/master/easylist-thailand.txt"), 1),
        FilterList("tr", "Turkish website ad blocker", "AdGuard Turkish", ag(13), 1)
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var genericCache: List<String>? = null
    private val loading = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun lid(id: String): Int = id.hashCode()
    fun isOn(l: FilterList) = Cfg.bool("fl_on_" + l.id, l.defOn)
    fun setOn(l: FilterList, v: Boolean) = Cfg.setBool("fl_on_" + l.id, v)

    // ───────── القوائم المخصصة ─────────
    fun customLists(): List<FilterList> = try {
        val a = JSONArray(Cfg.str(CUSTOM_KEY, "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); FilterList("c_" + o.getString("u").hashCode(), o.getString("n"), o.getString("u"), o.getString("u"), 2, defOn = true) }
    } catch (_: Exception) { emptyList() }

    fun addCustom(name: String, url: String): FilterList {
        val a = try { JSONArray(Cfg.str(CUSTOM_KEY, "[]")) } catch (_: Exception) { JSONArray() }
        a.put(JSONObject().put("n", name).put("u", url))
        Cfg.setStr(CUSTOM_KEY, a.toString())
        return customLists().first { it.url == url }
    }

    fun removeCustom(ctx: Context, l: FilterList) {
        val a = try { JSONArray(Cfg.str(CUSTOM_KEY, "[]")) } catch (_: Exception) { JSONArray() }
        val out = JSONArray()
        for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (o.getString("u") != l.url) out.put(o) }
        Cfg.setStr(CUSTOM_KEY, out.toString())
        executor.execute { unload(ctx, l) }
    }

    fun all(): List<FilterList> = catalog + customLists()

    fun status(ctx: Context, l: FilterList): String {
        if (!isOn(l)) return "متوقفة"
        if (loading.contains(l.id)) return "⏳ جاري التحميل..."
        val m = try { ShieldDatabase.getInstance(ctx).getMeta("fl_st_" + l.id) } catch (_: Exception) { null }
        return m?.takeIf { it.isNotBlank() } ?: "في انتظار التحميل"
    }

    // ───────── التحميل والتحليل ─────────
    private val PROCEDURAL = listOf(
        ":has-text(", ":-abp-", ":xpath(", ":matches-", ":upward(", ":remove(", ":style(", ":contains(",
        ":nth-ancestor(", ":min-text-length(", ":watch-attr(", ":others(", ":-js(", "{", "}", ";", "url(", "expression("
    )

    private fun cosmetic(line: String, i: Int, out: MutableList<Pair<Long, String>>) {
        if (out.size >= MAX_COSM) return
        val domPart = line.substring(0, i).trim()
        val sel = line.substring(i + 2).trim()
        if (sel.isEmpty() || sel.length > 300 || sel[0] == '+' || sel[0] == '^') return
        if (PROCEDURAL.any { sel.contains(it) }) return
        if (domPart.isEmpty()) { out.add(0L to sel); return }
        val ds = domPart.split(',')
        if (ds.any { it.isEmpty() || it.startsWith("~") || it.contains('*') || it.contains('/') || it.contains(' ') }) return
        for (d in ds) out.add(Hash.of(d.trim()) to sel)
    }

    private val bareDomain = Regex("^[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}$")

    private fun parseInto(raw: String, kind: FlKind, bare: Boolean, doms: LongList, cosm: MutableList<Pair<Long, String>>) {
        if (kind == FlKind.HOSTS) { RuleUpdater.parseHostsLine(raw) { doms.add(Hash.of(it)) }; RuleUpdater.parseDomainLine(raw) { doms.add(Hash.of(it)) }; return }
        val line = raw.trim()
        if (line.isEmpty() || line[0] == '!' || line[0] == '[' || line.startsWith("@@")) return
        if (line.contains("#@#") || line.contains("#?#") || line.contains("#$#") || line.contains("#%#")) return
        val i = line.indexOf("##")
        if (i >= 0) { cosmetic(line, i, cosm); return }
        if (bare && bareDomain.matches(line.lowercase())) { doms.add(Hash.of(line.lowercase())); return }
        RuleUpdater.parseAdblockLine(line) { doms.add(Hash.of(it)) }
    }

    private fun store(ctx: Context, l: FilterList, doms: LongList, cosm: List<Pair<Long, String>>): Boolean {
        val db = ShieldDatabase.getInstance(ctx)
        val d = doms.distinctSorted()
        if (!db.flReplace(lid(l.id), l.cat, d, cosm)) return false
        db.setMeta("fl_st_" + l.id, "✓ " + d.size + " نطاق • " + cosm.size + " قاعدة إخفاء")
        genericCache = null
        return true
    }

    private fun load(ctx: Context, l: FilterList): Boolean {
        val db = ShieldDatabase.getInstance(ctx)
        var err = ""
        for (url in listOf(l.url) + l.alt) {
            val doms = LongList(); val cosm = ArrayList<Pair<Long, String>>()
            var lines = 0
            val ok = RuleUpdater.streamLines(url, 80_000_000) { line -> lines++; parseInto(line, l.kind, false, doms, cosm) }
            if (ok && lines >= 5 && (doms.n > 0 || cosm.isNotEmpty())) return store(ctx, l, doms, cosm)
            err = if (!ok) RuleUpdater.lastError else "القائمة فاضية أو بصيغة مش مدعومة"
        }
        db.setMeta("fl_st_" + l.id, "⚠ فشل التحميل: " + err)
        return false
    }

    private fun unload(ctx: Context, l: FilterList) {
        try {
            val db = ShieldDatabase.getInstance(ctx)
            db.flDelete(lid(l.id)); db.setMeta("fl_st_" + l.id, ""); genericCache = null
            AdBlocker.refreshRules(ctx)
        } catch (_: Exception) {}
    }

    fun loadAsync(ctx: Context, l: FilterList, cb: (Boolean) -> Unit) {
        val app = ctx.applicationContext
        if (!loading.add(l.id)) return
        executor.execute {
            val ok = try { load(app, l) } catch (_: Throwable) { false }
            if (ok) AdBlocker.refreshRules(app)
            loading.remove(l.id)
            main.post { cb(ok) }
        }
    }

    /** أي قائمة مفعّلة ومتحمّلتش لسه — بتتحمّل أول ما تفتح الشاشة */
    fun loadPending(ctx: Context, onDone: (FilterList) -> Unit) {
        for (l in all()) {
            if (!isOn(l) || loading.contains(l.id)) continue
            val m = try { ShieldDatabase.getInstance(ctx).getMeta("fl_st_" + l.id) } catch (_: Exception) { null }
            if (m.isNullOrBlank()) loadAsync(ctx, l) { onDone(l) }
        }
    }

    fun disableAsync(ctx: Context, l: FilterList, cb: () -> Unit) {
        val app = ctx.applicationContext
        executor.execute { unload(app, l); main.post { cb() } }
    }

    /** يحدّث كل القوائم المفعّلة (بيتنادى من تحديث القوائم اليومي) */
    fun refreshEnabled(ctx: Context) {
        for (l in all()) if (isOn(l)) try { load(ctx, l) } catch (_: Throwable) {}
        try { applyCustomRules(ctx) } catch (_: Throwable) {}
        genericCache = null
        AdBlocker.refreshRules(ctx)
    }

    fun refreshAsync(ctx: Context, cb: (Int, Int) -> Unit) {
        val app = ctx.applicationContext
        executor.execute {
            var ok = 0; var total = 0
            for (l in all()) if (isOn(l)) { total++; if (try { load(app, l) } catch (_: Throwable) { false }) ok++ }
            try { applyCustomRules(app) } catch (_: Throwable) {}
            genericCache = null
            AdBlocker.refreshRules(app)
            main.post { cb(ok, total) }
        }
    }

    // ───────── القواعد المخصصة (نص) ─────────
    fun applyCustomRules(ctx: Context): Int {
        val text = Cfg.str(RULES_KEY, "")
        val l = FilterList(RULES_ID, "قواعدك", "", "", 2)
        val doms = LongList(); val cosm = ArrayList<Pair<Long, String>>()
        for (line in text.lines()) parseInto(line, FlKind.ADBLOCK, true, doms, cosm)
        val db = ShieldDatabase.getInstance(ctx)
        if (text.isBlank()) { db.flDelete(lid(RULES_ID)); genericCache = null; return 0 }
        store(ctx, l, doms, cosm)
        return doms.n + cosm.size
    }

    fun applyCustomRulesAsync(ctx: Context, cb: (Int) -> Unit) {
        val app = ctx.applicationContext
        executor.execute {
            val n = try { applyCustomRules(app) } catch (_: Throwable) { -1 }
            AdBlocker.refreshRules(app)
            main.post { cb(n) }
        }
    }

    // ───────── إخفاء العناصر ─────────
    private fun candidates(host: String): List<String> {
        val out = ArrayList<String>(4); var h = host.lowercase().trimEnd('.')
        while (true) {
            out.add(h)
            val i = h.indexOf('.'); if (i < 0) break
            val rest = h.substring(i + 1); if (!rest.contains('.')) break
            h = rest
        }
        return out
    }

    fun cosmeticFor(ctx: Context, host: String): List<String> {
        val db = ShieldDatabase.getInstance(ctx)
        val g = genericCache ?: db.flCosmetic(listOf(0L), GENERIC_CAP).also { genericCache = it }
        val spec = if (host.isEmpty()) emptyList() else db.flCosmetic(candidates(host).map { Hash.of(it) }, SPECIFIC_CAP)
        return g + spec
    }

    // ───────── حجب المواقع الإباحية + البحث الآمن (مربوطين بمفتاح Porn blocker) ─────────
    private val adultPattern = Regex("(porn|xvideos|xnxx|xhamster|redtube|youporn|spankbang|brazzers|(^|[.-])xxx([.-]|$))")

    fun adultOn(): Boolean = isOn(catalog.first { it.id == "porn" })

    fun isAdult(ctx: Context, host: String): Boolean {
        if (!adultOn() || host.isEmpty()) return false
        val h = host.lowercase()
        if (adultPattern.containsMatchIn(h)) return true
        return try {
            val db = ShieldDatabase.getInstance(ctx); val c = candidates(h)
            db.flHitList(c, lid("porn")) || db.flHitList(c, lid("porn_learned"))
        } catch (_: Exception) { false }
    }

    /** موقع اتحجب بتحليل المحتوى — بيتحفظ عشان يتحجب فورًا المرة الجاية */
    fun learnAdult(ctx: Context, host: String) {
        if (!AdultGuard.canLearn(host)) return
        try { ShieldDatabase.getInstance(ctx).flAdd(lid("porn_learned"), Cat.ADS, Hash.of(host.removePrefix("www."))); AdBlocker.refreshRules(ctx) } catch (_: Exception) {}
    }
    fun learnedCount(ctx: Context): Int = try { ShieldDatabase.getInstance(ctx).flCount(lid("porn_learned")) } catch (_: Exception) { 0 }
    fun clearLearned(ctx: Context) { try { ShieldDatabase.getInstance(ctx).flDelete(lid("porn_learned")); AdBlocker.refreshRules(ctx) } catch (_: Exception) {} }

    /** بيفرض البحث الآمن في جوجل وBing وDuckDuckGo لما Porn blocker يكون شغال */
    fun safeSearch(url: String): String {
        if (!adultOn() || !url.startsWith("http")) return url
        return try {
            val u = android.net.Uri.parse(url)
            val host = (u.host ?: return url).lowercase()
            val (key, value) = when {
                host.startsWith("www.google.") || host.startsWith("google.") -> if (u.path?.startsWith("/search") == true) "safe" to "active" else return url
                host.endsWith("bing.com") -> if (u.path?.startsWith("/search") == true || u.path?.startsWith("/images") == true || u.path?.startsWith("/videos") == true) "adlt" to "strict" else return url
                host.endsWith("duckduckgo.com") -> "kp" to "1"
                else -> return url
            }
            if (u.getQueryParameter(key) == value) return url
            if (u.query.isNullOrEmpty() && key != "kp") return url
            val b = u.buildUpon().clearQuery()
            for (n in u.queryParameterNames) if (n != key) for (v in u.getQueryParameters(n)) b.appendQueryParameter(n, v)
            b.appendQueryParameter(key, value).build().toString()
        } catch (_: Exception) { url }
    }
}

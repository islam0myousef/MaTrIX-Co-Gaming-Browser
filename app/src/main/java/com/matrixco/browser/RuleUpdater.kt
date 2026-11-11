package com.matrixco.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

/** مصفوفة long قابلة للنمو (بدل HashSet<String>) — أخف بكتير في الذاكرة */
class LongList {
    private var a = LongArray(4096)
    var n = 0
    fun add(v: Long) { if (n == a.size) a = a.copyOf(n * 2); a[n++] = v }
    fun appendAllTo(target: LongList) { for (i in 0 until n) target.add(a[i]) }
    fun distinctSorted(): LongArray {
        val c = a.copyOf(n); c.sort()
        var m = 0
        for (i in c.indices) if (i == 0 || c[i] != c[i - 1]) c[m++] = c[i]
        return c.copyOf(m)
    }
}

object RuleUpdater {

    private const val MAX_CHARS = 16_000_000
    private const val MIN_PER_SOURCE = 300
    const val UPDATE_INTERVAL_MS = 6L * 60 * 60 * 1000

    private const val META_LAST_UPDATE = "remote_last_update"
    private const val META_LAST_MS = "remote_last_ms"
    private const val META_LAST_COUNT = "remote_last_count"
    private const val META_LAST_MSG = "remote_last_msg"
    private const val META_LISTS_OK = "remote_lists_ok"

    enum class Kind { HOSTS, DOMAINS, ADBLOCK }

    /** level: 1 أساسية (منخفض) • 2 موصى بها (متوسط) • 3 قوية (عالي) • 9 اختيارية */
    data class Source(val id: String, val name: String, val url: String, val kind: Kind, val cat: Int, val level: Int, val note: String)

    val sources: List<Source> = listOf(
        Source("stevenblack", "StevenBlack", "https://cdn.jsdelivr.net/gh/StevenBlack/hosts@master/hosts", Kind.HOSTS, Cat.ADS, 1, "إعلانات ومتتبعات — قائمة موحّدة (~80 ألف نطاق)، الأساس"),
        Source("pgl", "Peter Lowe", "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext", Kind.HOSTS, Cat.ADS, 1, "إعلانات — خفيفة جدًا (~3 آلاف)"),
        Source("adaway", "AdAway", "https://adaway.org/hosts.txt", Kind.HOSTS, Cat.ADS, 1, "إعلانات الموبايل (~6 آلاف)"),
        Source("oisd", "OISD", "https://big.oisd.nl/domainswild", Kind.DOMAINS, Cat.ADS, 2, "شاملة وقليلة الأخطاء (~200 ألف)"),
        Source("adguarddns", "AdGuard DNS", "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt", Kind.ADBLOCK, Cat.ADS, 2, "إعلانات ومتتبعات (~50 ألف)"),
        Source("easylist", "EasyList", "https://easylist.to/easylist/easylist.txt", Kind.ADBLOCK, Cat.ADS, 2, "قائمة إعلانات الويب الأشهر"),
        Source("easyprivacy", "EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt", Kind.ADBLOCK, Cat.TRK, 2, "متتبعات وتحليلات"),
        Source("urlhaus", "URLhaus", "https://urlhaus.abuse.ch/downloads/hostfile/", Kind.HOSTS, Cat.MAL, 2, "برمجيات خبيثة (بتتحدث كل ساعة)"),
        Source("hagezi", "HaGeZi Pro", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@main/hosts/pro.txt", Kind.HOSTS, Cat.ADS, 3, "قوية وحديثة للإعلانات والتتبع (~150 ألف)"),
        Source("frogeye", "متتبعات الطرف الأول", "https://hostfiles.frogeye.fr/firstparty-trackers-hosts.txt", Kind.HOSTS, Cat.TRK, 3, "بتكشف المتتبعات المتخفية جوه دومين الموقع نفسه (CNAME)"),
        Source("phishing", "Phishing Army", "https://phishing.army/download/phishing_army_blocklist.txt", Kind.DOMAINS, Cat.MAL, 3, "مواقع التصيد"),
        Source("nocoin", "NoCoin", "https://cdn.jsdelivr.net/gh/hoshsadiq/adblock-nocoin-list@master/nocoin.txt", Kind.ADBLOCK, Cat.MAL, 3, "تعدين العملات الرقمية في الخفاء"),
        Source("devdan", "Developer Dan", "https://www.github.developerdan.com/hosts/lists/ads-and-tracking-extended.txt", Kind.HOSTS, Cat.ADS, 3, "إعلانات وتتبع موسّعة"),
        Source("pollock", "Dan Pollock", "https://someonewhocares.org/hosts/zero/hosts", Kind.HOSTS, Cat.ADS, 3, "قائمة شاملة قديمة وموثوقة"),
        Source("ubo", "uBlock filters", "https://cdn.jsdelivr.net/gh/uBlockOrigin/uAssets@master/filters/filters.txt", Kind.ADBLOCK, Cat.ADS, 9, "اختيارية — أغلبها قواعد إخفاء، النطاقات فيها قليلة")
    )

    private const val KEY_LEVEL = "bl_level"
    private const val META_STATUS = "remote_status"

    fun isOn(s: Source) = Cfg.bool("bl_on_" + s.id, s.level <= 2)
    fun setOn(s: Source, v: Boolean) { Cfg.setBool("bl_on_" + s.id, v); Cfg.setInt(KEY_LEVEL, 0) }
    fun enabledSources(): List<Source> = sources.filter { isOn(it) }

    /** مستوى جاهز: 1 منخفض • 2 متوسط (موصى به) • 3 عالي */
    fun applyPreset(level: Int) {
        for (s in sources) Cfg.setBool("bl_on_" + s.id, s.level <= level)
        Cfg.setInt(KEY_LEVEL, level)
    }
    fun currentLevel(): Int = Cfg.int(KEY_LEVEL, 2)

    /** حالة آخر تحديث لكل قائمة: id -> "ok:N" أو "fail:سبب" */
    fun statusOf(ctx: Context, id: String): String? {
        val m = ShieldDatabase.getInstance(ctx).getMeta(META_STATUS) ?: return null
        for (e in m.split('|')) { val i = e.indexOf('='); if (i > 0 && e.substring(0, i) == id) return e.substring(i + 1) }
        return null
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var running = false

    data class UpdateResult(val success: Boolean, val domainsCount: Int, val message: String)

    fun updateNow(context: Context, callback: (UpdateResult) -> Unit) {
        if (running) return
        running = true
        val app = context.applicationContext
        executor.execute {
            val result = try { performUpdate(app) } catch (e: Exception) { UpdateResult(false, 0, "فشل التحديث: ${e.message ?: "خطأ"}") }
            try { FilterEngine.refreshEnabled(app) } catch (_: Throwable) {}
            running = false
            mainHandler.post { callback(result) }
        }
    }

    /** Synchronous entry point used only by the WorkManager worker. */
    internal fun performScheduledUpdate(context: Context): UpdateResult {
        val result = performUpdate(context.applicationContext)
        if (result.success) {
            try { FilterEngine.refreshEnabled(context.applicationContext) } catch (_: Throwable) {}
            try { AdBlocker.refreshRules(context.applicationContext) } catch (_: Throwable) {}
        }
        return result
    }

    /** مجموعة ثريدز للتحميل المتوازي — القوائم بتتحمّل كلها مع بعض بدل واحدة ورا التانية، فالدرع بيبقى جاهز خلال ثواني بدل دقايق */
    private val fetchPool = Executors.newFixedThreadPool(6)

    private data class SourceResult(val source: Source, val success: Boolean, val count: Int, val list: LongList, val err: String)

    private fun performUpdate(context: Context): UpdateResult {
        val active = enabledSources()
        if (active.isEmpty()) return UpdateResult(false, 0, "اختار قايمة واحدة على الأقل")

        val futures = active.map { s ->
            fetchPool.submit<SourceResult> {
                val local = LongList()
                var count = 0
                val sink: (String) -> Unit = { d -> local.add(Hash.of(d)); count++ }
                val success = try {
                    streamLines(s.url) { line ->
                        when (s.kind) {
                            Kind.HOSTS -> parseHostsLine(line, sink)
                            Kind.DOMAINS -> parseDomainLine(line, sink)
                            Kind.ADBLOCK -> parseAdblockLine(line, sink)
                        }
                    }
                } catch (e: Exception) { lastError = (e.javaClass.simpleName + " " + (e.message ?: "")).take(60); false }
                SourceResult(s, success, count, local, lastError)
            }
        }

        val lists = HashMap<Int, LongList>()
        val ok = ArrayList<String>()
        val report = ArrayList<String>()
        var total = 0
        val status = ArrayList<String>()
        for (f in futures) {
            val r = try { f.get() } catch (e: Exception) { null } ?: continue
            if (!r.success || r.count < MIN_PER_SOURCE) {
                status.add(r.source.id + "=fail:" + (if (!r.success) r.err else "قليلة (${r.count})").replace('|', ' ').replace('=', ' '))
                report.add(r.source.name + ": فشل")
            } else {
                ok.add(r.source.name); total += r.count
                status.add(r.source.id + "=ok:" + r.count)
                report.add(r.source.name + ": " + r.count)
                r.list.appendAllTo(lists.getOrPut(r.source.cat) { LongList() })
            }
        }

        if (ok.isEmpty() || total < 1000) return UpdateResult(false, 0, "التحديث فشل — القواعد القديمة محفوظة. " + report.joinToString(" | "))

        val sets = HashMap<Int, LongArray>()
        for ((c, l) in lists) sets[c] = l.distinctSorted()
        lists.clear()

        val db = ShieldDatabase.getInstance(context)
        if (!db.replaceRemote(sets)) return UpdateResult(false, 0, "قاعدة البيانات رفضت التحديث — القواعد القديمة محفوظة")
        AdBlocker.refreshRules(context)
        db.vacuum()

        val now = System.currentTimeMillis()
        val unique = sets.values.sumOf { it.size }
        db.setMeta(META_LAST_UPDATE, java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(java.util.Date(now)))
        db.setMeta(META_LAST_MS, now.toString())
        db.setMeta(META_LAST_COUNT, unique.toString())
        db.setMeta(META_LISTS_OK, ok.joinToString(","))
        db.setMeta(META_STATUS, status.joinToString("|"))
        val msg = "تم التحديث (" + ok.size + "/" + active.size + " قوائم) — " + unique + " نطاق"
        db.setMeta(META_LAST_MSG, msg + " | " + report.joinToString(" | "))
        return UpdateResult(true, unique, msg)
    }

    /** قراءة سطر بسطر بدون تحميل الملف كله في الذاكرة */
    private val lastErrorTL = ThreadLocal<String>()
    internal var lastError: String
        get() = lastErrorTL.get() ?: ""
        set(v) { lastErrorTL.set(v) }

    internal fun streamLines(address: String, maxChars: Int = MAX_CHARS, onLine: (String) -> Unit): Boolean {
        lastError = ""
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(address).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 40000
            conn.setRequestProperty("User-Agent", "MaTrIX-Co-Browser/3.0")
            conn.instanceFollowRedirects = true
            if (conn.responseCode != HttpURLConnection.HTTP_OK) { lastError = "HTTP " + conn.responseCode; return false }
            var chars = 0
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8), 32768).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    chars += line.length + 1
                    if (chars > maxChars) { lastError = "الملف أكبر من الحد"; return false }
                    onLine(line)
                }
            }
            true
        } catch (e: Exception) { lastError = (e.javaClass.simpleName + " " + (e.message ?: "")).take(60); false } finally { conn?.disconnect() }
    }

    internal fun parseHostsLine(raw: String, sink: (String) -> Unit) {
        val line = raw.substringBefore('#').trim()
        if (line.isEmpty()) return
        val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
        if (parts.size < 2) return
        val a = parts[0]
        if (a != "0.0.0.0" && a != "127.0.0.1" && a != "::") return
        for (i in 1 until parts.size) normalizeDomain(parts[i])?.let(sink)
    }

    internal fun parseDomainLine(raw: String, sink: (String) -> Unit) {
        var line = raw.trim()
        if (line.isEmpty() || line[0] == '#' || line[0] == '!' || line[0] == '[') return
        if (line.startsWith("*.")) line = line.substring(2) else if (line.contains('*')) return
        if (line.contains('/') || line.contains('^') || line.contains('$') || line.contains('@') || line.contains('#')) return
        normalizeDomain(line)?.let(sink)
    }

    /** ||domain^ — بنتجاهل القواعد المشروطة */
    internal fun parseAdblockLine(raw: String, sink: (String) -> Unit) {
        val line = raw.trim()
        if (!line.startsWith("||")) return
        val rest = line.substring(2)
        val caret = rest.indexOf('^')
        if (caret < 0) return
        val body = rest.substring(0, caret)
        val opts = rest.substring(caret + 1)
        if (opts.isNotEmpty()) {
            if (!opts.startsWith("$")) return
            if (opts.contains("domain=") || opts.contains("badfilter") || opts.contains("~") || opts.contains("redirect") ||
                opts.contains("removeparam") || opts.contains("csp=") || opts.contains("document")
            ) return
        }
        if (body.contains('/') || body.contains('*') || body.contains(':') || body.contains('$')) return
        normalizeDomain(body)?.let(sink)
    }

    private val ipv4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

    private fun normalizeDomain(value: String): String? {
        val d = value.trim().lowercase(Locale.ROOT).trimEnd('.')
        if (d.length !in 3..253) return null
        if (ipv4.matches(d)) return null
        val labels = d.split('.')
        if (labels.size < 2) return null
        for (l in labels) {
            if (l.isEmpty() || l.length > 63) return null
            if (!l.first().isLetterOrDigit() || !l.last().isLetterOrDigit()) return null
            for (ch in l) if (ch.code >= 128 || !(ch.isLetterOrDigit() || ch == '-')) return null
        }
        if (d == "localhost" || d.endsWith(".localhost")) return null
        return d
    }

    fun getLastUpdate(context: Context): String? = ShieldDatabase.getInstance(context).getMeta(META_LAST_UPDATE)
    fun getLastCount(context: Context): Int = ShieldDatabase.getInstance(context).getMeta(META_LAST_COUNT)?.toIntOrNull() ?: 0
    fun getLastMessage(context: Context): String? = ShieldDatabase.getInstance(context).getMeta(META_LAST_MSG)
    fun getLastMs(context: Context): Long = ShieldDatabase.getInstance(context).getMeta(META_LAST_MS)?.toLongOrNull() ?: 0L
    fun activeLists(context: Context): List<String> =
        ShieldDatabase.getInstance(context).getMeta(META_LISTS_OK)?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
    fun isDue(context: Context): Boolean = System.currentTimeMillis() - getLastMs(context) > UPDATE_INTERVAL_MS
}

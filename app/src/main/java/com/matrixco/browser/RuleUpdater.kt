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
    const val UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000

    private const val META_LAST_UPDATE = "remote_last_update"
    private const val META_LAST_MS = "remote_last_ms"
    private const val META_LAST_COUNT = "remote_last_count"
    private const val META_LAST_MSG = "remote_last_msg"
    private const val META_LISTS_OK = "remote_lists_ok"

    private enum class Kind { HOSTS, DOMAINS, ADBLOCK }
    private data class Source(val name: String, val url: String, val kind: Kind, val cat: Int)

    private val sources = listOf(
        Source("StevenBlack", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", Kind.HOSTS, Cat.ADS),
        Source("OISD", "https://big.oisd.nl/domainswild", Kind.DOMAINS, Cat.ADS),
        Source("AdGuard DNS", "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt", Kind.ADBLOCK, Cat.ADS),
        Source("EasyList", "https://easylist.to/easylist/easylist.txt", Kind.ADBLOCK, Cat.ADS),
        Source("EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt", Kind.ADBLOCK, Cat.TRK),
        Source("uBlock filters", "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt", Kind.ADBLOCK, Cat.ADS),
        Source("Peter Lowe", "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext", Kind.HOSTS, Cat.ADS),
        Source("URLhaus (malware)", "https://urlhaus.abuse.ch/downloads/hostfile/", Kind.HOSTS, Cat.MAL)
    )

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

    private fun performUpdate(context: Context): UpdateResult {
        val lists = HashMap<Int, LongList>()
        val ok = ArrayList<String>()
        val report = ArrayList<String>()
        var total = 0

        for (s in sources) {
            val target = lists.getOrPut(s.cat) { LongList() }
            var count = 0
            val sink: (String) -> Unit = { d -> target.add(Hash.of(d)); count++ }
            val success = streamLines(s.url) { line ->
                when (s.kind) {
                    Kind.HOSTS -> parseHostsLine(line, sink)
                    Kind.DOMAINS -> parseDomainLine(line, sink)
                    Kind.ADBLOCK -> parseAdblockLine(line, sink)
                }
            }
            if (!success || count < MIN_PER_SOURCE) {
                report.add(s.name + ": فشل")
            } else {
                ok.add(s.name); total += count
                report.add(s.name + ": " + count)
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
        val msg = "تم التحديث (" + ok.size + "/" + sources.size + " قوائم) — " + unique + " نطاق"
        db.setMeta(META_LAST_MSG, msg + " | " + report.joinToString(" | "))
        return UpdateResult(true, unique, msg)
    }

    /** قراءة سطر بسطر بدون تحميل الملف كله في الذاكرة */
    @Volatile internal var lastError: String = ""

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

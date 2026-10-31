package com.matrixco.browser

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.webkit.WebResourceResponse
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** حجم مساحة التطبيق + التنظيف */
object Storage {
    data class Info(val cache: Long, val web: Long, val offline: Long, val db: Long) { val total get() = cache + web + offline + db }

    fun dirSize(f: File): Long {
        if (!f.exists()) return 0L
        if (f.isFile) return f.length()
        var s = 0L
        f.listFiles()?.forEach { s += dirSize(it) }
        return s
    }

    fun del(f: File) {
        if (!f.exists()) return
        if (f.isDirectory) f.listFiles()?.forEach { del(it) }
        f.delete()
    }

    private fun webDir(ctx: Context) = File(ctx.applicationInfo.dataDir, "app_webview")

    fun compute(ctx: Context): Info {
        val w = webDir(ctx)
        val cache = dirSize(ctx.cacheDir) + dirSize(File(w, "Default/HTTP Cache")) + dirSize(File(w, "Default/Code Cache")) +
            dirSize(File(w, "Default/GPUCache")) + dirSize(File(w, "Default/Service Worker/CacheStorage"))
        val web = (dirSize(w) - (cache - dirSize(ctx.cacheDir))).coerceAtLeast(0L)
        return Info(cache, web, dirSize(File(ctx.filesDir, "offline")), ShieldDatabase.getInstance(ctx).dbSizeBytes())
    }

    fun cleanCache(ctx: Context) {
        val w = webDir(ctx)
        del(ctx.cacheDir)
        for (p in listOf("Default/HTTP Cache", "Default/Code Cache", "Default/GPUCache", "Default/Service Worker/CacheStorage")) del(File(w, p))
    }

    fun fmt(b: Long): String = when {
        b >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", b / 1073741824.0)
        b >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
        b >= 1024 -> (b / 1024).toString() + " KB"
        else -> "$b B"
    }
}

/** صفحات المتصفح الداخلية (https://mx.home/...) — تصميم هادئ بأسلوب Chrome/Brave */
object Pages {
    const val HOST = "mx.home"
    const val HOME_URL = "https://mx.home/"

    private var logoUri: String? = null
    private fun logo(ctx: Context): String {
        logoUri?.let { return it }
        val s = try { "data:image/png;base64," + Base64.encodeToString(ctx.assets.open("logo_s.png").readBytes(), Base64.NO_WRAP) } catch (_: Exception) { "" }
        logoUri = s
        return s
    }

    data class Entry(val name: String, val url: String)

    fun readEntries(key: String, def: List<Entry>): List<Entry> {
        val s = Cfg.str(key, "")
        if (s.isEmpty()) return def
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map { val o = a.getJSONObject(it); Entry(o.getString("n"), o.getString("u")) }
        } catch (_: Exception) { def }
    }

    fun writeEntries(key: String, list: List<Entry>) {
        val a = JSONArray()
        for (e in list) a.put(JSONObject().put("n", e.name).put("u", e.url))
        Cfg.setStr(key, a.toString())
    }

    val defaultShortcuts = listOf(
        Entry("Conquer", "https://browser.playconqueronline.com/"),
        Entry("YouTube", "https://m.youtube.com/"),
        Entry("Facebook", "https://m.facebook.com/"),
        Entry("Google", "https://www.google.com/"),
        Entry("Discord", "https://discord.com/app"),
        Entry("Twitch", "https://m.twitch.tv/"),
        Entry("Reddit", "https://www.reddit.com/")
    )
    val defaultGames = listOf(Entry("Conquer Online", "https://browser.playconqueronline.com/"))

    fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    private fun fmtTime(ts: Long): String = SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(ts))
    private fun hostOf(u: String): String = try { Uri.parse(u).host.orEmpty() } catch (_: Exception) { "" }

    private fun css(t: MxTheme): String = """
:root{--bg:${t.bg.hex()};--sf:${t.surface.hex()};--sf2:${t.surface2.hex()};--ac:${t.accent.hex()};--ac2:${t.accent2.hex()};--tx:${t.text.hex()};--sb:${t.sub.hex()}}
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
html{background:var(--bg)}
body{margin:0 auto;color:var(--tx);font-family:system-ui,Roboto,sans-serif;font-size:14px;padding:12px 14px 40px;max-width:760px;background:var(--bg);min-height:100vh}
a{color:inherit;text-decoration:none}
.top{display:flex;align-items:center;justify-content:space-between;margin:2px 0 22px}
.brand{display:flex;align-items:center;gap:8px;font-weight:600;font-size:15px;color:var(--sb)}
.brand img{width:26px;height:26px;border-radius:7px}
.m{width:36px;height:36px;border-radius:50%;display:flex;align-items:center;justify-content:center;color:var(--sb);font-size:20px}
.search{display:flex;align-items:center;gap:6px;background:var(--sf2);border-radius:26px;padding:3px 6px 3px 16px;margin:0 auto 26px;max-width:640px}
.search input{flex:1;background:transparent;border:0;outline:0;color:var(--tx);font-size:15px;padding:13px 4px;min-width:0}
.search .b{width:38px;height:38px;border-radius:50%;display:flex;align-items:center;justify-content:center;color:var(--sb);font-size:17px;background:transparent;border:0}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(74px,1fr));gap:18px 6px;margin-bottom:22px}
.tile{display:flex;flex-direction:column;align-items:center;gap:7px;font-size:11px;color:var(--sb);position:relative;text-align:center}
.ic{width:52px;height:52px;border-radius:50%;background:var(--sf2);display:flex;align-items:center;justify-content:center;font-size:20px;font-weight:600;color:var(--tx);overflow:hidden}
.ic img{width:30px;height:30px;border-radius:6px;object-fit:contain}
.tile .x{position:absolute;top:-4px;right:12px;width:18px;height:18px;border-radius:50%;background:var(--sf);color:var(--sb);font-size:10px;display:flex;align-items:center;justify-content:center}
.strip{display:flex;gap:8px;margin-bottom:22px}
.strip a{flex:1;background:var(--sf);border-radius:14px;padding:11px 8px;text-align:center}
.strip b{display:block;font-size:17px;font-weight:600;margin-bottom:2px}
.strip span{font-size:10px;color:var(--sb)}
.sec{display:flex;justify-content:space-between;align-items:center;margin:22px 4px 10px;font-size:12px;color:var(--sb)}
.sec b{font-weight:600;letter-spacing:.2px}
.sec a{color:var(--ac)}
.games{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:10px}
.game{display:flex;align-items:center;gap:10px;background:var(--sf);border-radius:14px;padding:12px}
.game .ic{width:38px;height:38px;flex:none}
.game .ic img{width:24px;height:24px}
.game b{display:block;font-size:13px;font-weight:600}
.game small{color:var(--sb);font-size:10px;direction:ltr;display:block}
.item{display:flex;align-items:center;gap:12px;padding:10px 4px;border-bottom:1px solid var(--sf2)}
.item .ic{width:34px;height:34px;flex:none;font-size:14px}
.item .ic img{width:20px;height:20px}
.item .t{flex:1;min-width:0}
.item .t b{display:block;font-size:13px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.item .t span{font-size:11px;color:var(--sb);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;display:block;direction:ltr;text-align:right}
.x2{color:var(--sb);padding:6px 10px;font-size:15px}
.empty{text-align:center;color:var(--sb);padding:40px 10px}
.row{display:flex;justify-content:space-between;padding:11px 4px;border-bottom:1px solid var(--sf2);font-size:13px}
.row span:first-child{color:var(--sb)}
.ptop{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}
.ptop h2{margin:0;font-size:17px;font-weight:600}
.btn{display:inline-block;background:var(--ac);color:#fff;border-radius:12px;padding:9px 18px;font-size:13px;margin:4px 3px}
.btn.alt{background:var(--sf2);color:var(--tx)}
.card{background:var(--sf);border-radius:14px;padding:14px;margin-bottom:12px}
.card h4{margin:0 0 8px;font-size:12px;color:var(--sb);font-weight:500}
.bars{display:flex;align-items:flex-end;justify-content:space-between;gap:5px;height:80px}
.bars div{flex:1;background:var(--ac);border-radius:4px 4px 1px 1px;min-height:3px;opacity:.85}
.chip{display:inline-block;padding:2px 9px;border-radius:10px;font-size:11px;font-weight:600;color:#fff}
.small{font-size:11px;color:var(--sb)}
"""

    private fun page(t: MxTheme, title: String, body: String): String =
        "<!doctype html><html lang=\"ar\" dir=\"rtl\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" + esc(title) + "</title><style>" + css(t) + "</style></head><body>" + body + "</body></html>"

    /** أيقونة موقع: صورة حقيقية لو موجودة وإلا أول حرف */
    private fun icon(ctx: Context, url: String, name: String): String {
        val u = Favicons.dataUri(ctx, hostOf(url))
        return if (u != null) "<img src=\"$u\">" else esc(name.trim().take(1).uppercase())
    }

    private fun listBody(title: String, items: List<String>, emptyText: String, extraTop: String = ""): String =
        "<div class=\"ptop\"><h2>" + esc(title) + "</h2>" + extraTop + "</div>" +
            (if (items.isEmpty()) "<div class=\"empty\">" + esc(emptyText) + "</div>" else items.joinToString(""))

    private fun item(ctx: Context, title: String, sub: String, href: String, delHref: String?, iconUrl: String = sub): String =
        "<div class=\"item\"><div class=\"ic\">" + icon(ctx, iconUrl, title.ifBlank { sub }) + "</div><a class=\"t\" href=\"" + esc(href) + "\"><b>" + esc(title.ifBlank { sub }) + "</b><span>" + esc(sub) + "</span></a>" +
            (if (delHref != null) "<a class=\"x2\" href=\"" + esc(delHref) + "\">✕</a>" else "") + "</div>"

    fun respond(ctx: Context, db: ShieldDatabase, t: MxTheme, uri: Uri): WebResourceResponse {
        val path = uri.path ?: "/"
        val html = try {
            when (path) {
                "", "/" -> home(ctx, db, t)
                "/history" -> history(ctx, db, t)
                "/bookmarks" -> bookmarks(ctx, db, t)
                "/offline" -> offline(ctx, db, t)
                "/downloads" -> downloads(ctx, db, t)
                "/shield" -> shield(ctx, db, t)
                "/spy" -> spyLog(ctx, db, t)
                "/storage" -> storage(ctx, t)
                "/error" -> error(ctx, db, t, uri.getQueryParameter("u").orEmpty(), uri.getQueryParameter("m").orEmpty())
                "/warn" -> warn(t, uri.getQueryParameter("u").orEmpty())
                else -> null
            }
        } catch (e: Exception) {
            page(t, "خطأ", "<div class=\"empty\">تعذر تحميل الصفحة: " + esc(e.message ?: "") + "</div>")
        }
        if (html == null) return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
        val r = WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
        r.responseHeaders = mapOf("Cache-Control" to "no-store")
        return r
    }

    private fun home(ctx: Context, db: ShieldDatabase, t: MxTheme): String {
        val sb = StringBuilder()
        val sc = readEntries("shortcuts", defaultShortcuts)
        val games = readEntries("games", defaultGames)
        val quick = db.getBookmarks(5)
        // نجلب الأيقونات الناقصة في الخلفية
        Favicons.ensure(ctx, (sc.map { hostOf(it.url) } + games.map { hostOf(it.url) } + quick.map { hostOf(it.url) }).filter { it.isNotEmpty() })

        sb.append("<div class=\"top\"><a class=\"m\" href=\"mx://tools\">⋮</a><div class=\"brand\"><span>MaTrIX Co</span><img src=\"").append(logo(ctx)).append("\"></div></div>")
        sb.append("<form class=\"search\" onsubmit=\"return go()\"><input id=\"q\" placeholder=\"ابحث أو اكتب عنوان الموقع\" autocomplete=\"off\" autocapitalize=\"off\"><a class=\"b\" href=\"mx://voice\">🎤</a></form>")

        val custom = Cfg.str("shortcuts", "").isNotEmpty()
        sb.append("<div class=\"grid\">")
        for ((i, e) in sc.withIndex()) {
            sb.append("<a class=\"tile\" href=\"").append(esc(e.url)).append("\"><div class=\"ic\">").append(icon(ctx, e.url, e.name)).append("</div>").append(esc(e.name.take(12)))
            if (custom) sb.append("<span class=\"x\" onclick=\"event.preventDefault();location.href='mx://delsc?i=").append(i).append("'\">✕</span>")
            sb.append("</a>")
        }
        sb.append("<a class=\"tile\" href=\"mx://addsc\"><div class=\"ic\" style=\"font-size:24px\">＋</div>إضافة</a></div>")

        val st = Storage.compute(ctx)
        sb.append("<div class=\"strip\">")
        sb.append("<a href=\"https://mx.home/shield\"><b>").append(db.todayBlocked()).append("</b><span>🛡 محجوب اليوم</span></a>")
        sb.append("<a href=\"https://mx.home/shield\"><b>").append(db.totalSkipped()).append("</b><span>⚡ انتظار تم تخطيه</span></a>")
        sb.append("<a href=\"mx://storage\"><b>").append(Storage.fmt(st.total)).append("</b><span>💾 مساحة التطبيق</span></a></div>")

        sb.append("<div class=\"sec\"><b>الألعاب</b><a href=\"mx://addgame\">＋ إضافة لعبة</a></div><div class=\"games\">")
        for (g in games) {
            sb.append("<a class=\"game\" href=\"").append(esc(g.url)).append("\"><div class=\"ic\">").append(icon(ctx, g.url, g.name)).append("</div><div><b>").append(esc(g.name)).append("</b><small>").append(esc(hostOf(g.url))).append("</small></div></a>")
        }
        sb.append("</div>")

        sb.append("<div class=\"sec\"><b>الوصول السريع</b><a href=\"https://mx.home/bookmarks\">الكل</a></div>")
        val list = if (quick.isNotEmpty()) quick.map { Entry(it.title, it.url) } else db.getHistory(5).map { Entry(it.title, it.url) }
        if (list.isEmpty()) sb.append("<div class=\"empty\" style=\"padding:18px\">لا يوجد شيء بعد</div>")
        for (e in list) sb.append(item(ctx, e.name, e.url, e.url, null))

        sb.append("<script>function go(){var v=document.getElementById('q').value.trim();if(v){location.href='mx://go?q='+encodeURIComponent(v);}return false;}</script>")
        return page(t, "MaTrIX Co", sb.toString())
    }

    private fun history(ctx: Context, db: ShieldDatabase, t: MxTheme): String {
        val items = db.getHistory(300).map { item(ctx, it.title, it.url, it.url, "mx://delhist?id=" + it.id) }
        val clear = if (items.isEmpty()) "" else "<a class=\"btn alt\" href=\"mx://clearhist\">مسح الكل</a>"
        return page(t, "السجل", listBody("السجل", items, "لا يوجد سجل بعد", clear))
    }

    private fun bookmarks(ctx: Context, db: ShieldDatabase, t: MxTheme): String {
        val items = db.getBookmarks(300).map { item(ctx, it.title, it.url, it.url, "mx://delbm?u=" + Uri.encode(it.url)) }
        return page(t, "المفضلة", listBody("المفضلة", items, "لا توجد صفحات مفضلة"))
    }

    private fun offline(ctx: Context, db: ShieldDatabase, t: MxTheme): String {
        val all = db.getOffline()
        val items = all.map { item(ctx, it.title, fmtTime(it.ts) + " • " + Storage.fmt(it.size) + " • " + hostOf(it.url), "mx://openoff?id=" + it.id, "mx://deloff?id=" + it.id, it.url) }
        return page(t, "الصفحات المحفوظة", listBody("صفحات بدون إنترنت", items, "لم تحفظ أي صفحة بعد", "<span class=\"chip\" style=\"background:var(--sf2);color:var(--sb)\">" + Storage.fmt(all.sumOf { it.size }) + "</span>"))
    }

    private fun downloads(ctx: Context, db: ShieldDatabase, t: MxTheme): String {
        val items = db.getDownloads(200).map { item(ctx, it.name, fmtTime(it.ts) + " • " + hostOf(it.url), "mx://opendl?id=" + it.id, "mx://deldl?id=" + it.id, it.url) }
        return page(t, "التحميلات", listBody("التحميلات", items, "لا توجد تحميلات"))
    }

    private fun shield(ctx: Context, db: ShieldDatabase, t: MxTheme): String {
        val sb = StringBuilder()
        sb.append("<div class=\"ptop\"><h2>تقرير الدرع</h2><a class=\"btn\" href=\"mx://update\">تحديث القوائم</a></div>")
        val week = db.week()
        val maxV = (week.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
        sb.append("<div class=\"card\"><h4>المحجوب خلال آخر 7 أيام</h4><div class=\"bars\">")
        for (w in week) sb.append("<div style=\"height:").append(((w.second * 100) / maxV).coerceAtLeast(4)).append("%\" title=\"").append(w.first).append("\"></div>")
        sb.append("</div></div>")
        sb.append("<div class=\"row\"><span>المحجوب (7 أيام)</span><span>").append(week.sumOf { it.second }).append("</span></div>")
        sb.append("<div class=\"row\"><span>انتظار تم تخطيه (7 أيام)</span><span>").append(week.sumOf { it.third }).append("</span></div>")
        sb.append("<div class=\"row\"><span>إجمالي المحجوب</span><span>").append(db.totalBlocked()).append("</span></div>")
        sb.append("<div class=\"row\"><span>متتبعات محجوبة</span><span>").append(db.counter("trackers_total")).append("</span></div>")
        sb.append("<div class=\"row\"><span>نطاقات في قاعدة البيانات</span><span>").append(db.blockedHostsCount()).append("</span></div>")
        sb.append("<div class=\"row\"><span>آخر تحديث</span><span>").append(esc(RuleUpdater.getLastUpdate(ctx) ?: "لم يتم بعد")).append("</span></div>")
        val msg = RuleUpdater.getLastMessage(ctx)
        if (!msg.isNullOrBlank()) sb.append("<div class=\"row\"><span>التفاصيل</span><span style=\"max-width:62%;text-align:left;font-size:11px\">").append(esc(msg)).append("</span></div>")
        sb.append("<div class=\"sec\"><b>أكثر الجهات حجبًا</b></div>")
        val top = db.getTopBlocked(15)
        if (top.isEmpty()) sb.append("<div class=\"empty\">لا بيانات بعد</div>")
        for ((h, c) in top) sb.append("<div class=\"row\"><span style=\"direction:ltr\">").append(esc(h)).append("</span><span>").append(c).append("</span></div>")
        sb.append("<div style=\"margin-top:14px\"><a class=\"btn alt\" href=\"https://mx.home/spy\">سجل كاشف التجسس</a></div>")
        return page(t, "الدرع", sb.toString())
    }

    private fun spyLog(ctx: Context, db: ShieldDatabase, t: MxTheme): String {
        val log = db.getSpyLog(100)
        val items = log.map {
            val col = if (it.conf >= SpyGuard.AUTO) "#D93025" else if (it.conf >= SpyGuard.ASK) "#E37400" else "#5F6368"
            "<div class=\"item\"><div class=\"t\"><b>" + esc(SpyGuard.label(it.type)) + "</b><span>" + esc(it.host) + " ← " + esc(it.page) + " • " + fmtTime(it.ts) +
                "</span></div><span class=\"chip\" style=\"background:" + col + "\">" + it.conf + "% " + esc(it.decision) + "</span></div>"
        }
        val clear = if (items.isEmpty()) "" else "<a class=\"btn alt\" href=\"mx://clearspy\">مسح</a>"
        return page(t, "كاشف التجسس", listBody("سجل كاشف التجسس", items, "لا توجد حالات — المتصفح يراقب في الخلفية", clear))
    }

    private fun storage(ctx: Context, t: MxTheme): String {
        val s = Storage.compute(ctx)
        val sb = StringBuilder()
        sb.append("<div class=\"ptop\"><h2>مساحة التطبيق</h2><span class=\"chip\" style=\"background:var(--ac)\">").append(Storage.fmt(s.total)).append("</span></div>")
        sb.append("<div class=\"row\"><span>كاش التصفح</span><span>").append(Storage.fmt(s.cache)).append("</span></div>")
        sb.append("<div class=\"row\"><span>بيانات المواقع والكوكيز</span><span>").append(Storage.fmt(s.web)).append("</span></div>")
        sb.append("<div class=\"row\"><span>الصفحات المحفوظة</span><span>").append(Storage.fmt(s.offline)).append("</span></div>")
        sb.append("<div class=\"row\"><span>قاعدة الدرع</span><span>").append(Storage.fmt(s.db)).append("</span></div>")
        sb.append("<p class=\"small\">الكاش بيتنظف تلقائيًا لو عدّى الحد (").append(Cfg.int(Cfg.CACHE_MB, 150)).append(" MB). التحميلات في مجلد التنزيلات ولا تدخل ضمن الرقم ده.</p>")
        sb.append("<a class=\"btn\" href=\"mx://cleancache\">تنظيف الكاش الآن</a><a class=\"btn alt\" href=\"https://mx.home/offline\">الصفحات المحفوظة</a>")
        return page(t, "المساحة", sb.toString())
    }

    private fun error(ctx: Context, db: ShieldDatabase, t: MxTheme, url: String, msg: String): String {
        val saved = if (url.isNotEmpty()) db.getOfflineByUrl(url) else null
        val sb = StringBuilder()
        sb.append("<div class=\"empty\" style=\"padding-top:70px\"><div style=\"font-size:48px\">📡</div><h2 style=\"font-weight:600\">تعذر فتح الصفحة</h2>")
        sb.append("<div style=\"direction:ltr;word-break:break-all;font-size:12px\">").append(esc(url)).append("</div>")
        if (msg.isNotEmpty()) sb.append("<p>").append(esc(msg)).append("</p>")
        sb.append("<a class=\"btn\" href=\"").append(esc(url)).append("\">إعادة المحاولة</a>")
        if (saved != null) sb.append("<a class=\"btn alt\" href=\"mx://openoff?id=").append(saved.id).append("\">النسخة المحفوظة</a>")
        sb.append("<a class=\"btn alt\" href=\"mx://legacy?u=").append(Uri.encode(url)).append("\">وضع التوافق القديم</a></div>")
        return page(t, "خطأ", sb.toString())
    }

    private fun warn(t: MxTheme, url: String): String {
        val sb = StringBuilder()
        sb.append("<div class=\"empty\" style=\"padding-top:60px\"><div style=\"font-size:54px\">⛔</div><h2 style=\"color:#F28B82;font-weight:600\">موقع خطير</h2>")
        sb.append("<p>الدرع رصد إن الموقع ده في قوائم البرمجيات الخبيثة أو التصيّد. ننصحك بعدم المتابعة.</p>")
        sb.append("<div style=\"direction:ltr;word-break:break-all;font-size:12px\">").append(esc(url)).append("</div><br>")
        sb.append("<a class=\"btn\" href=\"mx://back\">رجوع للأمان</a><a class=\"btn alt\" href=\"mx://proceed?u=").append(Uri.encode(url)).append("\">أفهم المخاطر، تابع</a></div>")
        return page(t, "تحذير", sb.toString())
    }
}

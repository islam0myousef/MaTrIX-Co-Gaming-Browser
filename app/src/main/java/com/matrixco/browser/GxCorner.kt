package com.matrixco.browser

import android.content.Context
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** ركن الألعاب: أخبار + إصدارات قادمة + عروض مجانية — مخزّنة محليًا ومحدّثة كل 3 ساعات */
object GxCorner {
    data class Item(val title: String, val sub: String, val url: String, val img: String)
    data class Data(val news: List<Item>, val releases: List<Item>, val deals: List<Item>, val ts: Long)

    @Volatile var onUpdated: (() -> Unit)? = null
    private val busy = AtomicBoolean(false)
    private val exec = Executors.newSingleThreadExecutor()
    private const val TTL = 3L * 60 * 60 * 1000

    private val newsFeeds = listOf(
        "https://www.pcgamer.com/rss/",
        "https://www.eurogamer.net/feed",
        "https://www.gamespot.com/feeds/news/"
    )

    private fun file(ctx: Context) = File(ctx.filesDir, "gx_corner.json")

    fun cached(ctx: Context): Data? {
        return try {
            val o = JSONObject(file(ctx).readText())
            Data(arr(o.optJSONArray("news")), arr(o.optJSONArray("releases")), arr(o.optJSONArray("deals")), o.optLong("ts"))
        } catch (_: Exception) { null }
    }

    private fun arr(a: JSONArray?): List<Item> {
        if (a == null) return emptyList()
        return (0 until a.length()).map { val o = a.getJSONObject(it); Item(o.optString("t"), o.optString("s"), o.optString("u"), o.optString("i")) }
    }

    private fun toJson(l: List<Item>): JSONArray {
        val a = JSONArray()
        for (i in l) a.put(JSONObject().put("t", i.title).put("s", i.sub).put("u", i.url).put("i", i.img))
        return a
    }

    /** يحدّث في الخلفية لو القديم عدّى 3 ساعات */
    fun ensure(ctx: Context, force: Boolean = false) {
        val c = cached(ctx)
        if (!force && c != null && System.currentTimeMillis() - c.ts < TTL) return
        if (!busy.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        exec.execute {
            try {
                val prev = cached(app)
                val news = try { fetchNews() } catch (_: Exception) { emptyList() }
                val rel = try { fetchReleases() } catch (_: Exception) { emptyList() }
                val deals = try { fetchDeals() } catch (_: Exception) { emptyList() }
                if (news.isNotEmpty() || rel.isNotEmpty() || deals.isNotEmpty()) {
                    val o = JSONObject()
                        .put("ts", System.currentTimeMillis())
                        .put("news", toJson(if (news.isNotEmpty()) news else prev?.news ?: emptyList()))
                        .put("releases", toJson(if (rel.isNotEmpty()) rel else prev?.releases ?: emptyList()))
                        .put("deals", toJson(if (deals.isNotEmpty()) deals else prev?.deals ?: emptyList()))
                    file(app).writeText(o.toString())
                    onUpdated?.invoke()
                }
            } finally { busy.set(false) }
        }
    }

    private fun get(url: String): String? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 15000; c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) MaTrIXCo/3.3")
            if (c.responseCode != 200) return null
            c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) { null } finally { c?.disconnect() }
    }

    private fun fetchDeals(): List<Item> {
        val t = get("https://www.gamerpower.com/api/giveaways?type=game&sort-by=popularity") ?: return emptyList()
        val a = JSONArray(t)
        val out = ArrayList<Item>()
        for (i in 0 until minOf(a.length(), 14)) {
            val o = a.getJSONObject(i)
            val worth = o.optString("worth", "").let { if (it.isBlank() || it == "N/A") "مجاني" else "كان $it — مجاني" }
            out.add(Item(o.optString("title"), worth + " • " + o.optString("platforms").take(40), o.optString("open_giveaway_url").ifEmpty { o.optString("gamerpower_url") }, o.optString("thumbnail")))
        }
        return out
    }

    private fun fetchReleases(): List<Item> {
        val t = get("https://store.steampowered.com/api/featuredcategories?cc=us&l=english") ?: return emptyList()
        val items = JSONObject(t).optJSONObject("coming_soon")?.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<Item>()
        for (i in 0 until minOf(items.length(), 14)) {
            val o = items.getJSONObject(i)
            val id = o.optInt("id", 0)
            if (id == 0) continue
            out.add(Item(o.optString("name"), "قريبًا على Steam", "https://store.steampowered.com/app/$id", o.optString("small_capsule_image")))
        }
        return out
    }

    private fun fetchNews(): List<Item> {
        for (f in newsFeeds) {
            val t = get(f) ?: continue
            val items = parseRss(t)
            if (items.isNotEmpty()) return items
        }
        return emptyList()
    }

    private fun parseRss(xml: String): List<Item> {
        val out = ArrayList<Item>()
        val p = Xml.newPullParser()
        p.setInput(xml.reader())
        var ev = p.eventType
        var title = ""; var link = ""; var img = ""; var inItem = false
        while (ev != XmlPullParser.END_DOCUMENT && out.size < 14) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    when (p.name) {
                        "item", "entry" -> { inItem = true; title = ""; link = ""; img = "" }
                        "title" -> if (inItem) title = p.nextText().trim()
                        "link" -> if (inItem) { val h = p.getAttributeValue(null, "href"); link = if (!h.isNullOrEmpty()) h else p.nextText().trim() }
                        "thumbnail", "content", "enclosure" -> if (inItem) { val u = p.getAttributeValue(null, "url"); if (!u.isNullOrEmpty() && img.isEmpty() && (p.getAttributeValue(null, "type")?.startsWith("image") != false)) img = u }
                    }
                }
                XmlPullParser.END_TAG -> if ((p.name == "item" || p.name == "entry") && inItem) {
                    inItem = false
                    if (title.isNotEmpty() && link.startsWith("http")) out.add(Item(title, "", link, img))
                }
            }
            ev = p.next()
        }
        return out
    }
}

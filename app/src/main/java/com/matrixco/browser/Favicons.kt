package com.matrixco.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** أيقونات المواقع الحقيقية (favicon / apple-touch-icon) محفوظة محليًا بحجم صغير */
object Favicons {
    @Volatile var onUpdated: (() -> Unit)? = null

    private val busy = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val mem = ConcurrentHashMap<String, String>()
    private val exec = Executors.newFixedThreadPool(2)
    private const val SIZE = 96

    private fun key(host: String) = host.lowercase(Locale.ROOT).removePrefix("www.")

    private fun file(ctx: Context, host: String): File {
        val d = File(ctx.filesDir, "fav"); d.mkdirs()
        return File(d, key(host).replace(Regex("[^a-z0-9.-]"), "_") + ".png")
    }

    fun dataUri(ctx: Context, host: String): String? {
        if (host.isEmpty()) return null
        val k = key(host)
        mem[k]?.let { return it }
        val f = file(ctx, host)
        if (!f.exists()) return null
        return try {
            val u = "data:image/png;base64," + Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
            mem[k] = u; u
        } catch (_: Exception) { null }
    }

    private fun scaled(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val r = minOf(SIZE.toFloat() / src.width, SIZE.toFloat() / src.height)
        val w = (src.width * r).toInt().coerceAtLeast(1); val h = (src.height * r).toInt().coerceAtLeast(1)
        val sc = Bitmap.createScaledBitmap(src, w, h, true)
        Canvas(out).drawBitmap(sc, ((SIZE - w) / 2).toFloat(), ((SIZE - h) / 2).toFloat(), null)
        return out
    }

    private fun write(ctx: Context, host: String, bmp: Bitmap) {
        try {
            val out = scaled(bmp)
            val bos = ByteArrayOutputStream()
            out.compress(Bitmap.CompressFormat.PNG, 100, bos)
            file(ctx, host).writeBytes(bos.toByteArray())
            mem.remove(key(host))
        } catch (_: Exception) {}
    }

    /** من أيقونة وصلت من الـ WebView — نحفظها فقط لو مفيش أيقونة قبلها */
    fun saveIfMissing(ctx: Context, host: String, bmp: Bitmap) {
        if (host.isEmpty() || file(ctx, host).exists()) return
        write(ctx, host, bmp)
        onUpdated?.invoke()
    }

    /** يحمّل الأيقونات الناقصة في الخلفية */
    fun ensure(ctx: Context, hosts: Collection<String>) {
        for (h in hosts) {
            val k = key(h)
            if (k.isEmpty() || file(ctx, k).exists() || failed.contains(k) || !busy.add(k)) continue
            exec.execute {
                val ok = try { fetch(ctx, k) } catch (_: Exception) { false }
                if (!ok) failed.add(k)
                busy.remove(k)
                if (ok) onUpdated?.invoke()
            }
        }
    }

    private fun get(url: String, max: Int): Pair<ByteArray, String>? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 10000; c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            if (c.responseCode != 200) return null
            val bos = ByteArrayOutputStream(); val buf = ByteArray(8192); var total = 0
            c.inputStream.use { ins ->
                while (true) { val n = ins.read(buf); if (n < 0) break; total += n; if (total > max) break; bos.write(buf, 0, n) }
            }
            bos.toByteArray() to c.url.toString()
        } catch (_: Exception) { null } finally { c?.disconnect() }
    }

    private fun fetch(ctx: Context, host: String): Boolean {
        val cands = ArrayList<String>()
        val page = get("https://$host/", 150_000)
        if (page != null) {
            val html = String(page.first, Charsets.UTF_8)
            val rx = Regex("<link[^>]+rel=[\"']([^\"']*)[\"'][^>]*>", RegexOption.IGNORE_CASE)
            val hrefRx = Regex("href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            val apple = ArrayList<String>(); val icons = ArrayList<String>()
            for (m in rx.findAll(html)) {
                val rel = m.groupValues[1].lowercase(Locale.ROOT)
                val href = hrefRx.find(m.value)?.groupValues?.get(1) ?: continue
                if (href.endsWith(".svg", true)) continue
                val abs = try { URL(URL(page.second), href).toString() } catch (_: Exception) { continue }
                if (rel.contains("apple-touch-icon")) apple.add(abs) else if (rel.contains("icon")) icons.add(abs)
            }
            cands.addAll(apple); cands.addAll(icons)
        }
        cands.add("https://$host/apple-touch-icon.png")
        cands.add("https://$host/favicon.ico")
        for (u in cands.distinct().take(6)) {
            val d = get(u, 400_000) ?: continue
            val bmp = try { BitmapFactory.decodeByteArray(d.first, 0, d.first.size) } catch (_: Throwable) { null } ?: continue
            if (bmp.width < 16 || bmp.height < 16) continue
            write(ctx, host, bmp)
            return true
        }
        return false
    }
}

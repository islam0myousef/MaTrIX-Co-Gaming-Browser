package com.matrixco.browser

import android.app.Dialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * التحديث من جوه التطبيق: بيفحص آخر Release على GitHub، ولو فيه نسخة أحدث بيعرض نافذة،
 * وبينزّلها ويثبّتها بنفسه (النظام بيطلب منك التأكيد). أندرويد بيرفض أي APK مش موقّع بنفس مفتاحك.
 */
object Updater {
    const val REPO = "islam0myousef/MaTrIX-Co-Gaming-Browser"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val PREFIX = "https://github.com/$REPO/releases/download/"

    class Info(val tag: String, val version: String, val notes: String, val url: String, val size: Long, val sha256: String?)

    private fun parse(v: String): List<Int> =
        v.trim().removePrefix("v").split('.', '-').mapNotNull { p -> p.filter { it.isDigit() }.toIntOrNull() }

    fun newer(remote: String, local: String): Boolean {
        val r = parse(remote); val l = parse(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** بيرجّع null لو مفيش Release صالح. بيرمي استثناء لو مفيش نت. */
    fun fetchLatest(): Info? {
        val c = URL(API).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "MaTrIX-Co-Updater")
        try {
            if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
            val tag = o.getString("tag_name")
            val assets = o.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val url = a.optString("browser_download_url")
                if (a.optString("name").endsWith(".apk") && url.startsWith(PREFIX)) {
                    val d = a.optString("digest", "")
                    return Info(tag, tag.removePrefix("v"), o.optString("body", "").take(800), url, a.optLong("size"),
                        if (d.startsWith("sha256:")) d.removePrefix("sha256:") else null)
                }
            }
            return null
        } finally { c.disconnect() }
    }

    fun download(ctx: Context, info: Info, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean): File {
        // Fail closed: never install an update whose release metadata has no valid digest.
        val expectedDigest = info.sha256?.trim()?.lowercase()
        if (expectedDigest == null || !expectedDigest.matches(Regex("^[a-f0-9]{64}$"))) {
            throw IOException("التحديث مرفوض: بصمة SHA-256 غير موجودة أو غير صالحة في بيانات الإصدار")
        }
        val dir = File(ctx.filesDir, "update").apply { mkdirs() }
        val part = File(dir, "matrix-update.apk.part")
        val meta = File(dir, "matrix-update.properties")
        val complete = File(dir, "matrix-update.apk")
        val props = java.util.Properties()
        if (meta.exists()) try { meta.inputStream().use { props.load(it) } } catch (_: Exception) { meta.delete() }
        if (props.getProperty("url")?.let { it != info.url } == true) {
            part.delete(); meta.delete(); props.clear()
        }
        props.setProperty("url", info.url)
        var existing = if (part.exists()) part.length() else 0L
        if (info.size > 0L && existing > info.size) { part.delete(); existing = 0L }

        val parsedUpdateUrl = URL(info.url)
        if (parsedUpdateUrl.protocol != "https" || parsedUpdateUrl.host != "github.com") {
            throw IOException("مصدر التحديث غير موثوق")
        }
        val c = parsedUpdateUrl.openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "MaTrIX-Co-Updater")
        if (existing > 0L) {
            c.setRequestProperty("Range", "bytes=$existing-")
            val validator = props.getProperty("etag")?.takeIf { it.isNotBlank() }
                ?: props.getProperty("lastModified")?.takeIf { it.isNotBlank() }
            if (validator != null) c.setRequestProperty("If-Range", validator)
        }
        try {
            val code = c.responseCode
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) throw IOException("HTTP $code")
            val append = existing > 0L && code == HttpURLConnection.HTTP_PARTIAL
            if (append) {
                val cr = c.getHeaderField("Content-Range") ?: ""
                val match = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE).find(cr)
                if (match?.groupValues?.get(1)?.toLongOrNull() != existing) throw IOException("السيرفر أرسل نقطة استكمال غير صحيحة")
                val oldValidator = props.getProperty("etag")?.takeIf { it.isNotBlank() }
                    ?: props.getProperty("lastModified")?.takeIf { it.isNotBlank() }
                val newValidator = c.getHeaderField("ETag")?.takeIf { it.isNotBlank() }
                    ?: c.getHeaderField("Last-Modified")?.takeIf { it.isNotBlank() }
                if (oldValidator != null && newValidator != null && oldValidator != newValidator) {
                    throw IOException("ملف التحديث تغيّر على السيرفر؛ أعد المحاولة لتنزيل النسخة الجديدة بأمان")
                }
            } else {
                // HTTP 200: server ignored Range or representation changed; restart safely.
                existing = 0L; part.delete()
            }
            c.getHeaderField("ETag")?.let { props.setProperty("etag", it) }
            c.getHeaderField("Last-Modified")?.let { props.setProperty("lastModified", it) }
            meta.outputStream().use { props.store(it, "MaTRIX resumable updater") }
            val responseLength = c.contentLengthLong
            val rangeTotal = c.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
            val total = rangeTotal?.takeIf { it > 0L } ?: info.size.takeIf { it > 0L }
                ?: if (responseLength > 0L) existing + responseLength else -1L

            val md = MessageDigest.getInstance("SHA-256")
            if (append && part.exists()) part.inputStream().use { input ->
                val hashBuffer = ByteArray(64 * 1024)
                while (true) { val n = input.read(hashBuffer); if (n < 0) break; md.update(hashBuffer, 0, n) }
            }
            var done = existing
            var lastUpdate = 0L
            c.inputStream.use { input ->
                java.io.FileOutputStream(part, append).buffered().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled()) throw IOException("تم إيقاف التنزيل مؤقتًا")
                        val n = input.read(buffer); if (n < 0) break; if (n == 0) continue
                        out.write(buffer, 0, n); md.update(buffer, 0, n); done += n
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 150L) { lastUpdate = now; onProgress(done, total) }
                    }
                }
            }
            if (part.length() < 100_000L) throw IOException("الملف لم يكتمل أو حجمه غير صالح")
            if (total > 0L && part.length() != total) throw IOException("انقطع الاتصال؛ تم حفظ الملف الجزئي للاستكمال")
            val actual = md.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expectedDigest, true)) {
                part.delete(); meta.delete()
                throw IOException("الملف تالف أو تم العبث به (SHA-256 غير مطابق)")
            }
            if (complete.exists()) complete.delete()
            if (!part.renameTo(complete)) { part.copyTo(complete, overwrite = true); part.delete() }
            meta.delete()
            onProgress(complete.length(), total.takeIf { it > 0L } ?: complete.length())
            return complete
        } finally { c.disconnect() }
    }

    /** بيسلّم الـ APK لمثبّت أندرويد (بيطلب منك التأكيد) */
    fun install(ctx: Context, f: File) {
        val pi = ctx.packageManager.packageInstaller
        val id = pi.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        val s = pi.openSession(id)
        try {
            f.inputStream().use { inp ->
                s.openWrite("matrix-update.apk", 0, f.length()).use { out -> inp.copyTo(out); s.fsync(out) }
            }
            val i = Intent(ctx, InstallReceiver::class.java).setAction(InstallReceiver.ACTION)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            s.commit(PendingIntent.getBroadcast(ctx, id, i, flags).intentSender)
        } catch (e: Exception) {
            try { s.abandon() } catch (_: Exception) {}
            throw e
        } finally { s.close() }
    }
}

class InstallReceiver : BroadcastReceiver() {
    companion object { const val ACTION = "com.matrixco.browser.INSTALL_RESULT" }

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    // تحصين: شيل أي صلاحيات URI ممنوحة واي بيانات إضافية قبل ما نشغّل شاشة التأكيد بتاعة النظام
                    confirm.setFlags(confirm.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION).inv())
                    confirm.clipData = null
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { ctx.startActivity(confirm) } catch (_: Exception) {}
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> Toast.makeText(ctx, "فشل التحديث: " + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""), Toast.LENGTH_LONG).show()
        }
    }
}

/** النافذة والفحص التلقائي */
class UpdateUi(private val a: MainActivity, private val ui: UiKit) {
    private fun dp(v: Int) = ui.dp(v)
    private val th get() = ui.theme

    fun checkAuto() {
        if (!Cfg.bool("upd_auto", true)) return
        val last = Cfg.str("upd_last", "0").toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - last < 12 * 3600_000L) return
        check(false)
    }

    fun check(manual: Boolean) {
        if (manual) a.toastMsg("⏳ بدوّر على تحديث...")
        Thread {
            try {
                val info = Updater.fetchLatest()
                Cfg.setStr("upd_last", System.currentTimeMillis().toString())
                a.runOnUiThread { handle(info, manual) }
            } catch (_: Exception) {
                if (manual) a.runOnUiThread { a.toastMsg("تعذّر الفحص — اتأكد من الإنترنت") }
            }
        }.start()
    }

    private fun handle(info: Updater.Info?, manual: Boolean) {
        val cur = a.appVersion()
        if (info == null || !Updater.newer(info.version, cur)) { if (manual) a.toastMsg("✅ إنت على أحدث إصدار ($cur)"); return }
        if (!manual && Cfg.str("upd_skip", "") == info.tag) return
        show(info, cur)
    }

    private fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || a.packageManager.canRequestPackageInstalls()

    private fun mb(b: Long) = String.format(java.util.Locale.US, "%.1f", b / 1048576.0)

    private fun show(info: Updater.Info, cur: String) {
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        var cancelled = false
        var busy = false
        fun rounded(color: Int, stroke: Int = Color.TRANSPARENT, radius: Int = 20): GradientDrawable =
            GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke) }

        // Real colors sampled from reference art: deep navy-violet card (neon "Version Update" dialog),
        // violet→magenta glow accent (same dialog's glowing geometric number), and the Earth2 teal
        // progress/brand accent (bright fill on a dark teal track).
        val navyTop = 0xFF24183C.toInt()
        val navyBottom = 0xFF0F0917.toInt()
        val violet = 0xFF3F46B1.toInt()
        val magenta = 0xFFBF6ED7.toInt()
        val teal = 0xFF02B099.toInt()
        val tealDark = 0xFF00433C.toInt()
        val cyan = 0xFF89C0D5.toInt()

        val card = FrameLayout(a).apply {
            background = rounded(navyTop, (0x4A000000.toInt() or (cyan and 0x00FFFFFF)), 28)
            clipToOutline = true; elevation = dp(18).toFloat()
        }
        val dragon = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.64f; contentDescription = "MaTRIX animated dragon" }
        card.addView(dragon, FrameLayout.LayoutParams(-1, -1))
        val veil = View(a).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf((0xC0000000.toInt() or (navyTop and 0x00FFFFFF)), (0xA8000000.toInt() or (navyTop and 0x00FFFFFF)), (0xEC000000.toInt() or (navyBottom and 0x00FFFFFF))))
        }
        card.addView(veil, FrameLayout.LayoutParams(-1, -1))
        val content = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR; setPadding(dp(20), dp(18), dp(20), dp(18)) }
        card.addView(content, FrameLayout.LayoutParams(-1, -2))

        val header = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(a).apply {
            text = "  ◆  MATRIX GX  •  SYSTEM UPDATE  "; textSize = 10f; letterSpacing = 0.1f
            setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(violet, magenta)).apply { cornerRadius = dp(20).toFloat() }
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(a).apply {
            text = "×"; textSize = 26f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = rounded(0x442D2840, (0x66000000.toInt() or (cyan and 0x00FFFFFF)), 50)
            setOnClickListener { if (!busy) d.dismiss() else cancelled = true }
        }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginStart = dp(8) })
        content.addView(header)

        content.addView(TextView(a).apply {
            text = "UPDATE AVAILABLE"; textSize = 21f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD); letterSpacing = 0.035f; setPadding(0, dp(16), 0, dp(2))
        })
        content.addView(TextView(a).apply {
            text = "${info.version}  •  ${if (info.size > 0) mb(info.size) + " MB" else "NEW VERSION"}"
            textSize = 13f; gravity = Gravity.CENTER; setTextColor(teal); typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); setPadding(0, 0, 0, dp(12))
        })
        if (info.notes.isNotBlank()) content.addView(TextView(a).apply {
            text = info.notes.trim(); textSize = 12f; setTextColor(0xFFDCD6EB.toInt()); gravity = Gravity.CENTER
            maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END; setLineSpacing(0f, 1.12f)
            setPadding(dp(8), dp(4), dp(8), dp(10))
        })
        val status = TextView(a).apply { textSize = 11f; setTextColor(0xFFDCD2F0.toInt()); gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(7)); visibility = View.GONE }
        content.addView(status)
        val barTrack = rounded(tealDark, radius = 7)
        val bar = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = android.content.res.ColorStateList.valueOf(teal)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(tealDark)
            progressDrawable?.let { it.setTint(teal) }
            background = barTrack; clipToOutline = true; visibility = View.GONE
        }
        content.addView(bar, LinearLayout.LayoutParams(-1, dp(8)))
        val metrics = TextView(a).apply {
            text = "SECURE UPDATE  •  RESUME SUPPORTED"; textSize = 9f; gravity = Gravity.CENTER; letterSpacing = 0.08f
            setTextColor(cyan); setPadding(0, dp(11), 0, dp(3))
        }
        content.addView(metrics)
        val actions = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(13), 0, 0) }
        content.addView(actions)

        fun actionButton(label: String, primary: Boolean, click: () -> Unit) = TextView(a).apply {
            text = label; textSize = 13f; gravity = Gravity.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = if (primary) GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(violet, magenta)).apply { cornerRadius = dp(15).toFloat() }
                else rounded(0x552A2638, (0x66000000.toInt() or (cyan and 0x00FFFFFF)), 15)
            setPadding(dp(12), dp(13), dp(12), dp(13)); isClickable = true; isFocusable = true; setOnClickListener { click() }
        }
        lateinit var buildButtons: () -> Unit
        fun start() {
            if (!canInstall()) {
                a.toastMsg("فعّل السماح من هذا المصدر للمتصفح، وارجع واضغط تحديث")
                try { a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName))) } catch (_: Exception) {}
                return
            }
            busy = true; cancelled = false; actions.removeAllViews()
            actions.addView(actionButton("إيقاف التنزيل مؤقتًا", false) { cancelled = true })
            status.visibility = View.VISIBLE; bar.visibility = View.VISIBLE
            metrics.text = "RESUMABLE DOWNLOAD  •  KEEP THIS UPDATE"
            status.text = "جارٍ تجهيز التنزيل..."
            Thread {
                try {
                    val file = Updater.download(a, info, { done, total -> a.runOnUiThread {
                        val pct = if (total > 0L) (done * 100L / total).toInt().coerceIn(0, 100) else 0
                        bar.progress = pct
                        status.text = if (total > 0L) "DOWNLOADING  •  ${mb(done)} / ${mb(total)} MB  •  $pct%" else "DOWNLOADING  •  ${mb(done)} MB"
                    } }, { cancelled })
                    a.runOnUiThread { status.text = "اكتمل التنزيل — جارٍ فتح مُثبّت Android..." }
                    Updater.install(a, file)
                    a.runOnUiThread { d.dismiss() }
                } catch (e: Exception) {
                    a.runOnUiThread {
                        busy = false
                        status.text = if (cancelled) "تم الاحتفاظ بالجزء الذي تم تنزيله — اضغط استكمال عند عودة الإنترنت" else "⚠ ${e.message ?: "تعذر إكمال التنزيل"}".take(130)
                        bar.visibility = View.GONE; metrics.text = "PARTIAL FILE PRESERVED  •  RESUME READY"; buildButtons()
                    }
                }
            }.start()
        }
        buildButtons = {
            actions.removeAllViews()
            actions.addView(actionButton("⬇   DOWNLOAD / RESUME UPDATE", true) { start() })
            val secondary = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            val p1 = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(8); marginEnd = dp(5) }
            val p2 = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(8); marginStart = dp(5) }
            secondary.addView(actionButton("لاحقًا", false) { d.dismiss() }, p1)
            secondary.addView(actionButton("تخطي الإصدار", false) { Cfg.setStr("upd_skip", info.tag); d.dismiss() }, p2)
            actions.addView(secondary)
        }
        buildButtons()
        d.setCancelable(false)
        d.setOnKeyListener { _, code, ev ->
            if (code == android.view.KeyEvent.KEYCODE_BACK && ev.action == android.view.KeyEvent.ACTION_UP) { if (busy) cancelled = true else d.dismiss(); true } else false
        }
        d.setContentView(card)
        d.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); w.setDimAmount(0.78f); w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setLayout(minOf(dp(380), a.resources.displayMetrics.widthPixels - dp(28)), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        d.setOnDismissListener { try { Glide.with(a).clear(dragon) } catch (_: Exception) {} }
        d.show()
        d.window?.setLayout(minOf(dp(380), a.resources.displayMetrics.widthPixels - dp(28)), ViewGroup.LayoutParams.WRAP_CONTENT)
        Glide.with(a).asGif().load(R.drawable.matrix_dragon).into(dragon)
    }

}

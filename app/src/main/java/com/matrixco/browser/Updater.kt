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
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
        val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
        val f = File(dir, "matrix-update.apk")
        if (f.exists()) f.delete()
        val c = URL(info.url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "MaTrIX-Co-Updater")
        try {
            if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
            val total = if (c.contentLengthLong > 0) c.contentLengthLong else info.size
            val md = MessageDigest.getInstance("SHA-256")
            c.inputStream.use { inp ->
                f.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    var done = 0L; var last = 0L
                    while (true) {
                        val n = inp.read(buf)
                        if (n <= 0) break
                        if (cancelled()) throw IOException("cancelled")
                        out.write(buf, 0, n); md.update(buf, 0, n); done += n
                        val now = System.currentTimeMillis()
                        if (now - last > 150) { last = now; onProgress(done, total) }
                    }
                    onProgress(done, total)
                }
            }
            if (f.length() < 100_000) { f.delete(); throw IOException("الملف صغير بشكل مريب") }
            val exp = info.sha256
            if (exp != null) {
                val got = md.digest().joinToString("") { "%02x".format(it) }
                if (!got.equals(exp, true)) { f.delete(); throw IOException("الملف تالف (sha256 مختلف)") }
            }
            return f
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
                if (confirm != null) { confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); try { ctx.startActivity(confirm) } catch (_: Exception) {} }
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
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            background = Ui.round(th.surface, dp(24).toFloat())
        }
        box.addView(TextView(a).apply { text = "تحديث جديد متاح"; textSize = 18f; setTextColor(th.text); typeface = Typeface.DEFAULT_BOLD })
        box.addView(TextView(a).apply {
            text = "$cur  ←  ${info.version}" + (if (info.size > 0) "   •   " + mb(info.size) + " MB" else "")
            textSize = 13f; setTextColor(th.accent); setPadding(0, dp(4), 0, dp(10))
        })
        val notes = info.notes.trim()
        if (notes.isNotEmpty()) {
            val sv = ScrollView(a)
            sv.addView(TextView(a).apply { text = notes; textSize = 13f; setTextColor(th.sub); setLineSpacing(0f, 1.2f) })
            box.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(130)))
        }
        val status = TextView(a).apply { textSize = 12f; setTextColor(th.sub); setPadding(0, dp(12), 0, dp(4)); visibility = View.GONE }
        val bar = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }
        box.addView(status); box.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)))
        val row = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(14), 0, 0) }
        box.addView(row)

        lateinit var buildButtons: () -> Unit
        fun start() {
            if (!canInstall()) {
                a.toastMsg("فعّل \"السماح من هذا المصدر\" للمتصفح، وارجع واضغط تحديث")
                try { a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName))) } catch (_: Exception) {}
                return
            }
            busy = true; cancelled = false
            row.removeAllViews()
            row.addView(ui.button("إلغاء", false) { cancelled = true })
            status.visibility = View.VISIBLE; bar.visibility = View.VISIBLE
            status.text = "⏳ جاري التنزيل..."
            Thread {
                try {
                    val f = Updater.download(a, info, { done, total ->
                        a.runOnUiThread {
                            val pct = if (total > 0) (done * 100 / total).toInt() else 0
                            bar.progress = pct
                            status.text = "جاري التنزيل: " + mb(done) + " / " + mb(total) + " MB  (" + pct + "%)"
                        }
                    }, { cancelled })
                    a.runOnUiThread { status.text = "جاري التثبيت... أكّد من نافذة النظام" }
                    Updater.install(a, f)
                    a.runOnUiThread { d.dismiss() }
                } catch (e: Exception) {
                    a.runOnUiThread {
                        busy = false
                        status.text = if (cancelled) "اتلغى التنزيل" else "⚠ فشل: " + (e.message ?: "خطأ").take(80)
                        bar.visibility = View.GONE
                        buildButtons()
                    }
                }
            }.start()
        }
        buildButtons = {
            row.removeAllViews()
            row.addView(ui.button("حدّث الآن") { start() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val links = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            links.addView(TextView(a).apply {
                text = "لاحقًا"; textSize = 13f; setTextColor(th.sub); setPadding(dp(14), dp(12), dp(14), dp(4))
                setOnClickListener { d.dismiss() }
            })
            links.addView(TextView(a).apply {
                text = "تخطي النسخة دي"; textSize = 13f; setTextColor(th.sub); setPadding(dp(14), dp(12), dp(14), dp(4))
                setOnClickListener { Cfg.setStr("upd_skip", info.tag); d.dismiss() }
            })
            row.addView(links)
        }
        buildButtons()
        d.setCancelable(false)
        d.setOnKeyListener { _, code, ev ->
            if (code == android.view.KeyEvent.KEYCODE_BACK && ev.action == android.view.KeyEvent.ACTION_UP) {
                if (busy) cancelled = true else d.dismiss()
                true
            } else false
        }
        d.setContentView(box)
        d.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout(minOf(dp(360), a.resources.displayMetrics.widthPixels - dp(24)), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        d.show()
    }
}

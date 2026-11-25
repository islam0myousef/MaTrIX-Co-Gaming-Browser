package com.matrixco.browser

import android.app.DownloadManager
import android.content.Context
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * خزنة التحميل (MaTrIX Vault): فحص قبل التحميل وبعده، وبيحذف أي ملف مش متأكد منه.
 *  قبل: منع من غير سؤال لو المصدر خبيث/مريب، الملف تنفيذي ويندوز، امتداد مزدوج، نوع متنكّر، apk من http.
 *  بعد: قراءة بصمة الملف الحقيقية (magic bytes)، SHA-256، فحص سحابي للبصمة (VirusTotal بمفتاح المستخدم)،
 *  وتشريح الـ APK (X-Ray) وحذف الأنماط الخبيثة. لو السحابة علّمت على الملف بيتحذف زي أي حجب.
 *  مفيش مكتبات: الفحص المحلي كله جوه التطبيق، والسحابي طلب GET واحد بالبصمة بس.
 */
object DownloadVault {
    const val ON = "dl_vault"
    const val OK = 0
    const val BLOCKED = 1
    const val SKIP = 2

    const val VT_NONE = 0   // مفيش مفتاح — الفحص السحابي مش مفعّل
    const val VT_CLEAN = 1  // نظيف سحابيًا (أو البصمة غير معروفة)
    const val VT_SKIP = 2   // اتخطى (خطأ/معدل) — الحكم المحلي بس
    const val VT_HIT = 3    // السحابة علّمت عليه

    class Result(val state: Int, val name: String, val sha: String, val title: String, val detail: String, val isApk: Boolean = false, val vtState: Int = VT_NONE)

    private val hardExt = setOf("exe", "msi", "bat", "cmd", "scr", "vbs", "ps1", "dmg", "jar", "sh", "com", "pif", "lnk", "hta", "wsf", "dll", "apk.bin")
    private val apkExt = setOf("apk", "apks", "xapk")
    private val officeZip = setOf("docx", "xlsx", "pptx", "odt", "ods", "odp", "epub", "cbz", "kmz", "aar", "whl", "crx", "ipa")
    private val dangerMime = listOf("package-archive", "x-msdownload", "x-dosexec", "x-msi", "x-sh", "x-bat", "x-executable", "java-archive", "x-apple-diskimage")
    private val doubleExt = Regex("\\.(pdf|jpe?g|png|gif|docx?|xlsx?|pptx?|mp4|mp3|txt|zip|rar|7z|html?)\\.(apk|exe|scr|bat|cmd|msi|vbs|ps1|jar|js|sh|lnk|hta)$")

    /** روابط المستخدم اللي تخطّى فيها الحجب بوعي (وضع الخبير) — بتتفحص وتتعرض بس مش بتتحذف */
    val overrides: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun ext(name: String): String = name.lowercase(Locale.ROOT).substringAfterLast('.', "")

    /** بيرجّع سبب المنع، أو null لو مفيش سبب */
    fun preCheck(ctx: Context, url: String, name: String, mime: String?, pageUrl: String?): String? {
        if (!Cfg.bool(ON, true)) return null
        val lower = name.lowercase(Locale.ROOT)
        val e = ext(lower)
        val host = try { android.net.Uri.parse(url).host?.lowercase(Locale.ROOT) ?: "" } catch (_: Exception) { "" }
        val pageHost = try { if (pageUrl != null) android.net.Uri.parse(pageUrl).host?.lowercase(Locale.ROOT) ?: "" else "" } catch (_: Exception) { "" }
        if (host.isNotEmpty() && AdBlocker.categoryOf(host) == Cat.MAL) return "مصدر التحميل ($host) في قوائم البرمجيات الخبيثة"
        if (pageHost.isNotEmpty() && pageHost != host && AdBlocker.categoryOf(pageHost) == Cat.MAL) return "الصفحة اللي بدأت التحميل ($pageHost) في قوائم الخبيث"
        if (name.contains('\u202E')) return "اسم الملف فيه حرف عكس اتجاه مخفي (خدعة لتغيير الامتداد)"
        if (doubleExt.containsMatchIn(lower)) return "امتداد مزدوج مخادع: الملف شكله مستند وهو في الحقيقة تنفيذي"
        if (e in hardExt) return "ملف تنفيذي (." + e + ") ملوش لازمة على أندرويد وهو أشهر ناقل للبرمجيات الخبيثة"
        val m = (mime ?: "").lowercase(Locale.ROOT)
        if (dangerMime.any { m.contains(it) } && e !in apkExt && e !in hardExt) return "نوع الملف المعلن ($m) مش مطابق لاسمه (.$e)"
        if (e in apkExt) {
            if (url.startsWith("http://", true)) return "تطبيق APK من اتصال غير مشفّر (HTTP): ممكن يتبدّل في الطريق"
            val v = try { IronGuard.scoreUrl(ctx, url, 0) } catch (_: Exception) { null }
            if (v != null) return "مصدر التطبيق مريب: " + v.reason
        }
        return null
    }

    private fun hex(b: ByteArray): String { val sb = StringBuilder(); for (x in b) sb.append(String.format("%02x", x)); return sb.toString() }

    private val permLabels = linkedMapOf(
        "BIND_ACCESSIBILITY_SERVICE" to "التحكم في الشاشة (Accessibility)",
        "READ_SMS" to "قراءة الرسائل", "RECEIVE_SMS" to "التقاط الرسائل", "SEND_SMS" to "إرسال رسائل",
        "SYSTEM_ALERT_WINDOW" to "الرسم فوق التطبيقات", "BIND_DEVICE_ADMIN" to "مدير الجهاز",
        "REQUEST_INSTALL_PACKAGES" to "تثبيت تطبيقات", "READ_CONTACTS" to "جهات الاتصال",
        "RECORD_AUDIO" to "المايك", "READ_CALL_LOG" to "سجل المكالمات", "BIND_NOTIFICATION_LISTENER_SERVICE" to "قراءة الإشعارات"
    )

    private fun has(b: ByteArray, s: String): Boolean {
        val u8 = s.toByteArray(Charsets.UTF_8); val u16 = s.toByteArray(Charsets.UTF_16LE)
        return indexOf(b, u8) >= 0 || indexOf(b, u16) >= 0
    }
    private fun indexOf(h: ByteArray, n: ByteArray): Int {
        if (n.isEmpty() || h.size < n.size) return -1
        outer@ for (i in 0..h.size - n.size) { for (j in n.indices) if (h[i + j] != n[j]) continue@outer; return i }
        return -1
    }

    class ZipInfo(val manifest: ByteArray?, val hasDex: Boolean, val entries: Int)

    private fun zipInfo(ctx: Context, dm: DownloadManager, id: Long): ZipInfo {
        var manifest: ByteArray? = null; var dex = false; var n = 0
        try {
            dm.openDownloadedFile(id).use { pfd ->
                ZipInputStream(FileInputStream(pfd.fileDescriptor).buffered()).use { z ->
                    while (n < 4000) {
                        val en = z.nextEntry ?: break
                        n++
                        val nm = en.name
                        if (nm == "AndroidManifest.xml") {
                            val bo = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192); var t = 0
                            while (t < 3_000_000) { val r = z.read(buf); if (r <= 0) break; bo.write(buf, 0, r); t += r }
                            manifest = bo.toByteArray()
                        } else if (nm.startsWith("classes") && nm.endsWith(".dex")) dex = true
                        if (manifest != null && dex) break
                    }
                }
            }
        } catch (_: Throwable) {}
        return ZipInfo(manifest, dex, n)
    }

    /** بيتنادى من thread خلفي بعد اكتمال التحميل */
    fun scan(ctx: Context, id: Long): Result {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var name = ""; var status = -1; var uri = ""
        try {
            dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: ""
                    status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    uri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)) ?: ""
                }
            }
        } catch (_: Throwable) {}
        if (status != DownloadManager.STATUS_SUCCESSFUL) return Result(SKIP, name, "", "", "")
        val bypass = overrides.remove(uri)
        val head = ByteArray(8); var got = 0; var sha = ""
        try {
            val md = MessageDigest.getInstance("SHA-256")
            dm.openDownloadedFile(id).use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { fi ->
                    val buf = ByteArray(65536); var first = true
                    while (true) {
                        val r = fi.read(buf); if (r <= 0) break
                        if (first) { got = minOf(8, r); System.arraycopy(buf, 0, head, 0, got); first = false }
                        md.update(buf, 0, r)
                    }
                }
            }
            sha = hex(md.digest())
        } catch (_: Throwable) { return Result(SKIP, name, "", "", "") }

        // ☁️ فحص سحابي بالبصمة (VirusTotal) — بيبعت SHA-256 بس، ولو علّمت عليه بيتحذف فورًا.
        //  بيتعمل لأي ملف وصل لهنا (حتى المتخطى بوضع الخبير)، وأي فشل في الخدمة = تخطي مش منع.
        var vtState = VT_NONE
        var vtLine: String? = null
        val vtKey = Cfg.str(Cfg.VTKEY, "")
        if (vtKey.isNotEmpty()) {
            val v = try { VirusTotal.lookup(vtKey, sha) } catch (_: Throwable) { VirusTotal.Verdict(VirusTotal.SKIPPED, message = "خطأ غير متوقع", sha = sha) }
            when (v.state) {
                VirusTotal.DETECTED -> { vtState = VT_HIT; vtLine = "☁️ الفحص السحابي: " + v.detections + "/" + v.total + " محرك علّم عليه\n" + v.link }
                VirusTotal.CLEAN -> { vtState = VT_CLEAN; vtLine = "☁️ الفحص السحابي: نظيف (0/" + v.total + " محرك)\n" + v.link }
                VirusTotal.UNKNOWN -> { vtState = VT_CLEAN; vtLine = "☁️ الفحص السحابي: البصمة غير معروفة لـ VirusTotal (مفيش سجل خبيث ليها)" }
                else -> { vtState = VT_SKIP; vtLine = "☁️ الفحص السحابي اتخطى: " + v.message }
            }
        }
        fun b(i: Int) = if (i < got) head[i].toInt() and 0xff else -1
        val pe = b(0) == 0x4D && b(1) == 0x5A
        val elf = b(0) == 0x7F && b(1) == 0x45 && b(2) == 0x4C && b(3) == 0x46
        val dexMagic = b(0) == 0x64 && b(1) == 0x65 && b(2) == 0x78 && b(3) == 0x0A
        val zip = b(0) == 0x50 && b(1) == 0x4B && (b(2) == 3 || b(2) == 5)
        val e = ext(name)
        val declaredExec = e in hardExt || e in apkExt
        fun block(why: String): Result {
            val extra = if (vtLine != null) "\n" + vtLine else ""
            if (bypass) return Result(OK, name, sha, "⚠ اتخطى الحجب (وضع الخبير)", why + extra + "\nSHA-256: " + sha, false, vtState)
            try { dm.remove(id) } catch (_: Throwable) {}
            return Result(BLOCKED, name, sha, "🛡️ الخزنة حذفت الملف", why + extra + "\nSHA-256: " + sha, false, vtState)
        }
        if (vtState == VT_HIT) return block("الفحص السحابي (VirusTotal): محركات حماية علّمت على بصمة الملف")
        if ((pe || elf || dexMagic) && !declaredExec) return block("محتوى الملف تنفيذي (" + (if (pe) "Windows EXE" else if (elf) "ELF" else "DEX") + ") لكنه متنكّر باسم .$e")
        if (e in hardExt && e !in apkExt && (pe || elf)) return block("ملف تنفيذي حقيقي ($e)")
        var isApk = false
        if (zip && e !in officeZip) {
            val zi = zipInfo(ctx, dm, id)
            val manifestOk = zi.manifest != null && zi.hasDex
            if (manifestOk && e !in apkExt && e != "jar") return block("ده تطبيق أندرويد (APK) متنكّر باسم .$e")
            if (e in apkExt && e != "apks" && e != "xapk" && !manifestOk) return block("امتداده APK لكن مفيش جواه تطبيق حقيقي (AndroidManifest/classes.dex ناقصين)")
            if (manifestOk && zi.manifest != null) {
                isApk = true
                val found = permLabels.filter { has(zi.manifest, "android.permission." + it.key) }
                val acc = found.containsKey("BIND_ACCESSIBILITY_SERVICE")
                val sms = found.containsKey("READ_SMS") || found.containsKey("RECEIVE_SMS")
                val overlay = found.containsKey("SYSTEM_ALERT_WINDOW")
                val admin = found.containsKey("BIND_DEVICE_ADMIN")
                val listener = found.containsKey("BIND_NOTIFICATION_LISTENER_SERVICE")
                val banker = (acc && (sms || overlay || listener)) || (sms && overlay) || (admin && (acc || sms))
                val list = if (found.isEmpty()) "مفيش صلاحيات حساسة" else found.values.joinToString("، ")
                if (banker) return block("نمط تروجان بنكي: التطبيق بيطلب مجموعة صلاحيات خطيرة مع بعض (" + list + ")")
                val vtTail = if (vtLine != null) "\n" + vtLine else "\n\nتقدر تقارن البصمة على VirusTotal قبل التثبيت."
                return Result(OK, name, sha, "🔬 أشعة MaTrIX على التطبيق", "الصلاحيات الحساسة: " + list + "\nSHA-256: " + sha + vtTail +
                    "\n\nالتطبيق لازم تثبّته بإيدك.", true, vtState)
            }
        }
        val tail = if (vtLine != null) "\n" + vtLine else ""
        return Result(OK, name, sha, "", "SHA-256: $sha" + tail, false, vtState)
    }
}

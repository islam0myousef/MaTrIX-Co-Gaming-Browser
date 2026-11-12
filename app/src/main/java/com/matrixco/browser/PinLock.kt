package com.matrixco.browser

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * قفل الإعدادات بـ PIN (4–8 أرقام) + بصمة اختيارية.
 * - الـ PIN متخزّن كهاش PBKDF2 بملح عشوائي، مش نص.
 * - بعد كل 5 محاولات غلط: انتظار 30 ثانية، وبيتضاعف لحد ~16 دقيقة.
 * - بعد الفتح بنجاح الجلسة بتفضل مفتوحة 5 دقايق أو لحد ما تخرج من المتصفح.
 * - "نسيت الـ PIN" بتعدّي بقفل شاشة الجهاز.
 */
class PinLock(private val a: MainActivity, private val ui: UiKit) {

    companion object {
        const val ON = "pin_on"
        const val HASH = "pin_hash"
        const val SALT = "pin_salt"
        const val BIO = "pin_bio"
        private const val FAILS = "pin_fails"
        private const val UNTIL = "pin_until"
        private const val SESSION_MS = 5 * 60_000L
        private const val ITER = 20000
        private const val ALGO = "pin_algo"

        @Volatile private var unlockedUntil = 0L
        fun enabled() = Cfg.bool(ON, false) && Cfg.str(HASH, "").isNotEmpty()
        fun sessionOpen() = System.currentTimeMillis() < unlockedUntil
        fun lockNow() { unlockedUntil = 0L }
        fun bioAvailable() = Build.VERSION.SDK_INT >= 28
    }

    private fun dp(v: Int) = ui.dp(v)
    private val th get() = ui.theme

    // ───────── التخزين ─────────
    // v2 = PBKDF2-SHA256 بـ 120 ألف دورة (أندرويد 8+)، والقديم SHA1 بـ 20 ألف — بنفضل نقبل القديم ونرقّيه تلقائيًا أول ما الـ PIN يتكتب صح
    private fun newAlgo() = Build.VERSION.SDK_INT >= 26
    private fun hash(pin: String, salt: ByteArray, v2: Boolean = newAlgo()): ByteArray {
        val spec = if (v2) PBEKeySpec(pin.toCharArray(), salt, 120_000, 256) else PBEKeySpec(pin.toCharArray(), salt, ITER, 256)
        val alg = if (v2) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        return SecretKeyFactory.getInstance(alg).generateSecret(spec).encoded
    }

    private fun savePin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        Cfg.setStr(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
        Cfg.setStr(ALGO, if (newAlgo()) "2" else "1")
        Cfg.setStr(HASH, Base64.encodeToString(hash(pin, salt, newAlgo()), Base64.NO_WRAP))
        Cfg.setBool(ON, true)
        Cfg.setInt(FAILS, 0); Cfg.setStr(UNTIL, "0")
    }

    private fun check(pin: String): Boolean = try {
        val salt = Base64.decode(Cfg.str(SALT), Base64.NO_WRAP)
        val stored = Base64.decode(Cfg.str(HASH), Base64.NO_WRAP)
        val v2 = Cfg.str(ALGO, "") == "2"
        val ok = MessageDigest.isEqual(stored, hash(pin, salt, v2))
        if (ok && !v2 && newAlgo()) { try { savePin(pin) } catch (_: Exception) {} }
        ok
    } catch (_: Exception) { false }

    fun disable() { Cfg.setBool(ON, false); Cfg.setStr(HASH, ""); Cfg.setStr(SALT, ""); Cfg.setBool(BIO, false); lockNow() }

    /** بعد التحقق بقفل شاشة الجهاز */
    fun resetByDevice() { disable(); unlockedUntil = System.currentTimeMillis() + SESSION_MS }

    private fun waitMs(): Long = maxOf(0L, (Cfg.str(UNTIL, "0").toLongOrNull() ?: 0L) - System.currentTimeMillis())

    private fun registerFail() {
        val n = Cfg.int(FAILS, 0) + 1
        Cfg.setInt(FAILS, n)
        if (n % 5 == 0) {
            val mult = 1 shl minOf(n / 5 - 1, 5)
            Cfg.setStr(UNTIL, (System.currentTimeMillis() + 30_000L * mult).toString())
        }
    }

    private fun success() { unlockedUntil = System.currentTimeMillis() + SESSION_MS; Cfg.setInt(FAILS, 0); Cfg.setStr(UNTIL, "0") }

    // ───────── الواجهة العامة ─────────

    /** بينفّذ onOk لو القفل مش شغّال أو الجلسة مفتوحة، وإلا بيطلب الـ PIN */
    fun ask(title: String = "أدخل الـ PIN", onOk: () -> Unit) {
        if (!enabled() || sessionOpen()) { onOk(); return }
        val useBio = Cfg.bool(BIO, false) && bioAvailable()
        pad(title, "لفتح الإعدادات والتحكم في الحماية", forgot = true, bioOk = if (useBio) ({ success(); onOk() }) else null) { pin ->
            val w = waitMs()
            when {
                w > 0 -> "استنى " + (w / 1000 + 1) + " ثانية قبل ما تجرّب تاني"
                check(pin) -> { success(); onOk(); null }
                else -> {
                    registerFail()
                    val w2 = waitMs()
                    if (w2 > 0) "اتقفل مؤقتًا " + (w2 / 1000 + 1) + " ثانية" else "PIN غلط — باقي " + (5 - Cfg.int(FAILS, 0) % 5) + " محاولات"
                }
            }
        }
    }

    /** إنشاء PIN جديد (مرتين للتأكيد) */
    fun setup(onDone: () -> Unit) {
        pad("اختر PIN جديد", "من 4 إلى 8 أرقام", forgot = false, bioOk = null) { p1 ->
            if (p1.length < 4) return@pad "لازم 4 أرقام على الأقل"
            a.window.decorView.post {
                pad("أكّد الـ PIN", "اكتبه مرة تانية", forgot = false, bioOk = null) { p2 ->
                    if (p2 != p1) "مش متطابق — جرّب تاني"
                    else { savePin(p2); success(); a.toastMsg("✅ قفل الإعدادات اتفعّل"); onDone(); null }
                }
            }
            null
        }
    }

    // ───────── لوحة الأرقام ─────────

    private fun pad(title: String, sub: String, forgot: Boolean, bioOk: (() -> Unit)?, onSubmit: (String) -> String?) {
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val buf = StringBuilder()
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(18), dp(20), dp(18), dp(14))
            background = Ui.round(th.surface, dp(26).toFloat())
        }
        box.addView(TextView(a).apply { text = title; textSize = 18f; setTextColor(th.text); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER })
        box.addView(TextView(a).apply { text = sub; textSize = 12f; setTextColor(th.sub); gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(12)) })
        val dots = TextView(a).apply { textSize = 24f; setTextColor(th.text); gravity = Gravity.CENTER }
        val msg = TextView(a).apply { textSize = 12f; setTextColor(Color.rgb(255, 120, 110)); gravity = Gravity.CENTER; minHeight = dp(20); setPadding(0, dp(6), 0, dp(6)) }
        fun refresh() {
            val n = maxOf(4, buf.length)
            dots.text = (0 until n).joinToString("  ") { if (it < buf.length) "●" else "○" }
        }
        refresh()
        box.addView(dots, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(msg, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fun submit() {
            val err = onSubmit(buf.toString())
            if (err == null) d.dismiss() else { msg.text = err; buf.setLength(0); refresh() }
        }
        fun key(label: String, bold: Boolean = true, click: () -> Unit): View = TextView(a).apply {
            text = label; textSize = 22f; gravity = Gravity.CENTER; setTextColor(th.text)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            background = Ui.round(th.surface2, dp(34).toFloat())
            setOnClickListener { click() }
        }
        fun rowOf(vararg v: View) {
            val r = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            for (x in v) r.addView(x, LinearLayout.LayoutParams(dp(68), dp(68)).apply { setMargins(dp(6), dp(5), dp(6), dp(5)) })
            box.addView(r)
        }
        fun digit(n: Int) = key(n.toString()) { if (buf.length < 8) { buf.append(n); msg.text = ""; refresh() } }
        rowOf(digit(1), digit(2), digit(3))
        rowOf(digit(4), digit(5), digit(6))
        rowOf(digit(7), digit(8), digit(9))
        val bioKey: View = if (bioOk != null) key("بصمة", false) { runBio { d.dismiss(); bioOk() } }.also { (it as TextView).textSize = 12f } else View(a)
        rowOf(bioKey, digit(0), key("⌫", false) { if (buf.isNotEmpty()) { buf.setLength(buf.length - 1); refresh() } })

        box.addView(ui.button("تأكيد") { submit() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        val links = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        if (forgot) links.addView(TextView(a).apply {
            text = "نسيت الـ PIN؟"; textSize = 12f; setTextColor(th.accent); setPadding(dp(12), dp(10), dp(12), dp(4))
            setOnClickListener { d.dismiss(); a.pinForgot() }
        })
        links.addView(TextView(a).apply {
            text = "إلغاء"; textSize = 12f; setTextColor(th.sub); setPadding(dp(12), dp(10), dp(12), dp(4))
            setOnClickListener { d.cancel() }
        })
        box.addView(links)

        d.setContentView(box)
        d.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout(minOf(dp(340), a.resources.displayMetrics.widthPixels - dp(24)), ViewGroup.LayoutParams.WRAP_CONTENT)
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); w.setDimAmount(0.6f)
            w.addFlags(WindowManager.LayoutParams.FLAG_SECURE)   // يمنع لقطة الشاشة وإنت بتكتب الـ PIN
        }
        d.show()
        if (bioOk != null) runBio { d.dismiss(); bioOk() }
    }

    // ───────── البصمة (أندرويد 9+) ─────────

    @android.annotation.SuppressLint("NewApi")
    private fun runBio(onOk: () -> Unit) {
        if (Build.VERSION.SDK_INT < 28) return
        try {
            val exec = a.mainExecutor
            val prompt = android.hardware.biometrics.BiometricPrompt.Builder(a)
                .setTitle("MaTrIX Co")
                .setSubtitle("أكّد هويتك")
                .setNegativeButton("استخدم الـ PIN", exec) { _, _ -> }
                .build()
            prompt.authenticate(android.os.CancellationSignal(), exec, object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) { onOk() }
            })
        } catch (_: Throwable) {}
    }
}

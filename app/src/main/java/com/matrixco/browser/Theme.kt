package com.matrixco.browser

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class MxTheme(
    val id: String, val name: String,
    val bg: Int, val surface: Int, val surface2: Int,
    val accent: Int, val accent2: Int, val text: Int, val sub: Int, val light: Boolean
)

object Themes {
    private fun c(r: Int, g: Int, b: Int) = Color.rgb(r, g, b)

    private val midnight = MxTheme("midnight", "Midnight", c(31, 33, 40), c(43, 46, 55), c(54, 58, 68), c(96, 140, 240), c(160, 190, 250), Color.WHITE, c(168, 172, 184), false)
    private val light = MxTheme("classic", "Light", c(244, 245, 248), Color.WHITE, c(230, 232, 238), c(50, 100, 220), c(70, 110, 200), c(24, 26, 32), c(100, 106, 120), true)

    private val gxClassic = MxTheme("gxclassic", "MaTrIX GX Red", c(8, 7, 13), c(19, 16, 28), c(31, 26, 46), c(255, 27, 77), c(0, 229, 255), Color.WHITE, c(160, 154, 184), false)
    private val whiteWolf = MxTheme("whitewolf", "White Wolf", c(236, 238, 243), Color.WHITE, c(224, 227, 234), c(250, 30, 78), c(200, 40, 90), c(18, 18, 24), c(100, 104, 118), true)

    val all: List<MxTheme> = listOf(
        gxClassic,
        MxTheme("ultraviolet", "Ultra Violet", c(10, 6, 24), c(22, 14, 46), c(36, 24, 70), c(138, 60, 255), c(190, 140, 255), Color.WHITE, c(160, 150, 190), false),
        MxTheme("purplehaze", "Purple Haze", c(20, 6, 28), c(38, 12, 52), c(58, 20, 78), c(220, 60, 200), c(255, 140, 230), Color.WHITE, c(180, 150, 190), false),
        whiteWolf,
        midnight,
        MxTheme("redgold", "Red Gold", c(10, 5, 7), c(24, 11, 14), c(40, 18, 22), c(232, 30, 40), c(248, 192, 72), Color.WHITE, c(176, 150, 150), false),
        MxTheme("crimson", "Crimson", c(12, 4, 10), c(28, 9, 22), c(44, 14, 34), c(220, 20, 90), c(255, 120, 160), Color.WHITE, c(176, 140, 160), false),
        MxTheme("obsidian", "Obsidian", c(6, 6, 8), c(18, 18, 22), c(30, 30, 36), c(210, 210, 225), c(255, 255, 255), Color.WHITE, c(150, 150, 160), false),
        MxTheme("shadow", "Shadow", c(8, 10, 14), c(20, 24, 32), c(32, 38, 50), c(120, 150, 200), c(180, 205, 245), Color.WHITE, c(140, 150, 170), false),
        MxTheme("royal", "Royal", c(10, 6, 20), c(24, 14, 44), c(38, 22, 66), c(150, 80, 255), c(248, 200, 80), Color.WHITE, c(165, 150, 195), false),
        MxTheme("blue", "Blue", c(6, 10, 20), c(14, 24, 42), c(22, 38, 64), c(30, 130, 255), c(90, 210, 255), Color.WHITE, c(140, 160, 190), false),
        MxTheme("purple", "Purple", c(14, 6, 20), c(30, 14, 42), c(46, 22, 64), c(190, 60, 255), c(240, 130, 255), Color.WHITE, c(170, 140, 190), false),
        MxTheme("green", "Green", c(4, 14, 10), c(10, 30, 22), c(16, 46, 34), c(20, 210, 110), c(130, 255, 190), Color.WHITE, c(130, 170, 150), false),
        light
    )

    /** GX default theme: keeps the browser identity consistent across Android light/dark modes. */
    val auto: MxTheme = gxClassic.copy(id = "auto", name = "GX Auto")
    val picker: List<MxTheme> = listOf(auto) + all.filter { it.id != gxClassic.id }

    fun resolve(ctx: Context, id: String?): MxTheme {
        // GX identity is intentionally dark regardless of the Android system theme.
        // Users can still select the explicit Light theme from Appearance settings.
        val base: MxTheme = if (id == null || id == "auto") gxClassic else all.firstOrNull { it.id == id } ?: gxClassic
        val ov = Cfg.int(Cfg.ACCENT, 0)
        return if (ov != 0) base.copy(accent = ov, accent2 = Color.rgb(
            minOf(255, Color.red(ov) + 70), minOf(255, Color.green(ov) + 70), minOf(255, Color.blue(ov) + 70))) else base
    }

    fun byId(id: String?): MxTheme = all.firstOrNull { it.id == id } ?: gxClassic
}

fun Int.hex(): String = String.format("#%06X", 0xFFFFFF and this)

/** لون بصيغة CSS rgba(r,g,b,alpha) — alpha بيتكتب كنص زي ".22" */
fun rgbaStr(c: Int, alpha: String): String = "rgba(" + ((c shr 16) and 255) + "," + ((c shr 8) and 255) + "," + (c and 255) + "," + alpha + ")"
fun Int.withAlpha(a: Int): Int = Color.argb(a, Color.red(this), Color.green(this), Color.blue(this))
fun Int.darker(f: Float = 0.6f): Int = Color.rgb((Color.red(this) * f).toInt(), (Color.green(this) * f).toInt(), (Color.blue(this) * f).toInt())

object Ui {
    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun round(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): Drawable =
        ChamferDrawable(color, 0, false, radiusPx, strokeColor, strokePx, 0, 0)

    fun grad(c1: Int, c2: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0, vertical: Boolean = true): Drawable =
        ChamferDrawable(c1, c2, !vertical, radiusPx, strokeColor, strokePx, 0, 0)

    /** شيت من تحت بحواف علوية مقطوعة وخط نيون رفيع فوق */
    fun topRound(color: Int, radiusPx: Float, edge: Int = 0): Drawable =
        ChamferDrawable(color, 0, false, radiusPx, 0, 0, 1, edge)

    /** شكل سداسي مفلطح لشرائح الأيقونات */
    fun hex(color: Int, strokeColor: Int = 0, strokePx: Int = 0): Drawable =
        ChamferDrawable(color, 0, false, 0f, strokeColor, strokePx, 2, 0)

    fun oval(color: Int): GradientDrawable = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
}

/** إعدادات المتصفح (SharedPreferences) */
object Cfg {
    private lateinit var p: SharedPreferences
    private var ep: SharedPreferences? = null

    fun init(ctx: Context) {
        if (!::p.isInitialized) p = ctx.applicationContext.getSharedPreferences("mx_prefs", Context.MODE_PRIVATE)
        if (ep == null) {
            ep = runCatching {
                EncryptedSharedPreferences.create(
                    ctx.applicationContext,
                    "mx_secure_prefs",
                    MasterKey.Builder(ctx.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }.getOrNull()
        }
        migrateSecrets()
    }

    /** المفاتيح السرّية اللي بتتخزن مشفّرة (زي مفاتيح الـ API) */
    private fun isSecret(k: String) = k == APIKEY || k == VTKEY

    /** ينقل المفاتيح الحساسة من التخزين العادي للتخزين المشفر مرة واحدة */
    private fun migrateSecrets() {
        val sec = ep ?: return
        if (!::p.isInitialized) return
        for (k in listOf(APIKEY, VTKEY)) {
            if (p.contains(k)) {
                sec.edit().putString(k, p.getString(k, "")).apply()
                p.edit().remove(k).apply()
            }
        }
    }

    val prefs: SharedPreferences get() = p
    fun bool(k: String, d: Boolean) = p.getBoolean(k, d)
    fun setBool(k: String, v: Boolean) = p.edit().putBoolean(k, v).apply()
    fun str(k: String, d: String = ""): String =
        if (isSecret(k) && ep != null) ep!!.getString(k, d) ?: d else p.getString(k, d) ?: d
    fun setStr(k: String, v: String) {
        if (isSecret(k) && ep != null) ep!!.edit().putString(k, v).apply()
        else p.edit().putString(k, v).apply()
    }
    fun int(k: String, d: Int) = p.getInt(k, d)
    fun setInt(k: String, v: Int) = p.edit().putInt(k, v).apply()

    const val SHIELD = "shield_on"
    const val ADS = "cat_ads"
    const val TRK = "cat_trk"
    const val MAL = "cat_mal"
    const val SOC = "cat_soc"
    const val ANNOY = "cat_annoy"
    const val SPY = "spy_on"
    const val HTTPS_ONLY = "https_only"
    const val MIXED = "block_mixed"
    const val FP = "fp_noise"
    const val SAFEB = "safe_browsing"
    const val DLWARN = "dl_warn"
    const val DLVAULT = "dl_vault"
    const val EXPERT = "expert_override"
    const val WRECK = "spy_wrecker"
    const val AUTOCLEAR = "autoclear"
    const val CACHE_MB = "cache_mb"
    const val DARK = "dark_pages"
    const val DATASAVER = "datasaver"
    const val BGAUDIO = "bg_audio"
    const val APPLOCK = "app_lock"
    const val APIKEY = "api_key"
    const val VTKEY = "vt_api_key"
    const val MODEL = "api_model"
    const val THEME = "theme"
    const val ENGINE = "engine"
    const val TPCOOK = "tp_cookies"
    const val STRIPTRK = "strip_tracking"
    const val FPS = "fps_overlay"
    const val GESTURES = "gestures"
    const val SHOWFAB = "show_fab"
    const val WALL = "wallpaper"
    const val ACCENT = "accent_override"
    const val SOUND = "sound_pack"
    const val HAPTIC = "haptics"
    const val GXCORNER = "show_gxcorner"
    const val RAMLIM = "ram_limiter"
    const val TABSLEEP = "tab_sleep_min"
    const val GX_PROFILE = "gx_profile"
    const val GX_MOTION = "gx_motion"
    const val CLOCK_FONT = "clock_font"
}


/**
 * رسمة بحواف مقطوعة (Chamfer) على طريقة GX: القطع في الركنين المتقابلين (أعلى-يسار وأسفل-يمين) والباقي قطع أصغر.
 * mode 0: مستطيل بحواف مقطوعة. mode 1: شيت سفلي (الحواف العلوية فقط) + خط علوي بلون edge. mode 2: سداسي.
 */
class ChamferDrawable(
    private val fill: Int, private val fill2: Int, private val horizontal: Boolean,
    private val radius: Float, private val strokeColor: Int, private val strokePx: Int,
    private val mode: Int, private val edge: Int
) : Drawable() {
    private val path = android.graphics.Path()
    private val fp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val sp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE }

    override fun onBoundsChange(b: android.graphics.Rect) {
        val l = b.left.toFloat(); val t = b.top.toFloat(); val r = b.right.toFloat(); val bt = b.bottom.toFloat()
        val w = r - l; val h = bt - t
        path.reset()
        if (w <= 0f || h <= 0f) return
        if (mode == 2) {
            val k = minOf(w, h) * 0.28f
            path.moveTo(l + k, t); path.lineTo(r - k, t); path.lineTo(r, t + h / 2); path.lineTo(r - k, bt); path.lineTo(l + k, bt); path.lineTo(l, t + h / 2)
            path.close()
        } else {
            val big = minOf(radius * 0.6f, minOf(w, h) * 0.3f)
            val small = big * 0.35f
            if (mode == 1) {
                path.moveTo(l + big, t); path.lineTo(r - small, t); path.lineTo(r, t + small); path.lineTo(r, bt); path.lineTo(l, bt); path.lineTo(l, t + big)
            } else {
                path.moveTo(l + big, t); path.lineTo(r - small, t); path.lineTo(r, t + small)
                path.lineTo(r, bt - big); path.lineTo(r - big, bt); path.lineTo(l + small, bt)
                path.lineTo(l, bt - small); path.lineTo(l, t + big)
            }
            path.close()
        }
        fp.shader = if (fill2 != 0) (if (horizontal) android.graphics.LinearGradient(l, 0f, r, 0f, fill, fill2, android.graphics.Shader.TileMode.CLAMP)
            else android.graphics.LinearGradient(0f, t, 0f, bt, fill, fill2, android.graphics.Shader.TileMode.CLAMP)) else null
    }

    override fun draw(c: android.graphics.Canvas) {
        fp.color = fill
        c.drawPath(path, fp)
        if (strokePx > 0 && strokeColor != 0) { sp.color = strokeColor; sp.strokeWidth = strokePx.toFloat(); c.drawPath(path, sp) }
        if (mode == 1 && edge != 0) {
            val b = bounds
            sp.color = edge; sp.strokeWidth = 3f
            val big = minOf(radius * 0.6f, minOf(b.width(), b.height()) * 0.3f)
            c.drawLine(b.left + big, b.top + 1.5f, b.right.toFloat(), b.top + 1.5f, sp)
        }
    }
    override fun setAlpha(a: Int) { fp.alpha = a; sp.alpha = a }
    override fun setColorFilter(cf: android.graphics.ColorFilter?) { fp.colorFilter = cf; sp.colorFilter = cf }
    @Suppress("DEPRECATION") override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

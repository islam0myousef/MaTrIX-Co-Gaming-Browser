package com.matrixco.browser

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

data class MxTheme(
    val id: String, val name: String,
    val bg: Int, val surface: Int, val surface2: Int,
    val accent: Int, val accent2: Int, val text: Int, val sub: Int, val light: Boolean
)

object Themes {
    private fun c(r: Int, g: Int, b: Int) = Color.rgb(r, g, b)

    private val midnight = MxTheme("midnight", "Midnight", c(31, 33, 40), c(43, 46, 55), c(54, 58, 68), c(96, 140, 240), c(160, 190, 250), Color.WHITE, c(168, 172, 184), false)
    private val light = MxTheme("classic", "Light", c(244, 245, 248), Color.WHITE, c(230, 232, 238), c(50, 100, 220), c(70, 110, 200), c(24, 26, 32), c(100, 106, 120), true)

    private val gxClassic = MxTheme("gxclassic", "GX Classic", c(9, 9, 12), c(21, 21, 26), c(36, 36, 44), c(250, 30, 78), c(255, 120, 150), Color.WHITE, c(160, 160, 172), false)
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

    /** "تلقائي": يتبع وضع الجهاز (فاتح/داكن) */
    val auto: MxTheme = midnight.copy(id = "auto", name = "Auto")
    val picker: List<MxTheme> = listOf(auto) + all

    fun resolve(ctx: Context, id: String?): MxTheme {
        val base: MxTheme = if (id == null || id == "auto") {
            val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            if (night) gxClassic else whiteWolf
        } else all.firstOrNull { it.id == id } ?: gxClassic
        val ov = Cfg.int(Cfg.ACCENT, 0)
        return if (ov != 0) base.copy(accent = ov, accent2 = Color.rgb(
            minOf(255, Color.red(ov) + 70), minOf(255, Color.green(ov) + 70), minOf(255, Color.blue(ov) + 70))) else base
    }

    fun byId(id: String?): MxTheme = all.firstOrNull { it.id == id } ?: gxClassic
}

fun Int.hex(): String = String.format("#%06X", 0xFFFFFF and this)
fun Int.withAlpha(a: Int): Int = Color.argb(a, Color.red(this), Color.green(this), Color.blue(this))
fun Int.darker(f: Float = 0.6f): Int = Color.rgb((Color.red(this) * f).toInt(), (Color.green(this) * f).toInt(), (Color.blue(this) * f).toInt())

object Ui {
    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun round(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color); cornerRadius = radiusPx
            if (strokePx > 0) setStroke(strokePx, strokeColor)
        }

    fun grad(c1: Int, c2: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0, vertical: Boolean = true): GradientDrawable =
        GradientDrawable(if (vertical) GradientDrawable.Orientation.TOP_BOTTOM else GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(c1, c2)).apply {
            cornerRadius = radiusPx
            if (strokePx > 0) setStroke(strokePx, strokeColor)
        }

    fun topRound(color: Int, radiusPx: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadii = floatArrayOf(radiusPx, radiusPx, radiusPx, radiusPx, 0f, 0f, 0f, 0f)
        }

    fun oval(color: Int): GradientDrawable = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
}

/** إعدادات المتصفح (SharedPreferences) */
object Cfg {
    private lateinit var p: SharedPreferences
    fun init(ctx: Context) { if (!::p.isInitialized) p = ctx.applicationContext.getSharedPreferences("mx_prefs", Context.MODE_PRIVATE) }
    val prefs: SharedPreferences get() = p
    fun bool(k: String, d: Boolean) = p.getBoolean(k, d)
    fun setBool(k: String, v: Boolean) = p.edit().putBoolean(k, v).apply()
    fun str(k: String, d: String = ""): String = p.getString(k, d) ?: d
    fun setStr(k: String, v: String) = p.edit().putString(k, v).apply()
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
    const val AUTOCLEAR = "autoclear"
    const val CACHE_MB = "cache_mb"
    const val DARK = "dark_pages"
    const val DATASAVER = "datasaver"
    const val BGAUDIO = "bg_audio"
    const val APPLOCK = "app_lock"
    const val APIKEY = "api_key"
    const val MODEL = "api_model"
    const val THEME = "theme"
    const val ENGINE = "engine"
    const val TPCOOK = "tp_cookies"
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
}

package com.matrixco.browser

import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.graphics.drawable.ColorDrawable
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** تبويب واحد */
class Tab(val web: WebView, val priv: Boolean, val popup: Boolean) {
    @Volatile var host: String = ""
    @Volatile var url: String = ""
    @Volatile var title: String = ""
    var pending: String? = null
    var progress: Int = 100
    var desktop = false
    var bookmarked = false
    var skipMode = 1
    var lastPopupAt = 0L
    var opener: Tab? = null
    var thumb: Bitmap? = null
    var sleeping = false
    var pinned = false
    @Volatile var redirects = 0
    var lastActive = System.currentTimeMillis()
    var upgradedFrom: String? = null
    var sslBroken = false
    var lastSpyPromptAt = 0L
    val blocked = AtomicInteger(0)
    val videos = LinkedHashSet<String>()
    val spyFindings = LinkedHashMap<String, Int>()
}

private data class FabItem(val icon: String, val color: Int, val action: () -> Unit)

private class DownloadReq(val url: String, val ua: String?, val cd: String?, val mime: String?)

class MainActivity : Activity() {

    private lateinit var db: ShieldDatabase
    private lateinit var theme: MxTheme
    private lateinit var ui: UiKit
    internal val pinLock: PinLock by lazy { PinLock(this, ui) }
    internal val updateUi: UpdateUi by lazy { UpdateUi(this, ui) }
    private var updChecked = false

    internal fun appVersion(): String = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" }

    private val tabs = ArrayList<Tab>()
    private var cur: Tab? = null
    private val h = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private lateinit var root: FrameLayout
    private lateinit var column: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var addressBar: EditText
    private lateinit var lockView: TextView
    private lateinit var shieldBadge: TextView
    private lateinit var vidBadge: TextView
    private lateinit var starBtn: TextView
    private lateinit var reloadBtn: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var webHolder: FrameLayout
    private lateinit var findBar: LinearLayout
    private lateinit var findInput: EditText
    private lateinit var findCount: TextView
    private lateinit var suggestBox: LinearLayout
    private lateinit var spyBar: LinearLayout
    private lateinit var spyText: TextView
    private lateinit var hud: TextView
    private lateinit var fab: ImageView
    private val slotViews = ArrayList<View>()
    private var tabsIcon: IconDrawable? = null
    private var menuPopup: PopupWindow? = null
    private var shieldPopup: PopupWindow? = null
    private var settings: SettingsScreen? = null

    private var fabMenu: View? = null
    private var fullscreen = false
    private var lastNight = 0
    private var started = false
    private val homeRefresh = Runnable { val t = cur; if (t != null && t.url == Pages.HOME_URL) t.web.reload() }
    private var fabOpen = false
    private var customView: View? = null
    private var customHolder: FrameLayout? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var pendingDownload: DownloadReq? = null
    private var gameMode = false
    private var autoGame = false
    private var baseUa = ""
    private var tickCount = 0
    private var lastStopAt = 0L
    private var locked = false
    private var lockView2: FrameLayout? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lastFps = 0
    private var lastPing = -1
    private var pendingSpy: Triple<Tab, String, String>? = null
    private var dlDialogRunning = false

    private val ticker = object : Runnable {
        override fun run() {
            val t = cur
            if (t != null) {
                shieldBadge.text = t.blocked.get().toString()
                val n = synchronized(t.videos) { t.videos.size }
                vidBadge.visibility = if (n > 0 && !gameMode) View.VISIBLE else View.GONE
                vidBadge.text = "🎬 $n"
            }
            tickCount++
            if (tickCount % 20 == 0) io.execute { AdBlocker.flush() }
            if (gameMode && Cfg.bool(Cfg.FPS, true) && tickCount % 6 == 0) pingGame()
            if (tickCount % 40 == 0) sleepCheck()
            h.postDelayed(this, 800)
        }
    }

    private val dlReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) toast("✅ اكتمل التحميل")
        }
    }

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    // ═════════════════ دورة الحياة ═════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.init(this)
        val crashFile = File(filesDir, "crash.txt")
        if (crashFile.exists()) { showCrashReport(crashFile); return }
        db = ShieldDatabase.getInstance(this)
        theme = Themes.resolve(this, Cfg.str(Cfg.THEME, "auto"))
        lastNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        ui = UiKit(this).also { it.theme = theme }
        AdBlocker.init(this)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        Favicons.onUpdated = { h.removeCallbacks(homeRefresh); h.postDelayed(homeRefresh, 1500) }
        GxCorner.onUpdated = { h.removeCallbacks(homeRefresh); h.postDelayed(homeRefresh, 800) }
        buildUi()
        CookieManager.getInstance().setAcceptCookie(true)
        registerReceiver(dlReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

        val data = intent?.data
        val opened = data != null && (data.scheme == "http" || data.scheme == "https")
        try { if (!restoreSession()) newTab(Pages.HOME_URL) } catch (_: Throwable) { if (tabs.isEmpty()) newTab(Pages.HOME_URL) }
        if (opened) newTab(data.toString())
        if (savedInstanceState == null) try { showSplash() } catch (_: Throwable) {}

        h.postDelayed({
            io.execute {
                if (db.getMeta("needs_vacuum") == "1") db.vacuum()
                val lim = Cfg.int(Cfg.CACHE_MB, 150).toLong() * 1024 * 1024
                if (Storage.compute(this).cache > lim) { Storage.cleanCache(this); h.post { cur?.web?.clearCache(true) } }
            }
            if (RuleUpdater.isDue(this)) {
                RuleUpdater.updateNow(this) { r ->
                    if (r.success) { AdBlocker.refreshRules(this); toast("🛡 تم تحديث الفلاتر: " + r.domainsCount + " نطاق") }
                }
            }
        }, 4000)
        if (Cfg.bool(Cfg.APPLOCK, false) && savedInstanceState == null) lockApp()
        started = true
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val night = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (Cfg.str(Cfg.THEME, "auto") == "auto" && night != lastNight) { lastNight = night; saveSession(); recreate() }
    }

    /** شاشة بسيطة تعرض سبب آخر كراش (بدون أي اعتماد على باقي التطبيق) */
    private fun showCrashReport(f: File) {
        val text = try { f.readText() } catch (_: Exception) { "تعذر قراءة تقرير الكراش" }
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(18, 18, 22)); setPadding(px(14), px(40), px(14), px(14)) }
        col.addView(TextView(this).apply { this.text = "⚠ التطبيق وقف في آخر تشغيل"; textSize = 18f; setTextColor(Color.WHITE) })
        col.addView(TextView(this).apply { this.text = "انسخ النص ده وابعته للمطوّر، وبعدها اضغط متابعة"; textSize = 13f; setTextColor(Color.LTGRAY); setPadding(0, px(6), 0, px(10)) })
        val sv = ScrollView(this)
        sv.addView(TextView(this).apply { this.text = text; textSize = 10.5f; setTextColor(Color.rgb(255, 180, 160)); typeface = Typeface.MONOSPACE; setTextIsSelectable(true) })
        col.addView(sv, LinearLayout.LayoutParams(MATCH, 0, 1f))
        fun btn(t: String, a: () -> Unit) = TextView(this).apply {
            this.text = t; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(px(10), px(12), px(10), px(12))
            setBackgroundColor(Color.rgb(60, 62, 72)); setOnClickListener { a() }
        }
        col.addView(btn("📋 نسخ التقرير") {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("crash", text))
            Toast.makeText(this, "اتنسخ", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = px(8) })
        col.addView(btn("📤 مشاركة") {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "مشاركة التقرير"))
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = px(8) })
        col.addView(btn("▶ متابعة (يمسح التقرير ويجرّب تاني)") { f.delete(); recreate() }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = px(8) })
        setContentView(col)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val d = intent?.data ?: return
        if (d.scheme == "http" || d.scheme == "https") newTab(d.toString())
    }

    override fun onResume() {
        super.onResume()
        if (!updChecked) { updChecked = true; h.postDelayed({ updateUi.checkAuto() }, 4000); h.postDelayed({ maybeAskDefaultBrowser() }, 7000) }
        stopService(Intent(this, AudioService::class.java))
        cur?.web?.onResume()
        h.removeCallbacks(ticker)
        h.post(ticker)
        if (gameMode || customView != null) applySystemBars(true)
        if (Cfg.bool(Cfg.APPLOCK, false) && !locked && lastStopAt > 0 && System.currentTimeMillis() - lastStopAt > 30000) lockApp()
    }

    override fun onPause() {
        saveSession()
        io.execute { AdBlocker.flush() }
        CookieManager.getInstance().flush()
        h.removeCallbacks(ticker)
        if (Cfg.bool(Cfg.BGAUDIO, false) && !isFinishing) {
            try {
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(Intent(this, AudioService::class.java)) else startService(Intent(this, AudioService::class.java))
            } catch (_: Exception) {}
        } else cur?.web?.onPause()
        super.onPause()
    }

    override fun onStop() { lastStopAt = System.currentTimeMillis(); PinLock.lockNow(); if (PinLock.enabled()) settings?.close(); super.onStop() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && (gameMode || customView != null)) applySystemBars(true)
    }

    override fun onDestroy() {
        try { unregisterReceiver(dlReceiver) } catch (_: Exception) {}
        h.removeCallbacksAndMessages(null)
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        if (isFinishing && Cfg.bool(Cfg.AUTOCLEAR, false)) {
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
            cur?.web?.clearCache(true)
            io.execute { db.clearHistory() }
        }
        for (t in tabs) { try { t.web.stopLoading(); t.web.destroy() } catch (_: Exception) {} }
        tabs.clear()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (!::root.isInitialized) { super.onBackPressed(); return }
        val t = cur
        when {
            locked -> {}
            settings != null -> { val sc = settings!!; if (!sc.back()) sc.close() }
            customView != null -> exitCustomView(true)
            fabOpen -> hideFabMenu()
            fullscreen -> setFullscreen(false)
            suggestBox.visibility == View.VISIBLE -> hideSuggest()
            findBar.visibility == View.VISIBLE -> closeFind()
            t != null && t.web.canGoBack() -> t.web.goBack()
            tabs.size > 1 && t != null -> closeTab(t)
            else -> super.onBackPressed()
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQ_FILE -> { fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data)); fileCb = null }
            REQ_VOICE -> {
                val r = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (resultCode == RESULT_OK && r != null) cur?.let { loadInTab(it, r) }
            }
            REQ_LOCK -> { if (resultCode == RESULT_OK) unlockApp() else lockApp() }
            REQ_PINRESET -> if (resultCode == RESULT_OK) { pinLock.resetByDevice(); toast("اتلغى قفل الـ PIN — فعّله تاني من الإعدادات بـ PIN جديد") }
            REQ_BK_EXPORT -> if (resultCode == RESULT_OK) data?.data?.let { uri ->
                io.execute {
                    try {
                        val text = Backup.export(this)
                        contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        h.post { toast("✅ النسخة الاحتياطية اتحفظت") }
                    } catch (_: Exception) { h.post { toast("فشل حفظ الملف") } }
                }
            }
            REQ_BK_IMPORT -> if (resultCode == RESULT_OK) data?.data?.let { uri ->
                io.execute {
                    try {
                        val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                        if (text.length > 20_000_000) throw IllegalArgumentException("too big")
                        val n = Backup.import(this, text)
                        FilterEngine.refreshAsync(this) { _, _ -> }
                        h.post { toast("✅ اتسترجع $n عنصر. القوائم بتتحمّل تاني"); recreate() }
                    } catch (_: Exception) { h.post { toast("الملف ده مش نسخة احتياطية صالحة") } }
                }
            }
            REQ_WALL -> { val u = data?.data; if (resultCode == RESULT_OK && u != null) io.execute { saveWallpaper(u) } }
            else -> { super.onActivityResult(requestCode, resultCode, data) }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) {
            val req = pendingDownload; pendingDownload = null
            if (req != null && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startDownload(req.url, req.ua, req.cd, req.mime)
            else if (req != null) toast("لازم تسمح بصلاحية التخزين عشان التحميل")
        }
    }

    // ═════════════════ بناء الواجهة ═════════════════

    private fun buildUi() {
        @Suppress("DEPRECATION") run { window.statusBarColor = theme.bg; window.navigationBarColor = theme.bg }
        applySystemBars(false)

        root = FrameLayout(this).apply { setBackgroundColor(theme.bg); layoutDirection = View.LAYOUT_DIRECTION_LTR; isFocusableInTouchMode = true }
        column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(column, FrameLayout.LayoutParams(MATCH, MATCH))

        // ── الشريط العلوي (بأسلوب Brave)
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(theme.bg)
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.round(theme.surface2, dp(24).toFloat())
            setPadding(dp(4), 0, dp(2), 0)
        }
        shieldBadge = TextView(this).apply {
            text = "0"; textSize = 11f; setTextColor(theme.sub); gravity = Gravity.CENTER_VERTICAL
            compoundDrawablePadding = dp(3)
            setCompoundDrawablesWithIntrinsicBounds(IconDrawable("shield_check", theme.accent2, dp(20)), null, null, null)
            setPadding(dp(8), 0, dp(6), 0)
            setOnClickListener { showShieldPanel() }
        }
        pill.addView(shieldBadge, LinearLayout.LayoutParams(WRAP, dp(44)))
        lockView = TextView(this).apply { textSize = 12f; compoundDrawablePadding = 0 }
        pill.addView(lockView, LinearLayout.LayoutParams(dp(22), dp(44)))
        addressBar = EditText(this).apply {
            hint = "ابحث أو اكتب عنوان الموقع"
            textSize = 14f; setSingleLine(true)
            setTextColor(theme.text); setHintTextColor(theme.sub)
            background = null
            setPadding(dp(4), 0, dp(4), 0)
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                    cur?.let { loadInTab(it, text.toString()) }
                    hideKeyboard(); true
                } else false
            }
            setOnFocusChangeListener { _, has ->
                if (has) {
                    val t = cur
                    setText(if (t != null && !isInternal(t.url) && !t.url.startsWith("file:")) t.url else "")
                    selectAll()
                } else hideSuggest()
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { if (hasFocus()) showSuggest(s?.toString().orEmpty()) }
            })
        }
        pill.addView(addressBar, LinearLayout.LayoutParams(0, dp(44), 1f))
        starBtn = TextView(this)
        reloadBtn = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER
            setOnClickListener { cur?.let { if (it.progress in 1..99) it.web.stopLoading() else it.web.reload() } }
        }
        setReloadState(false)
        pill.addView(reloadBtn, LinearLayout.LayoutParams(dp(42), dp(44)))
        topBar.addView(pill, LinearLayout.LayoutParams(0, dp(44), 1f))
        vidBadge = TextView(this).apply {
            textSize = 11f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; visibility = View.GONE
            setPadding(dp(8), 0, dp(8), 0)
            background = Ui.round(theme.accent, dp(14).toFloat())
            setOnClickListener { showVideos() }
        }
        topBar.addView(vidBadge, LinearLayout.LayoutParams(WRAP, dp(28)).apply { marginStart = dp(6) })
        column.addView(topBar, LinearLayout.LayoutParams(MATCH, WRAP))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(theme.accent2)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(theme.bg)
            visibility = View.GONE
        }
        column.addView(progress, LinearLayout.LayoutParams(MATCH, dp(3)))

        webHolder = FrameLayout(this).apply { setBackgroundColor(theme.bg) }
        column.addView(webHolder, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // اقتراحات العنوان (فوق الويب)
        suggestBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            background = Ui.round(theme.surface, dp(14).toFloat(), theme.accent.withAlpha(90), dp(1))
            elevation = dp(8).toFloat()
        }
        webHolder.addView(suggestBox, FrameLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(8), dp(4), dp(8), 0) })

        // حواف الإيماءات (رجوع/تقدّم بالسحب من الحافة)
        webHolder.addView(edgeView(true), FrameLayout.LayoutParams(dp(16), MATCH, Gravity.START))
        webHolder.addView(edgeView(false), FrameLayout.LayoutParams(dp(16), MATCH, Gravity.END))

        // شريط بحث داخل الصفحة
        findBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4)); setBackgroundColor(theme.surface); visibility = View.GONE
        }
        findInput = EditText(this).apply {
            hint = "بحث في الصفحة"; textSize = 14f; setSingleLine(true)
            setTextColor(theme.text); setHintTextColor(theme.sub)
            background = Ui.round(theme.surface2, dp(16).toFloat()); setPadding(dp(12), 0, dp(12), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val q = s?.toString().orEmpty()
                    if (q.isEmpty()) { cur?.web?.clearMatches(); findCount.text = "" } else cur?.web?.findAllAsync(q)
                }
            })
        }
        findCount = TextView(this).apply { textSize = 12f; setTextColor(theme.sub); setPadding(dp(8), 0, dp(8), 0) }
        findBar.addView(findInput, LinearLayout.LayoutParams(0, dp(34), 1f))
        findBar.addView(findCount, LinearLayout.LayoutParams(WRAP, WRAP))
        findBar.addView(glowBtn("▲", 15f) { cur?.web?.findNext(false) }, LinearLayout.LayoutParams(dp(34), dp(34)))
        findBar.addView(glowBtn("▼", 15f) { cur?.web?.findNext(true) }, LinearLayout.LayoutParams(dp(34), dp(34)))
        findBar.addView(glowBtn("✕", 15f) { closeFind() }, LinearLayout.LayoutParams(dp(34), dp(34)))
        column.addView(findBar, LinearLayout.LayoutParams(MATCH, WRAP))

        // شريط قرار التجسس
        spyBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(8), dp(6)); visibility = View.GONE
            background = Ui.grad(Color.rgb(60, 20, 10), Color.rgb(30, 10, 8), 0f)
        }
        spyText = TextView(this).apply { textSize = 12f; setTextColor(Color.WHITE) }
        spyBar.addView(spyText, LinearLayout.LayoutParams(0, WRAP, 1f))
        column.addView(spyBar, LinearLayout.LayoutParams(MATCH, WRAP))

        // ── الشريط السفلي (أسلوب GX: رجوع، تقدّم، زر GX المركزي، التبويبات، القائمة)
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setBackgroundColor(theme.surface)
        }
        val defs = listOf("back", "forward", "bolt", "tabs", "dots")
        for (i in 0 until 5) {
            val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER }
            if (i == 2) {
                iv.setImageDrawable(IconDrawable("bolt", Color.WHITE, dp(24)))
                iv.background = Ui.round(theme.accent, dp(23).toFloat(), theme.accent2, dp(1))
                iv.elevation = dp(6).toFloat()
                val wrap = FrameLayout(this)
                wrap.addView(iv, FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER))
                slotViews.add(wrap); bottomBar.addView(wrap, LinearLayout.LayoutParams(0, dp(58), 1f))
            } else {
                iv.setImageDrawable(IconDrawable(defs[i], theme.text, dp(26)).also { if (i == 3) tabsIcon = it })
                slotViews.add(iv); bottomBar.addView(iv, LinearLayout.LayoutParams(0, dp(58), 1f))
            }
        }
        slotViews[0].setOnClickListener { Fx.tap(it); cur?.let { t -> if (t.web.canGoBack()) t.web.goBack() } }
        slotViews[1].setOnClickListener { Fx.tap(it); cur?.let { t -> if (t.web.canGoForward()) t.web.goForward() } }
        slotViews[2].setOnClickListener { Fx.tap(it, "open"); showGxControl() }
        slotViews[2].setOnLongClickListener {
            Fx.tap(it); addressBar.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(addressBar, 0); true
        }
        slotViews[3].setOnClickListener { Fx.tap(it); showTabs() }
        slotViews[4].setOnClickListener { Fx.tap(it, "open"); showMenu() }
        column.addView(bottomBar, LinearLayout.LayoutParams(MATCH, WRAP))

        // HUD للعبة (Ping/FPS)
        hud = TextView(this).apply {
            textSize = 11f; setTextColor(Color.rgb(120, 255, 170)); visibility = View.GONE
            setPadding(dp(8), dp(3), dp(8), dp(3)); setShadowLayer(6f, 0f, 0f, Color.BLACK)
            background = Ui.round(Color.argb(140, 0, 0, 0), dp(10).toFloat())
        }
        root.addView(hud, FrameLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.TOP or Gravity.START; setMargins(dp(8), dp(28), 0, 0) })

        setContentView(root)
        buildFab()
        updateBottomBar()
    }

    private fun glowBtn(label: String, size: Float, onClick: () -> Unit) = TextView(this).apply {
        text = label; textSize = size; gravity = Gravity.CENTER
        setTextColor(theme.sub)
        setOnClickListener { onClick() }
    }

    private fun showSplash() {
        val sp = FrameLayout(this).apply { setBackgroundColor(theme.bg); isClickable = true }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val logo = ImageView(this)
        try { logo.setImageBitmap(BitmapFactory.decodeStream(assets.open("logo.png"))) } catch (_: Exception) {}
        col.addView(logo, LinearLayout.LayoutParams(dp(84), dp(84)))
        col.addView(TextView(this).apply { text = "MaTrIX GX"; textSize = 19f; setTextColor(theme.text); gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0) })
        sp.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(sp, FrameLayout.LayoutParams(MATCH, MATCH))
        h.postDelayed({ sp.animate().alpha(0f).setDuration(250).withEndAction { root.removeView(sp) }.start() }, 600)
    }

    @Suppress("DEPRECATION")
    private fun applySystemBars(immersive: Boolean) {
        var f = View.SYSTEM_UI_FLAG_VISIBLE
        if (immersive) {
            f = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        } else if (theme.light) {
            f = f or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= 26) f = f or 0x10
        }
        window.decorView.systemUiVisibility = f
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(addressBar.windowToken, 0)
        addressBar.clearFocus(); root.requestFocus()
    }

    private fun isInternal(url: String) = url.isEmpty() || url.startsWith("https://" + Pages.HOST)

    // ═════════════════ شريط التنقل السفلي ═════════════════

    private fun updateBottomBar() {
        tabsIcon?.let { it.label = tabs.size.toString(); it.invalidateSelf() }
        if (slotViews.size >= 5) {
            val t = cur
            slotViews[0].alpha = if (t != null && t.web.canGoBack()) 1f else 0.35f
            slotViews[1].alpha = if (t != null && t.web.canGoForward()) 1f else 0.35f
            slotViews[3].invalidate()
        }
    }

    private fun lockText(t: Tab): Pair<String, Int> = when {
        isInternal(t.url) -> "" to theme.sub
        t.url.startsWith("file:") -> "info" to theme.sub
        t.url.startsWith("https") && !t.sslBroken -> "lock" to Color.rgb(90, 200, 130)
        t.url.startsWith("https") -> "lock_open" to Color.rgb(240, 170, 50)
        else -> "lock_open" to Color.rgb(235, 90, 80)
    }

    private fun setLockIcon(type: String, color: Int) {
        lockView.setCompoundDrawablesWithIntrinsicBounds(if (type.isEmpty()) null else IconDrawable(type, color, dp(16)), null, null, null)
        lockView.setPadding(dp(4), 0, 0, 0)
    }

    private fun setReloadState(loading: Boolean) {
        reloadBtn.setImageDrawable(IconDrawable(if (loading) "close" else "reload", theme.sub, dp(20)))
    }

    private fun refreshChrome() {
        val t = cur ?: return
        if (!addressBar.hasFocus()) {
            addressBar.setText(when {
                isInternal(t.url) -> ""
                t.url.startsWith("file:") -> t.title
                else -> t.url
            })
        }
        val (lk, col) = lockText(t)
        setLockIcon(lk, col)
        val loading = t.progress in 1..99
        progress.progress = t.progress
        progress.visibility = if (loading && !gameMode) View.VISIBLE else View.GONE
        starBtn.text = if (t.bookmarked) "★" else "☆"
        starBtn.setTextColor(if (t.bookmarked) theme.accent2 else theme.sub)
        setReloadState(loading)
        shieldBadge.text = t.blocked.get().toString()
        shieldBadge.alpha = if (!AdBlocker.enabled() || AdBlocker.isSiteAllowed(t.host)) 0.45f else 1f
        updateBottomBar()
    }

    // ═════════════════ التبويبات ═════════════════

    private fun newTab(url: String?, priv: Boolean = false, popup: Boolean = false, select: Boolean = true): Tab {
        val web = WebView(this)
        val tab = Tab(web, priv, popup)
        configure(tab)
        tabs.add(tab)
        webHolder.addView(web, 0, FrameLayout.LayoutParams(MATCH, MATCH))
        web.visibility = View.GONE
        if (select) selectTab(tab)
        if (url != null) { if (select) loadInTab(tab, url) else tab.pending = url }
        updateBottomBar()
        if (started && select) Fx.play("open")
        return tab
    }

    private fun captureThumb(t: Tab) {
        try {
            val w = t.web.width; val hh = t.web.height
            if (w <= 0 || hh <= 0) return
            val bmp = Bitmap.createBitmap((w * 0.4f).toInt(), (hh * 0.4f).toInt(), Bitmap.Config.RGB_565)
            val c = Canvas(bmp); c.scale(0.4f, 0.4f)
            t.web.draw(c)
            t.thumb = bmp
        } catch (_: Throwable) {}
    }

    private fun selectTab(tab: Tab) {
        cur?.let { if (it !== tab) { captureThumb(it); it.lastActive = System.currentTimeMillis(); it.web.visibility = View.GONE; it.web.onPause() } }
        cur = tab
        tab.lastActive = System.currentTimeMillis()
        tab.web.visibility = View.VISIBLE
        tab.web.onResume()
        applyRendererPriority()
        val p = tab.pending
        if (p != null) { tab.pending = null; tab.sleeping = false; loadInTab(tab, p) }
        hideFabMenu(); hideSuggest()
        refreshChrome()
    }

    private fun applyRendererPriority() {
        if (Build.VERSION.SDK_INT < 26) return
        for (t in tabs) {
            try {
                t.web.setRendererPriorityPolicy(if (t === cur) WebView.RENDERER_PRIORITY_IMPORTANT else WebView.RENDERER_PRIORITY_WAIVED, false)
            } catch (_: Throwable) {}
        }
    }

    private fun closeTab(tab: Tab) {
        val idx = tabs.indexOf(tab)
        if (idx < 0) return
        tabs.removeAt(idx)
        webHolder.removeView(tab.web)
        try { tab.web.stopLoading(); tab.web.destroy() } catch (_: Exception) {}
        tab.thumb = null
        if (started) Fx.play("close")
        if (tabs.isEmpty()) { cur = null; newTab(Pages.HOME_URL) }
        else if (cur === tab) {
            cur = null
            val op = tab.opener
            selectTab(if (op != null && tabs.contains(op)) op else tabs[(idx - 1).coerceAtLeast(0)])
        }
        updateBottomBar()
    }

    private fun closeSoon(tab: Tab) { h.post { if (tabs.contains(tab)) closeTab(tab) } }
    private fun reloadIfAlive(tab: Tab) { if (tabs.contains(tab)) tab.web.reload() }

    private fun saveSession() {
        try {
            val arr = JSONArray(); var curIdx = 0
            for (t in tabs) {
                if (t.priv) continue
                val u = t.pending ?: (if (t.url.isNotEmpty()) t.url else Pages.HOME_URL)
                if (u.startsWith("file:")) continue
                arr.put(u); if (t === cur) curIdx = arr.length() - 1
            }
            Cfg.prefs.edit().putString("session", arr.toString()).putInt("session_cur", curIdx).apply()
        } catch (_: Exception) {}
    }

    private fun restoreSession(): Boolean {
        val s = Cfg.str("session", "")
        if (s.isEmpty()) return false
        return try {
            val arr = JSONArray(s)
            if (arr.length() == 0) return false
            val curIdx = Cfg.int("session_cur", 0).coerceIn(0, arr.length() - 1)
            for (i in 0 until minOf(arr.length(), 10)) newTab(arr.getString(i), select = (i == curIdx))
            true
        } catch (_: Exception) { false }
    }

    private fun engineName() = Cfg.str(Cfg.ENGINE, "Google")
    private fun engineUrl() = when (engineName()) {
        "DuckDuckGo" -> "https://duckduckgo.com/?q="
        "Bing" -> "https://www.bing.com/search?q="
        else -> "https://www.google.com/search?q="
    }

    private fun resolve(input: String): String {
        val v = input.trim()
        return when {
            v.startsWith("https://", true) || v.startsWith("http://", true) -> v
            v.contains(" ") || (!v.contains(".") && !v.contains(":") && !v.startsWith("localhost")) -> engineUrl() + Uri.encode(v)
            else -> "http://$v".let { if (Cfg.bool(Cfg.HTTPS_ONLY, true) && !UrlUtil.isIp(v.substringBefore('/').substringBefore(':'))) "https://$v" else it }
        }
    }

    private fun loadInTab(tab: Tab, input: String) {
        if (input.isBlank()) return
        if (IronGuard.blockedTypedScheme(input)) { toast("🚫 البروتوكول ده محظور"); return }
        val target = smartUrl(tab, resolve(input))
        IronGuard.scoreUrl(this, target, 0)?.let { showRiskBlocked(tab, hostOf(target), target, it); return }
        AdultGuard.checkUrl(target)?.let { showBlocked(tab, hostOf(target), it); return }
        tab.web.loadUrl(target)
    }

    private fun toggleBookmark(tab: Tab) {
        val u = tab.url
        if (isInternal(u) || u.startsWith("file:")) { toast("مش ممكن تضيف الصفحة دي للمفضلة"); return }
        if (tab.bookmarked) { tab.bookmarked = false; io.execute { db.removeBookmark(u) }; toast("اتشالت من المفضلة") }
        else { tab.bookmarked = true; val title = tab.title; io.execute { db.addBookmark(u, title) }; toast("⭐ اتضافت للمفضلة") }
        refreshChrome()
    }

    // اقتراحات العنوان
    private fun showSuggest(q: String) {
        if (q.length < 2 || q.startsWith("http") && q.length < 8) { hideSuggest(); return }
        io.execute {
            val list = db.suggest(q, 6)
            h.post {
                if (!addressBar.hasFocus()) return@post
                suggestBox.removeAllViews()
                if (list.isEmpty()) { suggestBox.visibility = View.GONE; return@post }
                for ((u, ti) in list) {
                    val r = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(8))
                        setOnClickListener { hideSuggest(); hideKeyboard(); cur?.let { loadInTab(it, u) } }
                    }
                    r.addView(TextView(this).apply { text = ti.ifBlank { u }; textSize = 13f; setTextColor(theme.text); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                    r.addView(TextView(this).apply { text = u; textSize = 10f; setTextColor(theme.sub); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                    suggestBox.addView(r)
                }
                suggestBox.visibility = View.VISIBLE
            }
        }
    }

    private fun hideSuggest() { if (::suggestBox.isInitialized) { suggestBox.visibility = View.GONE; suggestBox.removeAllViews() } }

    // إيماءات الحواف
    private fun edgeView(left: Boolean): View = View(this).apply {
        var sx = 0f
        setOnTouchListener { _, e ->
            if (!Cfg.bool(Cfg.GESTURES, true) || gameMode) return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; true }
                MotionEvent.ACTION_UP -> {
                    val dx = e.rawX - sx
                    if (left && dx > dp(70)) cur?.let { if (it.web.canGoBack()) it.web.goBack() }
                    else if (!left && dx < -dp(70)) cur?.let { if (it.web.canGoForward()) it.web.goForward() }
                    true
                }
                else -> true
            }
        }
    }

    // ═════════════════ إعداد الـ WebView ═════════════════

    private fun cleanUa(ua: String) = ua.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+\\s"), "")

    @Suppress("DEPRECATION")
    private fun configure(tab: Tab) {
        val w = tab.web
        val s = w.settings
        if (baseUa.isEmpty()) baseUa = cleanUa(s.userAgentString)
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setSupportMultipleWindows(true)
        s.allowFileAccess = true
        s.allowContentAccess = false
        s.allowFileAccessFromFileURLs = false
        s.allowUniversalAccessFromFileURLs = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mixedContentMode = if (Cfg.bool(Cfg.MIXED, true)) WebSettings.MIXED_CONTENT_NEVER_ALLOW else WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.userAgentString = baseUa
        s.loadsImagesAutomatically = !Cfg.bool(Cfg.DATASAVER, false)
        if (Build.VERSION.SDK_INT >= 26) s.safeBrowsingEnabled = Cfg.bool(Cfg.SAFEB, true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, Cfg.bool(Cfg.TPCOOK, false))
        w.setBackgroundColor(theme.bg)
        w.overScrollMode = View.OVER_SCROLL_NEVER
        w.addJavascriptInterface(ShieldBridge(tab), "__mxb")
        w.webViewClient = makeClient(tab)
        w.webChromeClient = makeChrome(tab)
        w.setDownloadListener { url, ua, cd, mime, _ -> startDownload(url, ua, cd, mime) }
        w.setOnLongClickListener { onLongPress(tab) }
        w.setFindListener { active, total, done -> if (done) findCount.text = if (total == 0) "0" else "${active + 1}/$total" }
    }

    /** جسر صغير: الصفحة تسأل هل الرابط محجوب؟ (يرجع true/false فقط) */
    private inner class ShieldBridge(val tab: Tab) {
        @JavascriptInterface
        fun blocked(url: String?): Boolean {
            if (url.isNullOrEmpty()) return false
            return try {
                val uri = Uri.parse(url)
                val cat = AdBlocker.check(uri, tab.host, false)
                if (cat != 0) { tab.blocked.incrementAndGet(); AdBlocker.noteBlocked(uri.host, cat); true } else false
            } catch (_: Exception) { false }
        }
    }

    private fun offlineDir() = File(filesDir, "offline")
    private fun isOfflineFile(uri: Uri): Boolean = try { File(uri.path ?: "").canonicalPath.startsWith(offlineDir().canonicalPath) } catch (_: Exception) { false }

    private fun blockedResponse() = WebResourceResponse("text/plain", "utf-8", 403, "Blocked", mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(ByteArray(0)))

    private fun adultBlockedResponse(host: String): WebResourceResponse {
        val html = AdultGuard.blockHtml(host, AdultGuard.Verdict(AdultGuard.ADULT, "الموقع في قوائم الحجب", false))
        return WebResourceResponse("text/html", "utf-8", 200, "OK", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
    }

    private fun showBlocked(tab: Tab, host: String, v: AdultGuard.Verdict) {
        tab.web.stopLoading()
        tab.web.loadDataWithBaseURL("https://mx.blocked/", AdultGuard.blockHtml(host, v), "text/html", "utf-8", null)
        AdBlocker.noteBlocked(host, Cat.ADS)
    }

    /** تحليل نص الصفحة بعد التحميل (عنوان + وصف + عناوين + نص) */
    private fun scanAdult(tab: Tab) {
        if (!tabs.contains(tab)) return
        val host = tab.host
        if (host.isEmpty() || host == Pages.HOST || host == "mx.blocked" || isInternal(tab.url) || !AdultGuard.active()) return
        if (AdBlocker.isSiteAllowed(host) || AdultGuard.isSearchHost(host)) return
        tab.web.evaluateJavascript(AdultGuard.PROBE) { raw ->
            val v = AdultGuard.evaluate(host, tab.url, raw) ?: return@evaluateJavascript
            if (!tabs.contains(tab) || tab.host != host) return@evaluateJavascript
            if (v.learn && v.kind == AdultGuard.ADULT) FilterEngine.learnAdult(this, host)
            showBlocked(tab, host, v)
        }
    }

    private fun showRiskBlocked(tab: Tab, host: String, url: String, v: IronGuard.Verdict) {
        tab.web.stopLoading()
        tab.web.loadDataWithBaseURL("https://mx.blocked/", IronGuard.blockHtml(host, v), "text/html", "utf-8", null)
        IronGuard.log(this, host, url, v)
        AdBlocker.noteBlocked(host, Cat.ADS)
    }

    /** تحليل محتوى الصفحة بعد التحميل (إطارات مخفية، سكريبت مشفّر، نموذج باسورد منتحل) */
    private fun scanRisk(tab: Tab) {
        if (!tabs.contains(tab) || !IronGuard.enabled()) return
        val host = tab.host
        if (host.isEmpty() || host == Pages.HOST || host == "mx.blocked" || isInternal(tab.url)) return
        tab.web.evaluateJavascript(IronGuard.PROBE) { raw ->
            val v = IronGuard.evaluate(this, host, tab.url, raw) ?: return@evaluateJavascript
            if (!tabs.contains(tab) || tab.host != host) return@evaluateJavascript
            showRiskBlocked(tab, host, tab.url, v)
        }
    }

    /** مسح كوكيز وتخزين موقع معيّن */
    internal fun clearSiteData(host: String) {
        if (host.isEmpty()) return
        try {
            val cm = CookieManager.getInstance()
            for (base in listOf("https://$host", "https://www.$host")) {
                val raw = cm.getCookie(base) ?: continue
                for (c in raw.split(';')) {
                    val name = c.substringBefore('=').trim()
                    if (name.isNotEmpty()) cm.setCookie(base, "$name=; Max-Age=0; Path=/")
                }
            }
            cm.flush()
            android.webkit.WebStorage.getInstance().deleteOrigin("https://$host")
        } catch (_: Exception) {}
    }

    // ───────── المتصفح الافتراضي ─────────
    internal fun isDefaultBrowser(): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(android.app.role.RoleManager::class.java)
            return rm != null && rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)
        }
        val r = packageManager.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com")), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return r != null && packageName == r.activityInfo.packageName
    }

    internal fun askDefaultBrowser() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(android.app.role.RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)) {
                try { startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER), 4123); return } catch (_: Exception) {}
            }
        }
        try { startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) } catch (_: Exception) { toast("افتح إعدادات أندرويد ← التطبيقات الافتراضية") }
    }

    /** اقتراح مرة عند أول تشغيل، ولو اترفض يرجع بحد أقصى مرتين بعد أيام */
    private fun maybeAskDefaultBrowser() {
        if (isDefaultBrowser() || Cfg.bool("db_never", false)) return
        val n = Cfg.int("db_n", 0)
        val last = Cfg.str("db_last", "0").toLongOrNull() ?: 0L
        if (n >= 3 || (n > 0 && System.currentTimeMillis() - last < 3 * 86400_000L)) return
        Cfg.setInt("db_n", n + 1); Cfg.setStr("db_last", System.currentTimeMillis().toString())
        ui.sheet { box, d ->
            box.addView(ui.titleView("اجعل MaTrIX Co متصفحك الافتراضي؟"))
            box.addView(TextView(this).apply { text = "الروابط من أي تطبيق هتفتح هنا بالحماية والجدار الحديدي."; textSize = 13f; setTextColor(theme.sub); setPadding(0, dp(4), 0, dp(12)) })
            box.addView(ui.button("اجعله الافتراضي") { d.dismiss(); askDefaultBrowser() })
            box.addView(ui.button("لاحقًا", false) { d.dismiss() })
            box.addView(ui.button("متسألنيش تاني", false) { Cfg.setBool("db_never", true); d.dismiss() })
        }
    }

    private fun isGameHost(host: String) = GAME_HOSTS.any { host == it || host.endsWith(".$it") }
    private fun hostOf(u: String) = try { Uri.parse(u).host.orEmpty().lowercase() } catch (_: Exception) { "" }

    private val videoRegex = Regex("\\.(mp4|webm|m4v|mov|mp3|m4a|m3u8|mpd|ogg)(\\?|$)", RegexOption.IGNORE_CASE)

    private fun addVideo(tab: Tab, url: String) {
        val hh = hostOf(url)
        if (hh.contains("googlevideo.com") || hh.contains("youtube") || hh.contains("ytimg")) return
        synchronized(tab.videos) { if (tab.videos.size < 25) tab.videos.add(url) }
    }

    private fun inject(tab: Tab) {
        val u = tab.url
        if (u.isEmpty() || isInternal(u) || u.startsWith("file:")) return
        val host = tab.host
        val allowedSite = AdBlocker.isSiteAllowed(host)
        val on = AdBlocker.enabled() && !allowedSite
        val yt = host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com")
        val spy = on && AdBlocker.spyOn() && !isGameHost(host)
        val pol = if (spy) db.blockedTypesFor(host) else emptyList()
        tab.web.evaluateJavascript(
            Scripts.main(host, tab.skipMode, Cfg.bool(Cfg.DARK, false), yt, on && AdBlocker.adsOn(), on && AdBlocker.annoyOn(), spy, Cfg.bool(Cfg.FP, true), Cfg.bool(Cfg.BGAUDIO, false), pol), null
        )
        if (tab.desktop) tab.web.evaluateJavascript(Scripts.desktopViewport(desktopWidth()), null)
        if (on) {
            io.execute {
                try {
                    val sels = FilterEngine.cosmeticFor(this, host)
                    if (sels.isNotEmpty()) h.post { if (tabs.contains(tab) && tab.host == host) tab.web.evaluateJavascript(Scripts.cosmetic(sels), null) }
                } catch (_: Throwable) {}
            }
        }
    }

    private fun injectUserScripts(tab: Tab) {
        val u = tab.url
        if (u.isEmpty() || isInternal(u) || !u.startsWith("http")) return
        for (s in db.scriptsFor(tab.host)) tab.web.evaluateJavascript(Scripts.user(s.id, s.code, s.type), null)
    }

    private fun applyHostSettings(tab: Tab) {
        val flags = db.getLegacy(tab.host)
        tab.web.settings.mixedContentMode =
            if ((flags and 4) != 0 || !Cfg.bool(Cfg.MIXED, true)) WebSettings.MIXED_CONTENT_ALWAYS_ALLOW else WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }

    private fun smartUrl(tab: Tab, url: String): String {
        if (url.startsWith("http://", true)) {
            val hh = hostOf(url)
            val port = try { Uri.parse(url).port } catch (_: Exception) { -1 }
            val legacy = (db.getLegacy(hh) and 1) != 0
            if (Cfg.bool(Cfg.HTTPS_ONLY, true) && !legacy && port == -1 && hh != "localhost" && !UrlUtil.isIp(hh) && !hh.endsWith(".local") && !hh.endsWith(".lan")) {
                tab.upgradedFrom = url
                return FilterEngine.safeSearch("https://" + url.substring(7))
            }
        }
        return FilterEngine.safeSearch(url)
    }

    private val sslOnce = HashSet<String>()
    private var sslDialogOpen = false

    private fun makeClient(tab: Tab) = object : WebViewClient() {

        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            val req = request ?: return null
            val uri = req.url
            val scheme = uri.scheme ?: return null
            if (scheme == "file") return if (isOfflineFile(uri)) null else blockedResponse()
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host ?: return null
            if (host == Pages.HOST) return Pages.respond(this@MainActivity, db, theme, uri)

            if (req.isForMainFrame) {
                if (AdBlocker.isMaliciousPage(host)) {
                    io.execute { db.addSpyLog(host, host, "known_spy", 97, "صفحة خبيثة — تحذير") }
                    return Pages.respond(this@MainActivity, db, theme, Uri.parse("https://mx.home/warn?u=" + Uri.encode(uri.toString())))
                }
                if (FilterEngine.isAdult(this@MainActivity, host)) { AdBlocker.noteBlocked(host, Cat.ADS); return adultBlockedResponse(host) }
                if (tab.popup && AdBlocker.isBlockedHost(host)) { AdBlocker.noteBlocked(host); closeSoon(tab); return blockedResponse() }
                return null
            }

            val cat = AdBlocker.check(uri, tab.host, false)
            if (cat != 0) {
                tab.blocked.incrementAndGet()
                AdBlocker.noteBlocked(host, cat)
                if (cat == Cat.SPY) io.execute { db.addSpyLog(host, tab.host, "known_spy", 95, "حُجب تلقائيًا") }
                return blockedResponse()
            }
            if (videoRegex.containsMatchIn(uri.path.orEmpty() + "?")) addVideo(tab, uri.toString())
            return null
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            val u = url.orEmpty()
            if (tab.sleeping && u == "about:blank") return
            tab.url = u; tab.host = hostOf(u); tab.blocked.set(0); tab.progress = 5
            tab.sslBroken = false; tab.bookmarked = false
            synchronized(tab.videos) { tab.videos.clear() }
            tab.spyFindings.clear()
            tab.skipMode = if (isGameHost(tab.host)) 0 else (db.getSkipMode(tab.host) ?: 1)
            applyHostSettings(tab)
            if (tab === cur) {
                hideFabMenu(); hideSpyBar()
                if (isGameHost(tab.host)) { if (!gameMode) setGameMode(true, true) }
                else if (gameMode && autoGame) setGameMode(false, false)
                refreshChrome()
            }
            inject(tab)
        }

        override fun onPageCommitVisible(view: WebView?, url: String?) { if (tab.sleeping) return; inject(tab); injectUserScripts(tab) }

        override fun onPageFinished(view: WebView?, url: String?) {
            val u = url.orEmpty()
            if (tab.sleeping && u == "about:blank") { tab.progress = 100; return }
            tab.url = u; tab.progress = 100
            inject(tab); injectUserScripts(tab)
            h.postDelayed({ if (tabs.contains(tab) && tab === cur) captureThumb(tab) }, 900)
            scanAdult(tab); h.postDelayed({ scanAdult(tab) }, 1800)
            tab.redirects = 0; scanRisk(tab); h.postDelayed({ scanRisk(tab) }, 2500)
            if (gameMode && Cfg.bool(Cfg.FPS, true)) tab.web.evaluateJavascript(Scripts.fps(true), null)
            val title = tab.title
            if (!tab.priv && !isInternal(u) && u.startsWith("http")) {
                io.execute {
                    db.addHistory(u, title)
                    val bm = db.isBookmarked(u)
                    h.post { if (tab.url == u) { tab.bookmarked = bm; if (tab === cur) refreshChrome() } }
                }
            }
            if (tab === cur) refreshChrome()
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) { if (tab === cur) refreshChrome() }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val req = request ?: return false
            val uri = req.url
            when (uri.scheme.orEmpty().lowercase()) {
                "mx" -> { handleMx(tab, uri); return true }
                "http" -> {
                    val smart = smartUrl(tab, uri.toString())
                    if (smart != uri.toString()) { view?.loadUrl(smart); return true }
                    return false
                }
                "https" -> {
                    val host = uri.host.orEmpty()
                    if (host != Pages.HOST && req.isForMainFrame && !AdBlocker.isSiteAllowed(tab.host) && AdBlocker.enabled()) {
                        val cat = AdBlocker.categoryOf(host)
                        if (cat != 0 && cat != Cat.MAL && cat != Cat.SOC) {
                            tab.blocked.incrementAndGet(); AdBlocker.noteBlocked(host, cat)
                            h.post { toast("🛡 تم حجب تحويل إعلاني") }
                            return true
                        }
                    }
                    if (req.isForMainFrame) {
                        if (Build.VERSION.SDK_INT >= 24 && req.isRedirect) tab.redirects++ else tab.redirects = 0
                        IronGuard.scoreUrl(this@MainActivity, uri.toString(), tab.redirects)?.let { showRiskBlocked(tab, host, uri.toString(), it); return true }
                        AdultGuard.checkUrl(uri.toString())?.let { showBlocked(tab, host, it); return true }
                        val safe = FilterEngine.safeSearch(uri.toString())
                        if (safe != uri.toString()) { view?.loadUrl(safe); return true }
                        val cleaned = UrlUtil.stripTracking(uri.toString())
                        if (cleaned != uri.toString()) { view?.loadUrl(cleaned); return true }
                    }
                    return false
                }
                "file" -> return !isOfflineFile(uri)
                "about", "data", "blob" -> return false
                else -> {
                    if (IronGuard.externalSchemeAllowed(uri.scheme)) {
                        if (req.hasGesture()) {
                            try {
                                val i = Intent(Intent.ACTION_VIEW, uri)
                                i.addCategory(Intent.CATEGORY_BROWSABLE); i.component = null; i.selector = null
                                startActivity(i)
                            } catch (_: Exception) { toast("مفيش تطبيق يفتح الرابط ده") }
                        }
                    } else if (IronGuard.enabled()) {
                        IronGuard.log(this@MainActivity, uri.host.orEmpty(), uri.toString(), IronGuard.Verdict(100, "محاولة فتح تطبيق خارجي (" + (uri.scheme ?: "?") + ")"))
                        if (req.hasGesture()) toast("🛡️ اتمنع رابط تطبيق خارجي")
                    } else if (req.hasGesture()) {
                        try {
                            val i = if (uri.scheme == "intent") Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, uri)
                            i.addCategory(Intent.CATEGORY_BROWSABLE); i.component = null; i.selector = null
                            startActivity(i)
                        } catch (_: Exception) { toast("مفيش تطبيق يفتح الرابط ده") }
                    }
                    return true
                }
            }
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            val req = request ?: return
            if (!req.isForMainFrame || req.url.host == Pages.HOST) return
            val failing = req.url.toString()
            val fhost = req.url.host.orEmpty().lowercase()
            val up = tab.upgradedFrom
            if (up != null && failing.startsWith("https://") && hostOf(up) == fhost) {
                tab.upgradedFrom = null
                db.setLegacy(fhost, db.getLegacy(fhost) or 1)
                view?.loadUrl(up)
                toast("⚠ الموقع لا يدعم HTTPS — فُتح بدون تشفير")
                return
            }
            val saved = db.getOfflineByUrl(failing)
            if (saved != null && File(saved.path).exists()) {
                view?.loadUrl("file://" + saved.path); toast("📴 مفيش اتصال — اتفتحت النسخة المحفوظة"); return
            }
            view?.loadUrl(Pages.HOME_URL + "error?u=" + Uri.encode(failing) + "&m=" + Uri.encode(error?.description?.toString().orEmpty()))
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            val hnd = handler ?: return
            val err = error ?: run { hnd.cancel(); return }
            val host = hostOf(err.url)
            if (IronGuard.enabled()) {
                hnd.cancel()
                IronGuard.log(this@MainActivity, host, err.url ?: "", IronGuard.Verdict(100, "شهادة الأمان غير صالحة أو منتهية"))
                if (hostOf(view?.url.orEmpty()) == host || hostOf(tab.url) == host || tab.pending != null) h.post { showRiskBlocked(tab, host, err.url ?: "", IronGuard.Verdict(100, "شهادة الأمان غير صالحة أو منتهية")) }
                return
            }
            val flags = db.getLegacy(host)
            if ((flags and 2) != 0 || sslOnce.contains(host)) { tab.sslBroken = true; hnd.proceed(); h.post { refreshChrome() }; return }
            val isMain = hostOf(view?.url.orEmpty()) == host
            hnd.cancel()
            if (!isMain || AdBlocker.isMaliciousPage(host)) return
            h.post { askSsl(tab, host, err) }
        }

        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
            toast("الصفحة وقفت بسبب نقص الذاكرة — اتقفل التبويب")
            closeSoon(tab)
            return true
        }
    }

    private fun askSsl(tab: Tab, host: String, err: SslError) {
        if (sslDialogOpen || !tabs.contains(tab)) return
        sslDialogOpen = true
        val why = when (err.primaryError) {
            SslError.SSL_EXPIRED -> "الشهادة منتهية الصلاحية"
            SslError.SSL_IDMISMATCH -> "الشهادة لا تخص هذا الموقع"
            SslError.SSL_UNTRUSTED -> "شهادة ذاتية أو من جهة غير موثوقة (شائع في الراوترات)"
            SslError.SSL_NOTYETVALID -> "الشهادة لم يبدأ سريانها بعد (تاريخ الجهاز؟)"
            SslError.SSL_DATE_INVALID -> "تاريخ الشهادة غير صحيح"
            else -> "شهادة غير صالحة"
        }
        val d = ui.sheet { box, dlg ->
            box.addView(ui.titleView("⚠ اتصال غير آمن"))
            box.addView(ui.infoView(host + "\n" + why + "\nلو ده راوتر أو جهاز في شبكتك البيتية فغالبًا آمن، لكن لو موقع على الإنترنت ننصح بالرجوع."))
            box.addView(ui.button("⬅ رجوع (الأكثر أمانًا)") { dlg.dismiss(); if (tab.web.canGoBack()) tab.web.goBack() })
            box.addView(ui.space(8))
            box.addView(ui.button("متابعة هذه المرة فقط", false) { dlg.dismiss(); sslOnce.add(host); tab.web.reload() })
            box.addView(ui.space(8))
            box.addView(ui.button("متابعة دائمًا لهذا الموقع (وضع التوافق)", false) {
                dlg.dismiss(); db.setLegacy(host, db.getLegacy(host) or 2 or 1); tab.web.reload()
            })
        }
        d.setOnDismissListener { sslDialogOpen = false }
    }

    private fun makeChrome(tab: Tab) = object : WebChromeClient() {

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            tab.progress = newProgress
            if (newProgress in 20..60) inject(tab)
            if (tab === cur) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99 && !gameMode) View.VISIBLE else View.GONE
                setReloadState(newProgress in 1..99)
                updateBottomBar()
            }
        }

        override fun onReceivedTitle(view: WebView?, title: String?) { tab.title = title.orEmpty() }

        override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
            val hh = tab.host
            if (icon != null && hh.isNotEmpty() && !isInternal(tab.url)) io.execute { Favicons.saveIfMissing(this@MainActivity, hh, icon) }
        }

        override fun onConsoleMessage(m: ConsoleMessage?): Boolean {
            val msg = m?.message().orEmpty()
            when {
                msg == "MX_SKIP_AUTO" -> { io.execute { db.addSkipped() }; h.post { toast("⚡ اتكشف انتظار — جاري التخطي") } }
                msg.startsWith("MX_FPS:") -> { lastFps = msg.substring(7).toIntOrNull() ?: 0; h.post { updateHud() } }
                msg.startsWith("MX_VIDEO:") -> addVideo(tab, msg.substring(9))
                msg.startsWith("MX_SPY:") -> { val j = msg.substring(7); h.post { handleSpy(tab, j) } }
                msg.startsWith("MX_USERERR:") -> h.post { toast("⚠ خطأ في سكريبت: " + msg.substring(11).take(80)) }
                else -> return super.onConsoleMessage(m)
            }
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            request?.deny()
            val host = request?.origin?.host.orEmpty()
            io.execute { db.addSpyLog(host, host, "media", 50, "رُفض تلقائيًا") }
            h.post { toast("🕵 تم رفض طلب كاميرا/مايك من $host") }
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: android.webkit.GeolocationPermissions.Callback?) {
            callback?.invoke(origin, false, false)
        }

        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
            val msg = resultMsg ?: return false
            val now = System.currentTimeMillis()
            if (!isUserGesture || now - tab.lastPopupAt < 1500 || tabs.size >= 14) {
                tab.blocked.incrementAndGet(); AdBlocker.noteBlocked(tab.host); return false
            }
            tab.lastPopupAt = now
            val nt = newTab(null, tab.priv, popup = true, select = true)
            nt.opener = tab
            val transport = msg.obj as WebView.WebViewTransport
            transport.webView = nt.web
            msg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView?) { tabs.firstOrNull { it.web === window }?.let { t -> h.post { closeTab(t) } } }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (view == null || customView != null) { callback?.onCustomViewHidden(); return }
            customView = view; customCb = callback
            val holder = FrameLayout(this@MainActivity).apply { setBackgroundColor(Color.BLACK); addView(view, FrameLayout.LayoutParams(MATCH, MATCH)) }
            customHolder = holder
            root.addView(holder, FrameLayout.LayoutParams(MATCH, MATCH))
            column.visibility = View.GONE; fab.visibility = View.GONE; hud.visibility = View.GONE
            hideFabMenu(); applySystemBars(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        override fun onHideCustomView() { exitCustomView(false) }

        override fun onShowFileChooser(view: WebView?, cb: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
            fileCb?.onReceiveValue(null); fileCb = cb
            return try {
                val i = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).setType("*/*")
                startActivityForResult(i, REQ_FILE); true
            } catch (_: Exception) { fileCb = null; false }
        }
    }

    private fun exitCustomView(notify: Boolean) {
        if (customView == null) return
        customHolder?.let { root.removeView(it); it.removeAllViews() }
        customHolder = null; customView = null
        column.visibility = View.VISIBLE; fab.visibility = if (Cfg.bool(Cfg.SHOWFAB, true)) View.VISIBLE else View.GONE
        applySystemBars(gameMode)
        if (!gameMode) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = if (gameMode) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val cb = customCb; customCb = null
        if (notify) cb?.onCustomViewHidden()
        updateHud()
    }

    // ═════════════════ كاشف التجسس ═════════════════

    private val reloadedFor = HashSet<String>()

    private fun handleSpy(tab: Tab, json: String) {
        if (!AdBlocker.spyOn() || !tabs.contains(tab)) return
        val o = try { JSONObject(json) } catch (_: Exception) { return }
        val type = o.optString("t")
        val page = tab.host
        var src = o.optString("s").lowercase(Locale.ROOT)
        if (src.isEmpty()) src = page
        if (db.getSpyRule(page, type) == "allow") return
        val key = "$type|$src"
        if (tab.spyFindings.containsKey(key)) return
        val conf = SpyGuard.confidence(type, src, page)
        tab.spyFindings[key] = conf
        val label = SpyGuard.label(type)
        val third = SpyGuard.isThirdParty(src, page)
        when {
            conf >= SpyGuard.AUTO -> {
                if (third) AdBlocker.blockHost(src, Cat.SPY) else db.setSpyRule(page, type, "block")
                tab.web.evaluateJavascript("window.__mxBlockType&&window.__mxBlockType('$type')", null)
                io.execute { db.addSpyLog(src, page, type, conf, "حُجب تلقائيًا") }
                toast("🕵 حُجب تلقائيًا ($conf%): $label")
                if (third && reloadedFor.add(page + "|" + src)) tab.web.reload()
            }
            conf >= SpyGuard.ASK -> promptSpy(tab, type, src, conf)
            else -> io.execute { db.addSpyLog(src, page, type, conf, "سُجّل") }
        }
    }

    private fun promptSpy(tab: Tab, type: String, src: String, conf: Int) {
        val now = System.currentTimeMillis()
        val page = tab.host
        if (tab !== cur || now - tab.lastSpyPromptAt < 15000) { io.execute { db.addSpyLog(src, page, type, conf, "سُجّل") }; return }
        tab.lastSpyPromptAt = now
        spyBar.removeAllViews()
        spyText.text = "🕵 " + SpyGuard.label(type) + (if (src != page) " من " + src else "") + " — ثقة " + conf + "%\nتمنعه؟"
        spyBar.addView(spyText, LinearLayout.LayoutParams(0, WRAP, 1f))
        fun smallBtn(t: String, primary: Boolean, a: () -> Unit) = TextView(this).apply {
            text = t; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = if (primary) Ui.grad(theme.accent, theme.accent.darker(0.6f), dp(14).toFloat()) else Ui.round(Color.argb(70, 255, 255, 255), dp(14).toFloat())
            setOnClickListener { a() }
        }
        spyBar.addView(smallBtn("امنع", true) {
            hideSpyBar()
            db.setSpyRule(page, type, "block")
            if (SpyGuard.isThirdParty(src, page)) AdBlocker.blockHost(src, Cat.SPY)
            tab.web.evaluateJavascript("window.__mxBlockType&&window.__mxBlockType('$type')", null)
            io.execute { db.addSpyLog(src, page, type, conf, "مُنع بقرارك") }
            toast("🛡 تم المنع")
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(6) })
        spyBar.addView(smallBtn("اسمح", false) {
            hideSpyBar(); db.setSpyRule(page, type, "allow")
            io.execute { db.addSpyLog(src, page, type, conf, "سُمح بقرارك") }
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(6) })
        spyBar.addView(smallBtn("✕", false) { hideSpyBar(); io.execute { db.addSpyLog(src, page, type, conf, "تجاهل") } }, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(6) })
        spyBar.visibility = View.VISIBLE
        h.postDelayed({ if (spyBar.visibility == View.VISIBLE) spyBar.visibility = View.GONE }, 20000)
    }

    private fun hideSpyBar() { if (::spyBar.isInitialized) spyBar.visibility = View.GONE }

    // ═════════════════ إجراءات الصفحات الداخلية mx:// ═════════════════

    private fun handleMx(tab: Tab, uri: Uri) {
        val id = uri.getQueryParameter("id")?.toLongOrNull()
        when (uri.host) {
            "go" -> uri.getQueryParameter("q")?.let { loadInTab(tab, it) }
            "delhist" -> if (id != null) io.execute { db.deleteHistory(id); h.post { reloadIfAlive(tab) } }
            "clearhist" -> io.execute { db.clearHistory(); h.post { reloadIfAlive(tab) } }
            "delbm" -> uri.getQueryParameter("u")?.let { u -> io.execute { db.removeBookmark(u); h.post { reloadIfAlive(tab) } } }
            "openoff" -> if (id != null) openOffline(tab, id)
            "deloff" -> if (id != null) io.execute { db.deleteOffline(id)?.let { File(it).delete() }; h.post { reloadIfAlive(tab) } }
            "opendl" -> if (id != null) openDownload(id)
            "deldl" -> if (id != null) io.execute { db.deleteDownload(id); h.post { reloadIfAlive(tab) } }
            "update" -> runUpdate(tab)
            "themes" -> showThemes()
            "tools" -> showTools()
            "shield" -> showShield()
            "storage" -> openInternal("/storage")
            "cleancache" -> cleanCacheNow(tab)
            "voice" -> startVoice()
            "addsc" -> askEntry("إضافة اختصار", "shortcuts", Pages.defaultShortcuts) { reloadIfAlive(tab) }
            "addgame" -> askEntry("إضافة لعبة", "games", Pages.defaultGames) { reloadIfAlive(tab) }
            "delsc" -> uri.getQueryParameter("i")?.toIntOrNull()?.let { i ->
                val l = Pages.readEntries("shortcuts", Pages.defaultShortcuts).toMutableList()
                if (i in l.indices) { l.removeAt(i); Pages.writeEntries("shortcuts", l) }
                reloadIfAlive(tab)
            }
            "back" -> if (tab.web.canGoBack()) tab.web.goBack() else tab.web.loadUrl(Pages.HOME_URL)
            "proceed" -> uri.getQueryParameter("u")?.let { u -> AdBlocker.tempAllow(hostOf(u)); tab.web.loadUrl(u) }
            "legacy" -> uri.getQueryParameter("u")?.let { u -> openLegacy(tab, u) }
            "gxrefresh" -> { GxCorner.ensure(this, true); toast("جاري تحديث ركن الألعاب...") }
            "clearspy" -> io.execute { db.clearSpyLog(); h.post { reloadIfAlive(tab) } }
        }
    }

    private fun openLegacy(tab: Tab, url: String) {
        val hh = hostOf(url)
        if (hh.isEmpty()) return
        db.setLegacy(hh, 1 or 2 or 4)
        val u = if (url.startsWith("https://")) "http://" + url.substring(8) else url
        tab.web.loadUrl(u)
        toast("🕰 وضع التوافق القديم مفعّل لـ $hh")
    }

    private fun askEntry(title: String, key: String, defaults: List<Pages.Entry>, done: () -> Unit) {
        ui.sheet { box, d ->
            box.addView(ui.titleView(title))
            val n = ui.input("الاسم"); val u = ui.input("الرابط  (https://...)")
            box.addView(n); box.addView(ui.space(8)); box.addView(u); box.addView(ui.space(12))
            box.addView(ui.button("حفظ") {
                val name = n.text.toString().trim(); var url = u.text.toString().trim()
                if (name.isNotEmpty() && url.isNotEmpty()) {
                    if (!url.startsWith("http")) url = "https://$url"
                    val l = Pages.readEntries(key, defaults).toMutableList(); l.add(Pages.Entry(name, url))
                    Pages.writeEntries(key, l); d.dismiss(); done()
                }
            })
        }
    }

    private fun startVoice() {
        try {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            startActivityForResult(i, REQ_VOICE)
        } catch (_: Exception) { toast("البحث الصوتي غير متاح على جهازك") }
    }

    internal fun runUpdate(tab: Tab?) {
        Toast.makeText(this, "⏳ جاري تحديث قوائم الحجب...", Toast.LENGTH_LONG).show()
        RuleUpdater.updateNow(this) { r ->
            AdBlocker.refreshRules(this)
            Toast.makeText(this, (if (r.success) "✅ " else "⚠ ") + r.message, Toast.LENGTH_LONG).show()
            tab?.let { reloadIfAlive(it) }
        }
    }

    internal fun cleanCacheNow(tab: Tab?) {
        io.execute { Storage.cleanCache(this); h.post { cur?.web?.clearCache(true); toast("🧹 تم تنظيف الكاش"); tab?.let { reloadIfAlive(it) } } }
    }

    private fun openInternal(path: String) { cur?.web?.loadUrl(Pages.HOME_URL.trimEnd('/') + path) }

    // ═════════════════ الأوفلاين والتحميلات واللقطات ═════════════════

    private fun saveOffline(tab: Tab?) {
        if (tab == null || tab.url.isEmpty() || isInternal(tab.url) || tab.url.startsWith("file:")) { toast("افتح صفحة عادية الأول"); return }
        val url = tab.url; val title = tab.title
        val dir = offlineDir(); dir.mkdirs()
        val file = File(dir, "page_" + System.currentTimeMillis() + ".mht")
        tab.web.saveWebArchive(file.absolutePath, false) { saved ->
            if (saved != null) { val size = File(saved).length(); io.execute { db.addOffline(url, title, saved, size) }; toast("📴 اتحفظت — ${Storage.fmt(size)}") }
            else toast("تعذر حفظ الصفحة")
        }
    }

    private fun openOffline(tab: Tab, id: Long) {
        io.execute {
            val item = db.getOfflineById(id)
            h.post { if (item != null && File(item.path).exists()) tab.web.loadUrl("file://" + item.path) else toast("الملف المحفوظ مش موجود") }
        }
    }

    private val riskyExt = listOf(".apk", ".apks", ".xapk", ".exe", ".bat", ".cmd", ".scr", ".msi", ".jar", ".vbs", ".ps1", ".sh", ".dmg", ".js")

    private fun startDownload(url: String, ua: String?, cd: String?, mime: String?, confirmed: Boolean = false) {
        if (url.startsWith("blob:") || url.startsWith("data:")) { toast("الرابط ده مش مدعوم للتحميل المباشر"); return }
        val name = URLUtil.guessFileName(url, cd, mime)
        if (!confirmed && Cfg.bool(Cfg.DLWARN, true) && riskyExt.any { name.lowercase(Locale.ROOT).endsWith(it) }) {
            ui.sheet { box, d ->
                box.addView(ui.titleView("⚠ ملف قد يكون ضارًا"))
                box.addView(ui.infoView(name + "\nالملفات التنفيذية والتطبيقات من مصادر مجهولة من أشهر طرق الاختراق. حمّله فقط لو بتثق في المصدر."))
                box.addView(ui.button("إلغاء") { d.dismiss() })
                box.addView(ui.space(8))
                box.addView(ui.button("أثق في المصدر، حمّل", false) { d.dismiss(); startDownload(url, ua, cd, mime, true) })
            }
            return
        }
        if (Build.VERSION.SDK_INT in 23..28 && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = DownloadReq(url, ua, cd, mime)
            requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE)
            return
        }
        try {
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                if (!mime.isNullOrBlank()) setMimeType(mime)
                addRequestHeader("User-Agent", ua ?: baseUa)
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                cur?.url?.takeIf { it.startsWith("http") }?.let { addRequestHeader("Referer", it) }
                setTitle(name); setDescription("MaTrIX Co")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "MaTrIX/" + name)
            }
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(req)
            io.execute { db.addDownload(id, name, url, mime) }
            toast("⬇ بدأ التحميل: " + name)
        } catch (e: Exception) { toast("تعذر بدء التحميل: " + (e.message ?: "")) }
    }

    private fun openDownload(rowId: Long) {
        io.execute {
            val item = db.getDownload(rowId)
            h.post {
                if (item == null) { toast("مش لاقي التحميل"); return@post }
                try {
                    val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                    val uri = dm.getUriForDownloadedFile(item.dmId)
                    val mime = dm.getMimeTypeForDownloadedFile(item.dmId)
                    if (uri == null) { toast("الملف لسه بيتحمل أو اتمسح"); return@post }
                    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }

    private fun saveBitmap(bmp: Bitmap) {
        try {
            val name = "MaTrIX_" + System.currentTimeMillis() + ".png"
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MaTrIX")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            if (uri == null) { toast("تعذر حفظ اللقطة"); return }
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            toast("📸 اتحفظت في Pictures/MaTrIX")
        } catch (e: Exception) { toast("تعذر حفظ اللقطة: " + (e.message ?: "")) }
    }

    private fun screenshot(full: Boolean) {
        val t = cur ?: return
        val w = t.web
        if (w.width <= 0) return
        if (Build.VERSION.SDK_INT in 23..28 && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE + 1); toast("اسمح بالتخزين وكرر اللقطة"); return
        }
        if (!full) {
            val bmp = Bitmap.createBitmap(w.width, w.height, Bitmap.Config.ARGB_8888)
            w.draw(Canvas(bmp)); saveBitmap(bmp); return
        }
        val scale = w.scale
        val totalPx = (w.contentHeight * scale).toInt().coerceIn(w.height, 12000)
        val bmp = Bitmap.createBitmap(w.width, totalPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val startY = w.scrollY
        toast("📸 جاري التقاط الصفحة كاملة...")
        fun step(y: Int) {
            if (y >= totalPx) { w.scrollTo(0, startY); saveBitmap(bmp); return }
            w.scrollTo(0, y)
            h.postDelayed({
                val seg = Bitmap.createBitmap(w.width, w.height, Bitmap.Config.ARGB_8888)
                w.draw(Canvas(seg))
                canvas.drawBitmap(seg, 0f, y.toFloat(), null)
                seg.recycle()
                step(y + w.height)
            }, 320)
        }
        step(0)
    }

    // ═════════════════ وضع اللعب والزر العائم ═════════════════

    private fun setGameMode(on: Boolean, auto: Boolean) {
        if (gameMode == on) return
        gameMode = on; autoGame = on && auto
        topBar.visibility = if (on || fullscreen) View.GONE else View.VISIBLE
        bottomBar.visibility = if (on || fullscreen) View.GONE else View.VISIBLE
        if (on) { progress.visibility = View.GONE; closeFind(); hideSpyBar() }
        requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        applySystemBars(on)
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        fab.alpha = if (on) 0.5f else 0.9f
        cur?.web?.evaluateJavascript(Scripts.fps(on && Cfg.bool(Cfg.FPS, true)), null)
        lastFps = 0; lastPing = -1
        updateHud()
        toast(if (on) "🎮 وضع اللعب شغّال" else "خرجت من وضع اللعب")
    }

    private fun updateHud() {
        if (!::hud.isInitialized) return
        val show = gameMode && Cfg.bool(Cfg.FPS, true) && customView == null
        hud.visibility = if (show) View.VISIBLE else View.GONE
        if (show) hud.text = "FPS " + (if (lastFps > 0) lastFps else "--") + "  •  Ping " + (if (lastPing >= 0) "${lastPing}ms" else "--")
    }

    private fun pingGame() {
        val host = cur?.host.orEmpty()
        if (host.isEmpty()) return
        io.execute {
            val t0 = System.nanoTime()
            val ms = try { Socket().use { it.connect(InetSocketAddress(host, 443), 2500) }; ((System.nanoTime() - t0) / 1_000_000).toInt() } catch (_: Exception) { -1 }
            h.post { lastPing = ms; updateHud() }
        }
    }

    private fun buildFab() {
        val size = dp(42)
        fab = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER; setImageDrawable(IconDrawable("bolt", theme.accent2, dp(20)))
            background = Ui.round(Color.argb(235, Color.red(theme.surface2), Color.green(theme.surface2), Color.blue(theme.surface2)), size / 2f, theme.sub.withAlpha(110), dp(1))
            elevation = dp(4).toFloat(); alpha = 0.9f
            visibility = if (Cfg.bool(Cfg.SHOWFAB, true)) View.VISIBLE else View.GONE
        }
        root.addView(fab, FrameLayout.LayoutParams(size, size))
        root.post {
            val fx = Cfg.prefs.getFloat("fab_x", -1f); val fy = Cfg.prefs.getFloat("fab_y", -1f)
            fab.x = if (fx >= 0f) fx.coerceIn(0f, (root.width - size).toFloat()) else (root.width - size - dp(6)).toFloat()
            fab.y = if (fy >= 0f) fy.coerceIn(dp(40).toFloat(), (root.height - size - dp(20)).toFloat()) else root.height * 0.6f
        }
        var dX = 0f; var dY = 0f; var downX = 0f; var downY = 0f; var moved = false
        fab.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dX = v.x - e.rawX; dY = v.y - e.rawY; downX = e.rawX; downY = e.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    if (!moved && (abs(e.rawX - downX) > dp(8) || abs(e.rawY - downY) > dp(8))) { moved = true; hideFabMenu() }
                    if (moved) {
                        v.x = (e.rawX + dX).coerceIn(0f, (root.width - v.width).toFloat())
                        v.y = (e.rawY + dY).coerceIn(dp(30).toFloat(), (root.height - v.height - dp(10)).toFloat())
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) { if (fabOpen) hideFabMenu() else showFabMenu() }
                    else {
                        val target = if (v.x + v.width / 2f < root.width / 2f) dp(6).toFloat() else (root.width - v.width - dp(6)).toFloat()
                        v.animate().x(target).setDuration(180).start()
                        Cfg.prefs.edit().putFloat("fab_x", target).putFloat("fab_y", v.y).apply()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showFabMenu() {
        hideFabMenu()
        val onRight = fab.x + fab.width / 2f > root.width / 2f
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (onRight) Gravity.END else Gravity.START
            setPadding(dp(5), dp(6), dp(5), dp(2))
        }
        val items = listOf(
            FabItem("home", 0) { cur?.web?.loadUrl(Pages.HOME_URL) },
            FabItem("reload", 0) { cur?.web?.reload() },
            FabItem("shield_check", 0) { showSiteSheet() },
            FabItem("game", 0) { setGameMode(!gameMode, false) },
            FabItem("desktop", 0) { setDesktop(cur?.desktop != true) },
            FabItem("full", 0) { setFullscreen(!fullscreen) },
            FabItem("camera", 0) { screenshot(false) },
            FabItem("bolt", 0) { skipNow() },
            FabItem("translate", 0) { translatePage() },
            FabItem("search", 0) { openFind() },
            FabItem("dots", 0) { showTools() }
        )
        for ((icon, color, action) in items) {
            val c = FrameLayout(this).apply {
                background = Ui.oval(theme.surface2)
                addView(menuIcon(icon, theme.text, 20), FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
            }
            c.setOnClickListener { hideFabMenu(); action() }
            menu.addView(c, LinearLayout.LayoutParams(dp(38), dp(38)).apply { bottomMargin = dp(6) })
        }
        menu.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val mw = menu.measuredWidth; val mh = menu.measuredHeight
        val above = fab.y - dp(12); val below = root.height - fab.y - fab.height - dp(12)
        val goUp = above >= below
        val avail = (if (goUp) above else below).coerceAtLeast(dp(90).toFloat())
        val hh = minOf(mh.toFloat(), avail).toInt()
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
            background = Ui.round(Color.argb(240, Color.red(theme.surface), Color.green(theme.surface), Color.blue(theme.surface)), dp(22).toFloat(), theme.sub.withAlpha(70), dp(1))
            elevation = dp(8).toFloat()
            addView(menu, ViewGroup.LayoutParams(WRAP, WRAP))
        }
        root.addView(scroll, FrameLayout.LayoutParams(mw, hh))
        scroll.x = if (onRight) fab.x + fab.width - mw else fab.x
        scroll.y = if (goUp) fab.y - hh - dp(4) else fab.y + fab.height + dp(4)
        scroll.alpha = 0f; scroll.animate().alpha(1f).setDuration(120).start()
        fabMenu = scroll; fabOpen = true
    }

    private fun hideFabMenu() {
        fabMenu?.let { root.removeView(it) }
        fabMenu = null; fabOpen = false
    }

    private fun skipNow() {
        val t = cur ?: return
        if (isInternal(t.url)) { toast("مفيش انتظار في الصفحة دي"); return }
        t.web.evaluateJavascript(Scripts.FORCE_SKIP) { r ->
            if (r == "1") { io.execute { db.addSkipped() }; toast("⚡ جاري تخطي الانتظار... (45 ثانية)") }
            else toast("استنى الصفحة تخلص تحميل وجرّب تاني")
        }
    }

    private fun translatePage() {
        val t = cur ?: return
        if (isInternal(t.url) || !t.url.startsWith("http")) { toast("افتح صفحة عادية الأول"); return }
        t.web.loadUrl("https://translate.google.com/translate?sl=auto&tl=" + Locale.getDefault().language + "&u=" + Uri.encode(t.url))
    }

    // ═════════════════ قفل التطبيق ═════════════════

    private fun lockApp() {
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isKeyguardSecure) { toast("فعّل قفل شاشة للجهاز (PIN/بصمة) عشان قفل المتصفح يشتغل"); return }
        locked = true
        if (lockView2 == null) {
            val v = FrameLayout(this).apply { setBackgroundColor(theme.bg); isClickable = true }
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            col.addView(TextView(this).apply { text = "🔒"; textSize = 54f; gravity = Gravity.CENTER })
            col.addView(TextView(this).apply { text = "MaTrIX Co مقفول"; textSize = 20f; setTextColor(theme.text); gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(16)) })
            col.addView(ui.button("فتح القفل") { lockApp() })
            v.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
            lockView2 = v
            root.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        try {
            @Suppress("DEPRECATION")
            val i = km.createConfirmDeviceCredentialIntent("MaTrIX Co", "أكّد هويتك لفتح المتصفح")
            if (i != null) startActivityForResult(i, REQ_LOCK)
        } catch (_: Exception) { unlockApp() }
    }

    /** نسيت الـ PIN: التحقق بقفل شاشة الجهاز */
    internal fun pinForgot() {
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isKeyguardSecure) { toast("مفيش قفل شاشة للجهاز. الحل الوحيد: مسح بيانات التطبيق من إعدادات أندرويد"); return }
        try {
            @Suppress("DEPRECATION")
            val i = km.createConfirmDeviceCredentialIntent("MaTrIX Co", "أكّد هويتك لإلغاء قفل الـ PIN")
            if (i != null) startActivityForResult(i, REQ_PINRESET)
        } catch (_: Exception) { toast("تعذّر فتح تأكيد الهوية") }
    }

    private fun unlockApp() { locked = false; lockView2?.let { root.removeView(it) }; lockView2 = null; lastStopAt = 0L }

    // ═════════════════ الشاشات (Sheets) ═════════════════

    private fun pal(i: Int): Int = PAL[i % PAL.size]

    // ═════════════════ القائمة المنسدلة (بأسلوب Brave) ═════════════════

    private fun showTools() = showMenu()
    private fun showShield() = showShieldPanel()
    private fun showSiteSheet() = showShieldPanel()
    private fun showSettings() = openSettings("main")
    private fun showThemes() = openSettings("appearance")

    private fun dismissMenu() { menuPopup?.dismiss(); menuPopup = null }
    private fun dismissShield() { shieldPopup?.dismiss(); shieldPopup = null }

    private fun menuIcon(type: String, color: Int = theme.sub, size: Int = 22) =
        ImageView(this).apply { setImageDrawable(IconDrawable(type, color, dp(size))) }

    private fun popupWidth(max: Int) = minOf(dp(max), resources.displayMetrics.widthPixels - dp(16))

    private fun showMenu() {
        dismissMenu(); dismissShield()
        val t = cur
        val pw = popupWidth(320)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(0, dp(8), 0, dp(4)) }

        fun row(icon: String, label: String, sub: String? = null, color: Int = theme.sub, onClick: () -> Unit): View {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(12), dp(18), dp(12))
                setOnClickListener { dismissMenu(); onClick() }
            }
            r.addView(menuIcon(icon, color), LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(18) })
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(this).apply { text = label; textSize = 15f; setTextColor(theme.text) })
            if (sub != null) col.addView(TextView(this).apply { text = sub; textSize = 11f; setTextColor(theme.sub) })
            r.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
            return r
        }
        fun divider(): View = View(this).apply {
            setBackgroundColor(theme.surface2)
            layoutParams = LinearLayout.LayoutParams(MATCH, 1).apply { setMargins(dp(14), dp(4), dp(14), dp(4)) }
        }

        // بطاقة الدرع
        val on = AdBlocker.enabled()
        val hdr = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = Ui.round(theme.surface2, dp(14).toFloat())
            setOnClickListener { dismissMenu(); showShieldPanel() }
        }
        hdr.addView(menuIcon("shield_check", if (on) theme.accent2 else theme.sub, 26), LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(14) })
        val hc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        hc.addView(TextView(this).apply { text = if (on) "الدرع مفعّل" else "الدرع متوقف"; textSize = 15f; setTextColor(theme.text) })
        hc.addView(TextView(this).apply { text = db.todayBlocked().toString() + " محجوب اليوم"; textSize = 11f; setTextColor(theme.sub) })
        hdr.addView(hc, LinearLayout.LayoutParams(0, WRAP, 1f))
        val master = ui.switchView(on)
        master.setOnClickListener {
            ui.applySwitch(master, Cfg.bool(Cfg.SHIELD, true))
            gate {
                val v = !Cfg.bool(Cfg.SHIELD, true); Cfg.setBool(Cfg.SHIELD, v); ui.applySwitch(master, v)
                dismissMenu(); cur?.web?.reload()
            }
        }
        hdr.addView(master)
        box.addView(hdr, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(10), dp(2), dp(10), dp(6)) })

        box.addView(row("home", "الصفحة الرئيسية") { cur?.web?.loadUrl(Pages.HOME_URL) })
        box.addView(row("plus", "علامة تبويب جديدة") { newTab(Pages.HOME_URL) })
        box.addView(row("incognito", "علامة تبويب خاصة") { newTab(Pages.HOME_URL, priv = true) })
        box.addView(divider())
        box.addView(row("history", "السجل") { openInternal("/history") })
        box.addView(row("download", "عمليات التنزيل") { showDownloads() })
        box.addView(row("bookmark", "الإشارات المرجعية") { openInternal("/bookmarks") })
        box.addView(row("storage", "الصفحات المحفوظة") { openInternal("/offline") })
        box.addView(divider())
        box.addView(row("spy", "كاشف التجسس") { openInternal("/spy") })
        box.addView(row("sparkle", "المساعد الذكي") { showAi() })

        var resize: () -> Unit = {}
        fun group(title: String, icon: String, expanded: Boolean, items: List<View>) {
            val cont = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = if (expanded) View.VISIBLE else View.GONE }
            for (v in items) cont.addView(v)
            val chev = menuIcon(if (expanded) "chevron_up" else "chevron_down", theme.sub, 20)
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(12))
            }
            head.addView(menuIcon(icon, theme.sub), LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(18) })
            head.addView(TextView(this).apply { text = title; textSize = 15f; setTextColor(theme.text) }, LinearLayout.LayoutParams(0, WRAP, 1f))
            head.addView(chev, LinearLayout.LayoutParams(dp(20), dp(20)))
            head.setOnClickListener {
                val show = cont.visibility != View.VISIBLE
                cont.visibility = if (show) View.VISIBLE else View.GONE
                chev.setImageDrawable(IconDrawable(if (show) "chevron_up" else "chevron_down", theme.sub, dp(20)))
                resize()
            }
            box.addView(head); box.addView(cont)
        }
        group("أدوات الصفحة", "desktop", false, listOf(
            row("desktop", if (t?.desktop == true) "نسخة الموبايل" else "نسخة الكمبيوتر") { setDesktop(cur?.desktop != true) },
            row("search", "بحث في الصفحة") { openFind() },
            row("book", "وضع القراءة") { toggleReader() },
            row("t:🔊", "قراءة الصفحة بصوت") { speakPage() },
            row("translate", "ترجمة الصفحة") { translatePage() },
            row("camera", "لقطة شاشة") { screenshot(false) },
            row("camera", "لقطة كاملة للصفحة") { screenshot(true) },
            row("storage", "حفظ للقراءة بدون إنترنت") { saveOffline(cur) },
            row("t:▶", "فيديوهات الصفحة") { showVideos() },
            row("t:⧉", "نافذة فيديو عائمة (PiP)") { popOutVideo() },
            row("full", if (fullscreen) "إنهاء ملء الشاشة" else "ملء الشاشة") { setFullscreen(!fullscreen) },
            row("game", if (gameMode) "إنهاء وضع اللعب" else "وضع اللعب") { setGameMode(!gameMode, false) },
            row("moon", "تعتيم المواقع") { toggleDark() },
            row("t:QR", "QR للصفحة") { cur?.let { if (it.url.startsWith("http")) newTab("https://api.qrserver.com/v1/create-qr-code/?size=500x500&data=" + Uri.encode(it.url)) } }
        ))
        group("أدوات متقدمة", "code", false, listOf(
            row("code", "سكريبتات مخصصة") { showScripts() },
            row("eye", "مراقبة تغيّر صفحة") { showWatches() },
            row("game", "جلسات الألعاب") { showSessions() },
            row("storage", "المساحة") { openInternal("/storage") }
        ))
        box.addView(divider())
        box.addView(row("settings", "الإعدادات") { openSettings("main") })

        // صف الأزرار السفلي
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; layoutDirection = View.LAYOUT_DIRECTION_LTR; setPadding(dp(8), dp(8), dp(8), dp(12)) }
        fun circle(icon: String, enabled: Boolean, onClick: () -> Unit): View = FrameLayout(this).apply {
            background = Ui.oval(theme.surface2); alpha = if (enabled) 1f else 0.4f
            addView(menuIcon(icon, theme.text, 22), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            if (enabled) setOnClickListener { dismissMenu(); onClick() }
        }
        val cp = { LinearLayout.LayoutParams(dp(46), dp(46)).apply { setMargins(dp(5), 0, dp(5), 0) } }
        bottom.addView(circle("back", t?.web?.canGoBack() == true) { cur?.web?.goBack() }, cp())
        bottom.addView(circle("forward", t?.web?.canGoForward() == true) { cur?.web?.goForward() }, cp())
        bottom.addView(circle("reload", t != null) { cur?.web?.reload() }, cp())
        bottom.addView(circle(if (t?.bookmarked == true) "bookmark_fill" else "bookmark", t != null && !isInternal(t.url)) { cur?.let { toggleBookmark(it) } }, cp())
        bottom.addView(circle("share", t != null && t.url.startsWith("http")) { cur?.let { shareUrl(it.url) } }, cp())

        val maxH = (resources.displayMetrics.heightPixels * 0.8f).toInt() - (if (bottomBar.visibility == View.VISIBLE) bottomBar.height else 0)
        val spec = View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY)
        bottom.measure(spec, View.MeasureSpec.UNSPECIFIED)
        val bth = bottom.measuredHeight
        val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; addView(box, ViewGroup.LayoutParams(MATCH, WRAP)) }
        fun naturalH(): Int { box.measure(spec, View.MeasureSpec.UNSPECIFIED); return box.measuredHeight + bth }
        var ph = minOf(naturalH(), maxH)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.round(theme.surface, dp(20).toFloat())
            addView(sv, LinearLayout.LayoutParams(MATCH, ph - bth))
            addView(bottom, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        val pop = PopupWindow(content, pw, ph, true)
        pop.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        pop.elevation = dp(12).toFloat()
        pop.isOutsideTouchable = true
        pop.setOnDismissListener { if (menuPopup === pop) menuPopup = null }
        val yOff = if (bottomBar.visibility == View.VISIBLE) bottomBar.height + dp(8) else dp(24)
        resize = {
            ph = minOf(naturalH(), maxH)
            sv.layoutParams = LinearLayout.LayoutParams(MATCH, ph - bth)
            pop.update(dp(8), yOff, pw, ph)
        }
        pop.showAtLocation(root, Gravity.BOTTOM or Gravity.START, dp(8), yOff)
        menuPopup = pop
    }

    // ═════════════════ لوحة الدرع (قائمة منسدلة من شريط العنوان) ═════════════════

    private fun showShieldPanel() {
        dismissShield(); dismissMenu()
        val t = cur
        val host = t?.host.orEmpty()
        val internal = t == null || host.isEmpty() || isInternal(t.url)
        val pw = popupWidth(350)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(14), dp(12), dp(14), dp(12)) }

        fun swRow(title: String, sub: String?, initial: Boolean, onChange: (Boolean) -> Unit): View {
            var st = initial
            val sw = ui.switchView(st)
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(9), 0, dp(9)) }
            val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(TextView(this).apply { text = title; textSize = 14f; setTextColor(theme.text) })
            if (sub != null) c.addView(TextView(this).apply { text = sub; textSize = 11f; setTextColor(theme.sub) })
            r.addView(c, LinearLayout.LayoutParams(0, WRAP, 1f))
            r.addView(sw)
            r.setOnClickListener { gate(true) { st = !st; ui.applySwitch(sw, st); onChange(st) } }
            return r
        }
        fun divider(): View = View(this).apply { setBackgroundColor(theme.surface2); layoutParams = LinearLayout.LayoutParams(MATCH, 1).apply { setMargins(0, dp(6), 0, dp(6)) } }
        fun reloadSoon() { cur?.web?.reload() }

        // الرأس
        val siteOn = !AdBlocker.isSiteAllowed(host) && AdBlocker.enabled()
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(menuIcon(if (internal) "shield_check" else (if (siteOn) "shield_check" else "shield"), if (siteOn || internal) theme.accent2 else theme.sub, 30), LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(12) })
        val hc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        hc.addView(TextView(this).apply { text = if (internal) "MaTrIX Shield" else host; textSize = 16f; setTextColor(theme.text); typeface = Typeface.DEFAULT_BOLD })
        hc.addView(TextView(this).apply { text = if (siteOn || internal) "الدرع نشط" else "الدرع متوقف لهذا الموقع"; textSize = 12f; setTextColor(theme.sub) })
        head.addView(hc, LinearLayout.LayoutParams(0, WRAP, 1f))
        val headSw = ui.switchView(if (internal) AdBlocker.enabled() else siteOn)
        headSw.setOnClickListener {
            ui.applySwitch(headSw, if (internal) AdBlocker.enabled() else siteOn)
            gate {
                if (internal) { Cfg.setBool(Cfg.SHIELD, !Cfg.bool(Cfg.SHIELD, true)) } else AdBlocker.setSiteAllowed(host, siteOn)
                dismissShield(); reloadSoon()
            }
        }
        head.addView(headSw)
        box.addView(head)

        // الأرقام
        val nums = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(0, dp(12), 0, dp(4)) }
        fun stat(v: String, l: String): View {
            val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(4), dp(10), dp(4), dp(10)); background = Ui.round(theme.surface2, dp(12).toFloat()) }
            c.addView(TextView(this).apply { text = v; textSize = 20f; setTextColor(theme.text); typeface = Typeface.DEFAULT_BOLD })
            c.addView(TextView(this).apply { text = l; textSize = 10f; setTextColor(theme.sub) })
            return c
        }
        nums.addView(stat(if (internal) db.todayBlocked().toString() else t!!.blocked.get().toString(), if (internal) "محجوب اليوم" else "محجوب هنا"), LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(6) })
        nums.addView(stat(db.counter("trackers_total").toString(), "متتبعات"), LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(6) })
        nums.addView(stat(db.totalSkipped().toString(), "انتظار تخطّيناه"), LinearLayout.LayoutParams(0, WRAP, 1f))
        box.addView(nums)

        // الأمان (للمواقع فقط)
        if (!internal && t != null) {
            var score = 100
            if (t.url.startsWith("http://")) score -= 40
            if (t.sslBroken) score -= 30
            score -= t.spyFindings.values.count { it >= SpyGuard.ASK } * 8
            if (!siteOn) score -= 15
            score = score.coerceIn(5, 100)
            val cert = try { t.web.certificate } catch (_: Exception) { null }
            var line = "درجة أمان الصفحة: $score/100"
            if (cert != null) line += "\nالشهادة: " + (cert.issuedBy?.oName ?: cert.issuedBy?.cName ?: "؟") + " • تنتهي " + (try { cert.validNotAfter } catch (_: Throwable) { null } ?: "؟")
            else if (t.url.startsWith("http://")) line += "\n⚠ اتصال غير مشفّر (HTTP)"
            box.addView(TextView(this).apply {
                text = line; textSize = 12f
                setTextColor(if (score >= 80) Color.rgb(90, 200, 130) else if (score >= 55) Color.rgb(240, 170, 50) else Color.rgb(235, 90, 80))
                setPadding(dp(2), dp(10), dp(2), dp(2))
            })
            if (t.spyFindings.isNotEmpty()) {
                box.addView(divider())
                box.addView(TextView(this).apply { text = "كاشف التجسس رصد:"; textSize = 11f; setTextColor(theme.sub) })
                for ((k, v) in t.spyFindings) {
                    box.addView(TextView(this).apply { text = "• " + SpyGuard.label(k.substringBefore('|')) + " — " + k.substringAfter('|') + " (" + v + "%)"; textSize = 12f; setTextColor(theme.text); setPadding(0, dp(3), 0, 0) })
                }
            }
        }

        box.addView(divider())
        box.addView(swRow("حجب الإعلانات", null, AdBlocker.adsOn()) { Cfg.setBool(Cfg.ADS, it); reloadSoon() })
        box.addView(swRow("حجب المتتبعات", null, AdBlocker.trkOn()) { Cfg.setBool(Cfg.TRK, it); reloadSoon() })
        box.addView(swRow("نطاقات خبيثة + تحذير", null, AdBlocker.malOn()) { Cfg.setBool(Cfg.MAL, it); reloadSoon() })
        box.addView(swRow("ودجتس السوشيال", "قد تكسر تسجيل الدخول", AdBlocker.socOn()) { Cfg.setBool(Cfg.SOC, it); reloadSoon() })
        box.addView(swRow("فلاتر الإزعاج", "كوكيز ونوافذ منبثقة", AdBlocker.annoyOn()) { Cfg.setBool(Cfg.ANNOY, it); reloadSoon() })
        box.addView(swRow("كاشف التجسس", "≥90% حجب تلقائي، غير كده يسألك", AdBlocker.spyOn()) { Cfg.setBool(Cfg.SPY, it); reloadSoon() })
        if (!internal && t != null) {
            box.addView(divider())
            box.addView(swRow("تخطي الانتظار التلقائي", "لهذا الموقع", t.skipMode != 0) { on -> t.skipMode = if (on) 1 else 0; io.execute { db.setSkipMode(host, if (on) null else 0) }; reloadSoon() })
            box.addView(swRow("وضع التوافق القديم", "HTTP وشهادات قديمة (راوترات/مواقع قديمة)", db.getLegacy(host) != 0) { on -> db.setLegacy(host, if (on) 7 else 0); reloadSoon() })
        }
        box.addView(divider())
        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        btns.addView(ui.button("تقرير الدرع", false) { dismissShield(); openInternal("/shield") }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(6) })
        btns.addView(ui.button("⚡ تخطي الانتظار", false) { dismissShield(); skipNow() }, LinearLayout.LayoutParams(0, WRAP, 1f))
        box.addView(btns)

        val maxH = (resources.displayMetrics.heightPixels * 0.78f).toInt()
        box.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val ph = minOf(box.measuredHeight, maxH)
        val sv = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
            background = Ui.round(theme.surface, dp(20).toFloat())
            addView(box, ViewGroup.LayoutParams(MATCH, WRAP))
        }
        val pop = PopupWindow(sv, pw, ph, true)
        pop.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        pop.elevation = dp(12).toFloat()
        pop.isOutsideTouchable = true
        pop.setOnDismissListener { if (shieldPopup === pop) shieldPopup = null }
        pop.showAsDropDown(topBar, dp(8), -dp(2))
        shieldPopup = pop
    }

    // ═════════════════ GX: التحكم في الذاكرة وتنويم التبويبات ═════════════════

    private fun ramInfo(): Triple<Long, Long, Int> {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        val total = mi.totalMem / 1048576; val avail = mi.availMem / 1048576
        return Triple(total, avail, if (total > 0) (avail * 100 / total).toInt() else 100)
    }

    private fun sleepTab(t: Tab) {
        if (t === cur || t.popup || t.sleeping || !t.url.startsWith("http")) return
        t.pending = t.url
        t.sleeping = true
        t.web.loadUrl("about:blank")
    }

    private fun sleepAll(): Int {
        var n = 0
        for (t in tabs.toList()) if (t !== cur && !t.sleeping && !t.popup && t.url.startsWith("http")) { sleepTab(t); n++ }
        return n
    }

    private fun sleepCheck() {
        val mins = Cfg.int(Cfg.TABSLEEP, 10)
        val now = System.currentTimeMillis()
        val low = Cfg.bool(Cfg.RAMLIM, false) && ramInfo().third < 15
        for (t in tabs.toList()) {
            if (t === cur || t.sleeping || t.popup || !t.url.startsWith("http")) continue
            if (low || (mins > 0 && now - t.lastActive > mins * 60_000L)) sleepTab(t)
        }
    }

    private fun showGxControl() {
        dismissMenu(); dismissShield()
        val pw = popupWidth(350)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
        fun gap(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(h)) }

        box.addView(TextView(this).apply {
            text = "GX CONTROL"; textSize = 17f; setTextColor(theme.accent); typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.18f
            setShadowLayer(12f, 0f, 0f, theme.accent)
        })
        box.addView(gap(8))

        // الذاكرة
        val (total, avail, free) = ramInfo()
        val used = total - avail
        val ramCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)); background = Ui.round(theme.surface2, dp(14).toFloat()) }
        ramCard.addView(TextView(this).apply { text = "ذاكرة الجهاز (RAM)"; textSize = 12f; setTextColor(theme.sub) })
        ramCard.addView(TextView(this).apply { text = "$used / $total MB مستخدمة • متاح $free%"; textSize = 14f; setTextColor(theme.text); setPadding(0, dp(2), 0, dp(6)) })
        val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = if (total > 0) (used * 100 / total).toInt() else 0
            progressTintList = android.content.res.ColorStateList.valueOf(if (free < 15) Color.rgb(235, 80, 80) else theme.accent)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(theme.bg)
        }
        ramCard.addView(pb, LinearLayout.LayoutParams(MATCH, dp(6)))
        box.addView(ramCard)
        box.addView(gap(8))

        fun swRow(title: String, sub: String?, initial: Boolean, onChange: (Boolean) -> Unit): View {
            var st = initial
            val sw = ui.switchView(st)
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(8), dp(4), dp(8)) }
            val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(TextView(this).apply { text = title; textSize = 14f; setTextColor(theme.text) })
            if (sub != null) c.addView(TextView(this).apply { text = sub; textSize = 11f; setTextColor(theme.sub) })
            r.addView(c, LinearLayout.LayoutParams(0, WRAP, 1f)); r.addView(sw)
            r.setOnClickListener { st = !st; ui.applySwitch(sw, st); onChange(st) }
            return r
        }
        box.addView(swRow("محدِّد الذاكرة (RAM Limiter)", "ينوّم التبويبات الخلفية لما المتاح يقل عن 15%", Cfg.bool(Cfg.RAMLIM, false)) { Cfg.setBool(Cfg.RAMLIM, it) })
        val sleepLabel = { m: Int -> if (m == 0) "متوقف" else "$m دقيقة" }
        val sleepRow = TextView(this).apply { textSize = 13f; setTextColor(theme.text); setPadding(dp(4), dp(10), dp(4), dp(10)); text = "تنويم التبويبات الخاملة بعد: " + sleepLabel(Cfg.int(Cfg.TABSLEEP, 10)) + "  ‹" }
        sleepRow.setOnClickListener {
            val next = when (Cfg.int(Cfg.TABSLEEP, 10)) { 0 -> 5; 5 -> 10; 10 -> 30; 30 -> 60; else -> 0 }
            Cfg.setInt(Cfg.TABSLEEP, next); sleepRow.text = "تنويم التبويبات الخاملة بعد: " + sleepLabel(next) + "  ‹"
        }
        box.addView(sleepRow)

        // قاتل التبويبات
        box.addView(TextView(this).apply { text = "HOT TABS (" + tabs.size + ")"; textSize = 11f; setTextColor(theme.sub); letterSpacing = 0.12f; setPadding(dp(2), dp(8), 0, dp(4)) })
        for (t in tabs.toList().take(8)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(6), dp(4), dp(6)) }
            val nm = when { isInternal(t.url) && t.pending == null -> "الرئيسية"; t.title.isNotBlank() -> t.title; else -> t.host.ifEmpty { t.pending ?: "تبويب" } }
            row.addView(TextView(this).apply {
                text = (if (t === cur) "● " else if (t.sleeping) "💤 " else "○ ") + nm; textSize = 12f; setTextColor(if (t === cur) theme.accent2 else theme.text)
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            if (t !== cur && !t.sleeping && t.url.startsWith("http")) {
                row.addView(ui.button("نوّم", false) { sleepTab(t); showGxControl() }.apply { textSize = 11f; setPadding(dp(12), dp(5), dp(12), dp(5)) })
            }
            box.addView(row)
        }
        box.addView(ui.space(6))
        box.addView(ui.button("💤 نوّم كل التبويبات الخلفية", false) { val n = sleepAll(); dismissMenu(); toast("تم تنويم $n تبويب") })
        box.addView(gap(10))

        // اختصارات سريعة
        fun chip(icon: String, label: String, on: Boolean, action: () -> Unit): View {
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(2), dp(8), dp(2), dp(8))
                background = Ui.round(if (on) theme.accent else theme.surface2, dp(12).toFloat())
                setOnClickListener { Fx.tap(it); dismissMenu(); action() }
            }
            c.addView(menuIcon(icon, if (on) Color.WHITE else theme.sub, 20), LinearLayout.LayoutParams(dp(20), dp(20)))
            c.addView(TextView(this).apply { text = label; textSize = 10f; setTextColor(if (on) Color.WHITE else theme.sub); gravity = Gravity.CENTER; setPadding(0, dp(3), 0, 0) })
            return c
        }
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val cl = { LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(3), 0, dp(3), 0) } }
        chips.addView(chip("game", "اللعب", gameMode) { setGameMode(!gameMode, false) }, cl())
        chips.addView(chip("bolt", "FPS/Ping", Cfg.bool(Cfg.FPS, true)) { Cfg.setBool(Cfg.FPS, !Cfg.bool(Cfg.FPS, true)); updateHud() }, cl())
        chips.addView(chip("desktop", "كمبيوتر", cur?.desktop == true) { setDesktop(cur?.desktop != true) }, cl())
        chips.addView(chip("full", "ملء الشاشة", fullscreen) { setFullscreen(!fullscreen) }, cl())
        chips.addView(chip("shield_check", "الدرع", AdBlocker.enabled()) { Cfg.setBool(Cfg.SHIELD, !Cfg.bool(Cfg.SHIELD, true)); cur?.web?.reload() }, cl())
        box.addView(chips)
        box.addView(gap(10))

        val cleaner = ui.button("🧹 GX Cleaner — تنظيف الكاش", false) { dismissMenu(); cleanCacheNow(cur) }
        box.addView(cleaner)
        computeStorageAsync { s -> cleaner.text = "🧹 GX Cleaner — تنظيف الكاش (" + Storage.fmt(s.cache) + ")" }
        box.addView(TextView(this).apply { text = "ملاحظة: محدِّد CPU والشبكة غير ممكنين داخل متصفح أندرويد."; textSize = 10f; setTextColor(theme.sub); setPadding(dp(2), dp(8), 0, 0) })

        val maxH = (resources.displayMetrics.heightPixels * 0.82f).toInt() - (if (bottomBar.visibility == View.VISIBLE) bottomBar.height else 0)
        box.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val ph = minOf(box.measuredHeight, maxH)
        val sv = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
            background = Ui.round(theme.surface, dp(22).toFloat(), theme.accent.withAlpha(120), dp(1))
            addView(box, ViewGroup.LayoutParams(MATCH, WRAP))
        }
        val pop = PopupWindow(sv, pw, ph, true)
        pop.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        pop.elevation = dp(14).toFloat()
        pop.isOutsideTouchable = true
        pop.setOnDismissListener { if (menuPopup === pop) menuPopup = null }
        pop.showAtLocation(root, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, if (bottomBar.visibility == View.VISIBLE) bottomBar.height + dp(8) else dp(24))
        menuPopup = pop
    }

    // ═════════════════ نافذة الفيديو العائمة (PiP) ═════════════════

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipIfVideo()
    }

    private fun enterPipIfVideo(): Boolean {
        if (customView == null || Build.VERSION.SDK_INT < 26) return false
        return try { enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build()); true } catch (_: Throwable) { false }
    }

    private fun popOutVideo() {
        if (!enterPipIfVideo()) toast("شغّل الفيديو بملء الشاشة الأول، وبعدها ارجع للرئيسية وهيتحول لنافذة عائمة")
    }

    // ═════════════════ خلفية الصفحة الرئيسية (صورة من المعرض) ═════════════════

    internal fun pickWallpaper() {
        try { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), REQ_WALL) } catch (_: Exception) { toast("لا يوجد تطبيق معرض صور") }
    }

    private fun saveWallpaper(u: Uri) {
        try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
            var sample = 1
            while (o.outWidth / sample > 1400 || o.outHeight / sample > 1400) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o2) }
            if (bmp == null) { h.post { toast("تعذر قراءة الصورة") }; return }
            File(filesDir, "wall.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 72, it) }
            Cfg.setStr(Cfg.WALL, "custom")
            h.post { toast("✅ تم تعيين الخلفية"); val t = cur; if (t != null && isInternal(t.url)) t.web.reload() }
        } catch (_: Exception) { h.post { toast("تعذر حفظ الصورة") } }
    }

    // ═════════════════ الإعدادات (صفحة كاملة) ═════════════════

    private fun openSettings(page: String = "main") {
        dismissMenu(); dismissShield()
        if (page == "appearance" || page == "gxmods") { openSettingsNow(page); return }
        pinLock.ask { openSettingsNow(page) }
    }

    /** بينفّذ الإجراء لو القفل مش شغّال أو الجلسة مفتوحة، وإلا بيطلب الـ PIN الأول */
    private fun gate(reopenShield: Boolean = false, block: () -> Unit) {
        if (!PinLock.enabled() || PinLock.sessionOpen()) { block(); return }
        dismissShield(); dismissMenu()
        pinLock.ask { block(); if (reopenShield) showShieldPanel() }
    }

    private fun openSettingsNow(page: String) {
        settings?.close()
        val sc = SettingsScreen(this, ui, theme, root) { settings = null }
        settings = sc
        sc.open(page)
    }

    // دوال تستخدمها صفحة الإعدادات
    internal fun engineLabel() = engineName()
    internal fun reloadHomeIfShown() { val t = cur; if (t != null && isInternal(t.url)) t.web.reload() }
    internal fun toastMsg(m: String) = toast(m)
    internal fun currentTab(): Tab? = cur
    internal fun reloadCurrent() { cur?.web?.reload() }
    internal fun openInternalPage(path: String) = openInternal(path)
    internal fun openDownloadsManager() = showDownloads()
    internal fun refreshHostSettings() { for (x in tabs) applyHostSettings(x) }
    internal fun applyThirdPartyCookies(on: Boolean) { for (x in tabs) CookieManager.getInstance().setAcceptThirdPartyCookies(x.web, on) }
    internal fun applyDataSaver(on: Boolean) { for (x in tabs) x.web.settings.loadsImagesAutomatically = !on; cur?.web?.reload() }
    internal fun applyFabVisibility(on: Boolean) { fab.visibility = if (on) View.VISIBLE else View.GONE }
    internal fun applyDarkPages(on: Boolean) { cur?.web?.evaluateJavascript("window.__mxDark&&window.__mxDark($on)", null) }
    internal fun computeStorageAsync(cb: (Storage.Info) -> Unit) { io.execute { val st = Storage.compute(this); h.post { cb(st) } } }


    internal fun applyTheme(id: String) {
        Cfg.setStr(Cfg.THEME, id)
        saveSession()
        recreate()
    }


    private fun toggleDark() {
        val on = !Cfg.bool(Cfg.DARK, false)
        Cfg.setBool(Cfg.DARK, on)
        cur?.web?.evaluateJavascript("window.__mxDark&&window.__mxDark($on)", null)
        toast(if (on) "🌙 تعتيم المواقع: شغّال" else "تعتيم المواقع: مقفول")
    }

    private fun desktopWidth(): Int = if (resources.configuration.screenWidthDp >= 600) 1366 else 1100

    private fun setDesktop(on: Boolean) {
        val t = cur ?: return
        t.desktop = on
        t.web.settings.userAgentString = if (on) DESKTOP_UA else baseUa
        t.web.settings.useWideViewPort = true
        t.web.settings.loadWithOverviewMode = true
        if (!gameMode) requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        t.web.reload()
        toast(if (on) "🖥 وضع الكمبيوتر — لف الجهاز أفقيًا لمساحة أكبر" else "رجعت لنسخة الموبايل")
    }

    private fun setFullscreen(on: Boolean) {
        if (fullscreen == on) return
        fullscreen = on
        topBar.visibility = if (on || gameMode) View.GONE else View.VISIBLE
        bottomBar.visibility = if (on || gameMode) View.GONE else View.VISIBLE
        if (!gameMode) applySystemBars(on)
        toast(if (on) "⛶ ملء الشاشة — الزر العائم للخروج" else "خرجت من ملء الشاشة")
    }

    internal fun confirmClear() {
        ui.sheet { box, d ->
            box.addView(ui.titleView("مسح بيانات التصفح؟"))
            box.addView(ui.infoView("هيتمسح السجل والكوكيز والكاش وبيانات المواقع. المفضلة والصفحات المحفوظة مش هتتأثر."))
            box.addView(ui.button("مسح") {
                d.dismiss()
                CookieManager.getInstance().removeAllCookies(null); WebStorage.getInstance().deleteAllData()
                for (x in tabs) x.web.clearCache(true)
                io.execute { db.clearHistory(); Storage.cleanCache(this) }
                toast("🧹 تم المسح")
            })
        }
    }

    /** نسخة احتياطية في ملف (بيطلب الـ PIN لو القفل شغّال) */
    internal fun exportBackup() = gate {
        val name = "MaTrIX-backup-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".json"
        try {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, name), REQ_BK_EXPORT)
        } catch (_: Exception) { toast("تعذّر فتح مدير الملفات") }
    }

    internal fun importBackup() = gate {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_BK_IMPORT)
        } catch (_: Exception) { toast("تعذّر فتح مدير الملفات") }
    }

    // ── شاشة التبويبات (بأسلوب Brave): معاينة حقيقية لكل تبويب + وضع خاص بشخصيته
    private fun showTabs(startPriv: Boolean? = null) {
        cur?.let { captureThumb(it) }
        val d = Dialog(this)
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        var priv = startPriv ?: (cur?.priv == true)
        var query = ""
        var selecting = false
        val selected = HashSet<Tab>()
        var menuOpen = false

        val pBg = Color.rgb(20, 11, 36); val pCard = Color.rgb(42, 26, 70); val pAcc = Color.rgb(184, 140, 255)
        fun bgC() = if (priv) pBg else theme.bg
        fun cardC() = if (priv) pCard else theme.surface2
        fun accC() = if (priv) pAcc else theme.accent
        fun txtC() = if (priv) Color.WHITE else theme.text
        fun subC() = if (priv) Color.rgb(176, 160, 205) else theme.sub

        val rootV = FrameLayout(this).apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rootV.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        var nbH = 0
        @Suppress("DEPRECATION")
        rootV.setOnApplyWindowInsetsListener { _, ins ->
            nbH = ins.systemWindowInsetBottom
            content.setPadding(0, ins.systemWindowInsetTop, 0, ins.systemWindowInsetBottom)
            ins
        }

        val headerSlot = FrameLayout(this)
        val searchV = EditText(this).apply {
            hint = "البحث في علامات التبويب"; textSize = 15f; maxLines = 1; isSingleLine = true
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(20), 0, dp(20), 0)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(16)) }
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(listBox, ViewGroup.LayoutParams(MATCH, WRAP)) }
        val bottomSlot = FrameLayout(this)
        content.addView(headerSlot, LinearLayout.LayoutParams(MATCH, dp(64)))
        content.addView(searchV, LinearLayout.LayoutParams(MATCH, dp(48)).apply { setMargins(dp(14), dp(2), dp(14), dp(6)) })
        content.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        content.addView(bottomSlot, LinearLayout.LayoutParams(MATCH, dp(84)))

        val menuLayer = FrameLayout(this).apply { visibility = View.GONE; setBackgroundColor(Color.argb(90, 0, 0, 0)) }
        rootV.addView(menuLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        fun nameOf(t: Tab): String = when {
            isInternal(t.url) && t.pending == null -> if (t.priv) "علامة تبويب خاصة" else "علامة تبويب جديدة"
            t.title.isNotBlank() -> t.title
            else -> t.host.ifEmpty { t.pending ?: "علامة تبويب" }
        }.let { if (t.sleeping) "💤 " + it else it }.let { if (t.pinned) "📌 " + it else it }
        fun countOf(p: Boolean) = tabs.count { it.priv == p }
        fun icon(name: String, color: Int, size: Int, label: String = "") = IconDrawable(name, color, dp(size), label)
        fun iconView(name: String, color: Int, size: Int) = ImageView(this).apply { setImageDrawable(icon(name, color, size)) }

        lateinit var renderAll: () -> Unit
        lateinit var renderList: () -> Unit

        fun showSnack(msg: String, undo: (() -> Unit)?) {
            val fl = FrameLayout(this)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(12), dp(14), dp(12))
                background = Ui.round(cardC(), dp(16).toFloat(), subC().withAlpha(60), dp(1))
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            row.addView(TextView(this).apply { text = msg; textSize = 13f; setTextColor(txtC()) }, LinearLayout.LayoutParams(0, WRAP, 1f))
            if (undo != null) row.addView(TextView(this).apply {
                text = "تراجع"; textSize = 14f; setTextColor(accC()); setPadding(dp(14), dp(6), dp(6), dp(6))
                setOnClickListener { rootV.removeView(fl); undo() }
            })
            fl.addView(row, FrameLayout.LayoutParams(MATCH, WRAP))
            rootV.addView(fl, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { setMargins(dp(14), 0, dp(14), nbH + dp(96)) })
            h.postDelayed({ rootV.removeView(fl) }, 5000)
        }

        fun closeMany(list: List<Tab>) {
            if (list.isEmpty()) return
            val saved = list.map { Pair(if (it.url.isNotEmpty() && !isInternal(it.url)) it.url else (it.pending ?: Pages.HOME_URL), it.priv) }
            val n = list.size
            for (t in list) closeTab(t)
            selected.clear(); selecting = false
            renderAll()
            showSnack(if (n == 1) "تم إغلاق علامة تبويب واحدة" else "تم إغلاق $n علامة تبويب", {
                for ((u, p) in saved) newTab(u, p, select = false)
                renderAll()
            })
        }

        fun gesture(dir: String): String = Cfg.str("tg_$dir", when (dir) { "left" -> "close"; "right" -> "pin"; "up" -> "closeclear"; else -> "suspend" })

        fun runTabAction(t: Tab, act: String) {
            when (act) {
                "close", "closeclear" -> {
                    if (t.pinned) { showSnack("📌 التاب مثبّت — فك التثبيت الأول", null); renderList() }
                    else { val host = t.host; closeMany(listOf(t)); if (act == "closeclear") clearSiteData(host) }
                }
                "pin" -> { t.pinned = !t.pinned; showSnack(if (t.pinned) "📌 اتثبّت" else "اتفك التثبيت", null); renderList() }
                "suspend" -> { if (t === cur) showSnack("مينفعش تجمّد التاب الحالي", null) else sleepTab(t); renderList() }
                "gamemode" -> { d.dismiss(); setGameMode(!gameMode, false) }
                else -> renderList()
            }
        }

        /** سحب الكارت: شمال/يمين على الكارت كله، فوق/تحت من شريط العنوان (عشان ما يتعارضش مع سكرول القايمة) */
        fun attachSwipe(trigger: View, card: View, t: Tab, vertical: Boolean) {
            val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
            var sx = 0f; var sy = 0f; var mode = 0
            trigger.setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> { sx = ev.rawX; sy = ev.rawY; mode = 0; if (vertical) v.parent?.requestDisallowInterceptTouchEvent(true); true }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - sx; val dy = ev.rawY - sy
                        if (mode == 0 && maxOf(Math.abs(dx), Math.abs(dy)) > slop) mode = if (Math.abs(dx) > Math.abs(dy)) 1 else if (vertical) 2 else 3
                        if (mode == 1) { v.parent?.requestDisallowInterceptTouchEvent(true); card.translationX = dx; card.alpha = 1f - minOf(0.6f, Math.abs(dx) / card.width) }
                        else if (mode == 2) { card.translationY = dy; card.alpha = 1f - minOf(0.6f, Math.abs(dy) / (card.height.toFloat() + 1f)) }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val dx = ev.rawX - sx; val dy = ev.rawY - sy
                        var act: String? = null
                        if (mode == 1 && Math.abs(dx) > card.width * 0.4f) act = gesture(if (dx < 0) "left" else "right")
                        else if (mode == 2 && Math.abs(dy) > card.height * 0.4f) act = gesture(if (dy < 0) "up" else "down")
                        card.animate().translationX(0f).translationY(0f).alpha(1f).setDuration(150).start()
                        if (mode == 0) card.performClick()
                        else if (act != null && act != "none") { v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); runTabAction(t, act) }
                        true
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> { card.animate().translationX(0f).translationY(0f).alpha(1f).setDuration(150).start(); true }
                    else -> true
                }
            }
        }

        fun menuRow(ic: String, label: String, color: Int = txtC(), onClick: () -> Unit): View {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), 0, dp(18), 0)
                setOnClickListener { menuLayer.visibility = View.GONE; menuOpen = false; onClick() }
            }
            r.addView(iconView(ic, color, 24), LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(16) })
            r.addView(TextView(this).apply { text = label; textSize = 15f; setTextColor(color) }, LinearLayout.LayoutParams(0, WRAP, 1f))
            return r
        }

        fun openMenu() {
            menuLayer.removeAllViews()
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(0, dp(8), 0, dp(8))
                background = Ui.round(if (priv) Color.rgb(34, 20, 58) else theme.surface, dp(18).toFloat(), subC().withAlpha(50), dp(1))
                elevation = dp(8).toFloat()
            }
            val rowH = dp(52)
            fun add(v: View) = card.addView(v, LinearLayout.LayoutParams(MATCH, rowH))
            add(menuRow("plus", "علامة تبويب جديدة") { d.dismiss(); newTab(Pages.HOME_URL) })
            add(menuRow("incognito", "علامة تبويب خاصة جديدة") { d.dismiss(); newTab(Pages.HOME_URL, priv = true) })
            add(menuRow("close", "إغلاق جميع علامات التبويب") { closeMany(tabs.filter { it.priv == priv && !it.pinned }) })
            add(menuRow("check", "اختيار علامات التبويب") { selecting = true; selected.clear(); renderAll() })
            add(menuRow("trash", "حذف بيانات التصفح") { d.dismiss(); confirmClear() })
            add(menuRow("settings", "الإعدادات") { d.dismiss(); showSettings() })
            menuLayer.addView(card, FrameLayout.LayoutParams(dp(280), WRAP, Gravity.BOTTOM or Gravity.LEFT).apply { setMargins(dp(14), 0, 0, nbH + dp(84)) })
            menuLayer.setOnClickListener { menuLayer.visibility = View.GONE; menuOpen = false }
            menuLayer.visibility = View.VISIBLE; menuOpen = true
        }

        fun buildHeader() {
            headerSlot.removeAllViews()
            if (selecting) {
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
                row.addView(TextView(this).apply {
                    text = if (selected.isEmpty()) "اختر علامات التبويب" else "تم تحديد " + selected.size; textSize = 17f; setTextColor(txtC()); typeface = Typeface.DEFAULT_BOLD
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                row.addView(TextView(this).apply {
                    text = "إلغاء"; textSize = 15f; setTextColor(accC()); setPadding(dp(12), dp(10), dp(12), dp(10))
                    setOnClickListener { selecting = false; selected.clear(); renderAll() }
                })
                headerSlot.addView(row, FrameLayout.LayoutParams(MATCH, MATCH))
                return
            }
            val seg = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_LTR
                setPadding(dp(5), dp(5), dp(5), dp(5))
                background = Ui.round(if (priv) Color.rgb(32, 19, 54) else theme.surface, dp(28).toFloat())
            }
            fun segBtn(isPriv: Boolean) {
                val on = priv == isPriv
                val b = FrameLayout(this).apply {
                    background = if (on) Ui.round(cardC(), dp(22).toFloat()) else null
                    setOnClickListener { if (priv != isPriv) { priv = isPriv; selected.clear(); selecting = false; renderAll() } }
                }
                val col = if (on) txtC() else subC()
                if (isPriv) {
                    b.addView(iconView("incognito", col, 26), FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
                    val n = countOf(true)
                    if (n > 0) b.addView(TextView(this).apply {
                        text = n.toString(); textSize = 9f; setTextColor(bgC()); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
                        background = Ui.round(if (priv) pAcc else Color.rgb(176, 130, 255), dp(8).toFloat())
                        setPadding(dp(4), 0, dp(4), 0)
                    }, FrameLayout.LayoutParams(WRAP, dp(14), Gravity.TOP or Gravity.RIGHT).apply { setMargins(0, dp(3), dp(6), 0) })
                } else {
                    b.addView(ImageView(this).apply { setImageDrawable(icon("tabs", col, 26, countOf(false).toString())) }, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
                }
                seg.addView(b, LinearLayout.LayoutParams(dp(60), dp(42)).apply { if (seg.childCount > 0) marginStart = dp(4) })
            }
            segBtn(false); segBtn(true)
            headerSlot.addView(seg, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }

        fun buildBottom() {
            bottomSlot.removeAllViews()
            if (selecting) {
                val btn = TextView(this).apply {
                    text = if (selected.isEmpty()) "إغلاق" else "إغلاق (" + selected.size + ")"
                    textSize = 15f; gravity = Gravity.CENTER; setTextColor(bgC()); typeface = Typeface.DEFAULT_BOLD
                    background = Ui.round(accC().withAlpha(if (selected.isEmpty()) 90 else 255), dp(20).toFloat())
                    setOnClickListener { if (selected.isNotEmpty()) closeMany(selected.toList()) }
                }
                bottomSlot.addView(btn, FrameLayout.LayoutParams(dp(200), dp(54), Gravity.CENTER))
                return
            }
            val plus = FrameLayout(this).apply {
                background = Ui.round(accC(), dp(20).toFloat())
                setOnClickListener { d.dismiss(); newTab(Pages.HOME_URL, priv = priv) }
            }
            plus.addView(iconView("plus", bgC(), 30), FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
            bottomSlot.addView(plus, FrameLayout.LayoutParams(dp(76), dp(56), Gravity.CENTER))
            val dots = FrameLayout(this).apply { setOnClickListener { openMenu() } }
            dots.addView(iconView("dots", subC(), 26), FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
            bottomSlot.addView(dots, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.LEFT or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(14) })
        }

        fun emptyState() {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(18), dp(60), dp(18), dp(20)) }
            val badge = FrameLayout(this).apply { background = Ui.round(accC().withAlpha(50), dp(40).toFloat()) }
            badge.addView(iconView(if (priv) "incognito" else "tabs", accC(), 44), FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
            col.addView(badge, LinearLayout.LayoutParams(dp(112), dp(96)))
            col.addView(TextView(this).apply {
                text = if (query.isNotEmpty()) "لا توجد نتائج" else if (priv) "علامة تبويب خاصة" else "ستظهر لك علامات التبويب هنا"
                textSize = 20f; setTextColor(txtC()); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(26) })
            col.addView(TextView(this).apply {
                text = if (query.isNotEmpty()) "جرّب كلمات أخرى" else if (priv) "لا تترك أي أثر على جهازك" else "يمكنك فتح علامات تبويب لزيارة صفحات مختلفة في آنٍ واحد"
                textSize = 14f; setTextColor(subC()); gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
            if (priv && query.isEmpty()) {
                val info = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
                    background = Ui.round(pCard, dp(18).toFloat())
                }
                info.addView(TextView(this).apply { text = "عن التصفح الخاص"; textSize = 14f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
                val lines = listOf(
                    "• لا يُحفظ سجل التصفح ولا الكوكيز ولا بيانات المواقع بعد إغلاق التبويبات الخاصة.",
                    "• لا تُحفظ الصفحات التي تزورها في السجل ولا في الاقتراحات.",
                    "• التنزيلات والإشارات المرجعية التي تنشئها تبقى محفوظة.",
                    "• قد يظل موقع الويب أو شبكتك أو مزوّد الإنترنت قادرًا على رؤية نشاطك."
                )
                for (l in lines) info.addView(TextView(this).apply { text = l; textSize = 13f; setTextColor(subC()); setLineSpacing(0f, 1.15f) }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
                col.addView(info, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(28) })
            }
            listBox.addView(col, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        renderList = {
            listBox.removeAllViews()
            val q = query.trim()
            val vis = tabs.filter { it.priv == priv && (q.isEmpty() || nameOf(it).contains(q, true) || it.url.contains(q, true)) }
            if (vis.isEmpty()) emptyState()
            else {
                if (priv && q.isEmpty()) listBox.addView(TextView(this).apply {
                    text = "🕶  لا تترك أي أثر على جهازك"; textSize = 12f; setTextColor(subC()); gravity = Gravity.CENTER
                    setPadding(0, dp(2), 0, dp(10))
                }, LinearLayout.LayoutParams(MATCH, WRAP))
                val sw = resources.displayMetrics.widthPixels
                val cardW = (minOf(sw, dp(560)) - dp(24) - dp(16)) / 2
                val imgH = (cardW * 1.12f).toInt()
                val cards = ArrayList<View>()
                for (t in vis) {
                    val isCur = t === cur
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = Ui.round(if (isCur && !selecting) accC().withAlpha(215) else cardC(), dp(20).toFloat())
                        setPadding(dp(5), 0, dp(5), dp(5))
                        setOnClickListener {
                            if (selecting) { if (!selected.remove(t)) selected.add(t); buildHeader(); buildBottom(); renderList() }
                            else { d.dismiss(); selectTab(t) }
                        }
                    }
                    val onAcc = isCur && !selecting
                    val tc = if (onAcc) bgC() else txtC()
                    val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(2), 0) }
                    bar.addView(iconView(if (t.priv) "incognito" else if (isInternal(t.url)) "shield" else "globe", tc, 18), LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) })
                    bar.addView(TextView(this).apply {
                        text = nameOf(t); textSize = 13f; setTextColor(tc); maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }, LinearLayout.LayoutParams(0, WRAP, 1f))
                    if (!selecting) bar.addView(FrameLayout(this).apply {
                        addView(iconView("close", tc, 20), FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
                        setOnClickListener { closeMany(listOf(t)) }
                    }, LinearLayout.LayoutParams(dp(40), dp(40)))
                    card.addView(bar, LinearLayout.LayoutParams(MATCH, dp(40)))
                    val frame = FrameLayout(this).apply {
                        background = Ui.round(if (priv) Color.rgb(28, 16, 48) else theme.bg, dp(14).toFloat())
                        clipToOutline = true
                    }
                    val th = t.thumb
                    if (th != null) frame.addView(TopCropImage(this).apply { setImageBitmap(th) }, FrameLayout.LayoutParams(MATCH, MATCH))
                    else frame.addView(iconView(if (t.priv) "incognito" else "globe", subC().withAlpha(120), 40), FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
                    if (selecting) {
                        val on = selected.contains(t)
                        frame.addView(FrameLayout(this).apply {
                            background = Ui.round(if (on) accC() else Color.argb(140, 0, 0, 0), dp(13).toFloat(), Color.WHITE, dp(2))
                            if (on) addView(iconView("check", bgC(), 18), FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
                        }, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.TOP or Gravity.LEFT).apply { setMargins(dp(8), dp(8), 0, 0) })
                    }
                    card.addView(frame, LinearLayout.LayoutParams(MATCH, imgH))
                    attachSwipe(card, card, t, false)
                    attachSwipe(bar, card, t, true)
                    cards.add(card)
                }
                var i = 0
                while (i < cards.size) {
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
                    for (j in 0..1) {
                        val v = cards.getOrNull(i + j)
                        if (v != null) row.addView(v, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
                        else row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
                    }
                    listBox.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
                    i += 2
                }
            }
        }

        renderAll = {
            rootV.setBackgroundColor(bgC())
            @Suppress("DEPRECATION") run { d.window?.statusBarColor = bgC(); d.window?.navigationBarColor = bgC() }
            searchV.setTextColor(txtC()); searchV.setHintTextColor(subC())
            searchV.background = Ui.round(if (priv) Color.rgb(34, 20, 58) else theme.surface, dp(24).toFloat())
            buildHeader(); buildBottom(); renderList()
        }

        searchV.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty(); renderList() }
        })
        searchV.setOnEditorActionListener { v, _, _ -> v.clearFocus(); hideKeyboardFrom(v); true }

        d.setContentView(rootV)
        d.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout(MATCH, MATCH)
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        d.setOnKeyListener { _, code, ev ->
            if (code == android.view.KeyEvent.KEYCODE_BACK && ev.action == android.view.KeyEvent.ACTION_UP) {
                when {
                    menuOpen -> { menuLayer.visibility = View.GONE; menuOpen = false }
                    selecting -> { selecting = false; selected.clear(); renderAll() }
                    else -> d.dismiss()
                }
                true
            } else false
        }
        renderAll()
        d.show()
    }

    private fun hideKeyboardFrom(v: View) {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
    }

    // ── مدير التحميلات (تقدّم مباشر)
    private fun showDownloads() {
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        var alive = true
        lateinit var listBox: LinearLayout
        fun render(d: Dialog) {
            listBox.removeAllViews()
            val items = db.getDownloads(15)
            if (items.isEmpty()) listBox.addView(ui.infoView("لا توجد تحميلات بعد"))
            for (dl in items) {
                var status = -1; var total = 0L; var so = 0L
                try {
                    dm.query(DownloadManager.Query().setFilterById(dl.dmId)).use { c ->
                        if (c.moveToFirst()) {
                            status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            so = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        }
                    }
                } catch (_: Exception) {}
                val pct = if (total > 0) ((so * 100) / total).toInt() else 0
                val label = when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> "✅ اكتمل • " + Storage.fmt(total)
                    DownloadManager.STATUS_FAILED -> "❌ فشل"
                    DownloadManager.STATUS_PAUSED -> "⏸ متوقف • $pct%"
                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> Storage.fmt(so) + " / " + (if (total > 0) Storage.fmt(total) else "؟") + " • $pct%"
                    else -> "غير موجود"
                }
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(8), dp(10))
                    background = Ui.grad(theme.surface2, theme.surface2.darker(0.85f), dp(16).toFloat(), theme.accent.withAlpha(50), dp(1))
                    setOnClickListener { if (status == DownloadManager.STATUS_SUCCESSFUL) openDownload(dl.id) }
                }
                val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                top.addView(TextView(this).apply { text = dl.name; textSize = 13f; setTextColor(theme.text); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, WRAP, 1f))
                top.addView(TextView(this).apply {
                    text = "✕"; textSize = 16f; setTextColor(theme.accent2); setPadding(dp(10), 0, dp(10), 0)
                    setOnClickListener { try { dm.remove(dl.dmId) } catch (_: Exception) {}; io.execute { db.deleteDownload(dl.id) }; render(d) }
                })
                card.addView(top)
                if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED) {
                    val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100; progress = pct
                        progressTintList = android.content.res.ColorStateList.valueOf(theme.accent2)
                    }
                    card.addView(pb, LinearLayout.LayoutParams(MATCH, dp(6)).apply { topMargin = dp(6); bottomMargin = dp(4) })
                }
                card.addView(TextView(this).apply { text = label; textSize = 11f; setTextColor(theme.sub) })
                card.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(8) }
                listBox.addView(card)
            }
        }
        ui.sheet { box, d ->
            box.addView(ui.titleView("⬇ مدير التحميلات"))
            listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            box.addView(listBox)
            box.addView(ui.button("📂 فتح مجلد التنزيلات", false) { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
            render(d)
            val poll = object : Runnable { override fun run() { if (alive) { render(d); h.postDelayed(this, 1200) } } }
            h.postDelayed(poll, 1200)
            d.setOnDismissListener { alive = false }
        }
    }

    // ── الفيديوهات المكتشفة
    private fun showVideos() {
        val t = cur ?: return
        val list = synchronized(t.videos) { t.videos.toList() }
        ui.sheet { box, d ->
            box.addView(ui.titleView("🎬 فيديوهات الصفحة (" + list.size + ")"))
            if (list.isEmpty()) box.addView(ui.infoView("لم يُكتشف فيديو. شغّل الفيديو أولًا ثم افتح هذه القائمة.\nيوتيوب غير مدعوم (يخالف شروطه)."))
            for (u in list) {
                val hls = u.contains(".m3u8", true) || u.contains(".mpd", true)
                val name = try { Uri.parse(u).lastPathSegment ?: u } catch (_: Exception) { u }
                box.addView(ui.row(if (hls) "📡" else "🎬", name.take(40), if (hls) "بث HLS/DASH — نسخ الرابط فقط" else hostOf(u), null, pal(if (hls) 4 else 0)) {
                    d.dismiss()
                    if (hls) { copyText(u) } else startDownload(u, baseUa, null, null)
                })
            }
        }
    }

    // ── سكريبتات المستخدم
    private fun showScripts() {
        ui.sheet { box, d ->
            box.addView(ui.titleView("📜 سكريبتات مخصصة"))
            box.addView(ui.infoView("JS أو CSS يتنفذ تلقائيًا على المواقع اللي تحددها (host أو * لكل المواقع)."))
            val list = db.getScripts()
            for (s in list) {
                box.addView(ui.toggleRow(if (s.type == "css") "🎨" else "📜", s.name.ifBlank { s.host }, s.host + " • " + s.type, s.enabled, pal(2)) { on ->
                    io.execute { db.setScriptEnabled(s.id, on) }
                })
                box.addView(ui.button("🗑 حذف: " + s.name.ifBlank { s.host }, false) { d.dismiss(); io.execute { db.deleteScript(s.id) }; h.postDelayed({ showScripts() }, 150) })
                box.addView(ui.space(6))
            }
            box.addView(ui.button("➕ سكريبت جديد") { d.dismiss(); editScript() })
        }
    }

    private fun editScript() {
        ui.sheet { box, d ->
            box.addView(ui.titleView("سكريبت جديد"))
            val host = ui.input("الموقع (example.com أو *)", false, cur?.host ?: "")
            val name = ui.input("اسم")
            val code = ui.input("الكود (JS أو CSS)", true)
            var type = "js"
            val typeBtn = ui.button("النوع: JS (اضغط للتبديل لـ CSS)", false) {}
            typeBtn.setOnClickListener { type = if (type == "js") "css" else "js"; typeBtn.text = "النوع: " + type.uppercase() + " (اضغط للتبديل)" }
            box.addView(host); box.addView(ui.space(6)); box.addView(name); box.addView(ui.space(6)); box.addView(typeBtn); box.addView(ui.space(6)); box.addView(code); box.addView(ui.space(10))
            box.addView(ui.button("حفظ") {
                val hs = host.text.toString().trim(); val c = code.text.toString()
                if (hs.isNotEmpty() && c.isNotBlank() && c.length < 100000) { db.addScript(hs, name.text.toString().trim(), c, type); d.dismiss(); cur?.web?.reload(); toast("✅ اتحفظ") }
                else toast("اكتب الموقع والكود")
            })
        }
    }

    // ── مساعد Claude
    private fun showAi() {
        val key = Cfg.str(Cfg.APIKEY, "")
        ui.sheet { box, d ->
            box.addView(ui.titleView("🤖 مساعد Claude"))
            if (key.isBlank()) {
                box.addView(ui.infoView("ضيف مفتاح Anthropic API من الإعدادات الأول. الاستخدام بيتحاسب على حسابك."))
                box.addView(ui.button("فتح الإعدادات") { d.dismiss(); showSettings() })
                return@sheet
            }
            box.addView(ui.infoView("نص الصفحة بيتبعت لـ Anthropic عبر مفتاحك. لا تستخدمه في صفحات خاصة."))
            val out = TextView(this).apply { textSize = 14f; setTextColor(theme.text); setTextIsSelectable(true); setPadding(dp(6), dp(8), dp(6), dp(8)) }
            val model = Cfg.str(Cfg.MODEL, "claude-sonnet-5-5")
            fun run(prompt: String) {
                out.text = "⏳ ..."
                io.execute { val a = AiClient.ask(key, model, prompt); h.post { out.text = a } }
            }
            fun withText(js: String, build: (String) -> String) {
                cur?.web?.evaluateJavascript(js) { r ->
                    val s = try { JSONTokener(r).nextValue() as? String ?: "" } catch (_: Exception) { "" }
                    if (s.isBlank()) toast("مفيش نص") else run(build(s))
                }
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
            row.addView(ui.button("لخّص الصفحة", false) { withText(Scripts.TEXT) { "لخّص الصفحة التالية في نقاط واضحة بالعربية:\n\n$it" } }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(6) })
            row.addView(ui.button("اشرح المحدد", false) { withText(Scripts.SELECTION) { "اشرح النص التالي ببساطة بالعربية:\n\n$it" } }, LinearLayout.LayoutParams(0, WRAP, 1f))
            box.addView(row); box.addView(ui.space(6))
            val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
            row2.addView(ui.button("ترجم المحدد", false) { withText(Scripts.SELECTION) { "ترجم النص التالي إلى العربية (أو للإنجليزية لو كان عربيًا):\n\n$it" } }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(6) })
            row2.addView(ui.button("نسخ الرد", false) { copyText(out.text.toString()) }, LinearLayout.LayoutParams(0, WRAP, 1f))
            box.addView(row2); box.addView(ui.space(10))
            val q = ui.input("اسأل عن الصفحة أو أي سؤال...")
            box.addView(q); box.addView(ui.space(6))
            box.addView(ui.button("إرسال") {
                val ask = q.text.toString().trim()
                if (ask.isNotEmpty()) withText(Scripts.TEXT) { "السؤال: $ask\n\nنص الصفحة الحالية للسياق:\n$it\n\nأجب بالعربية باختصار." }
            })
            box.addView(out)
        }
    }

    // ── مراقبة الصفحات
    private fun fetchHash(url: String): String? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 12000; c.readTimeout = 20000; c.setRequestProperty("User-Agent", baseUa)
            val raw = c.inputStream.bufferedReader(Charsets.UTF_8).use { r -> val b = CharArray(400_000); val n = r.read(b); if (n > 0) String(b, 0, n) else "" }
            val text = raw.replace(Regex("(?is)<(script|style).*?</\\1>"), " ").replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")
            Hash.of(text.take(80000)).toString()
        } catch (_: Exception) { null } finally { c?.disconnect() }
    }

    private fun showWatches() {
        ui.sheet { box, d ->
            box.addView(ui.titleView("👁 مراقبة الصفحات"))
            box.addView(ui.infoView("بيفحص الصفحة ويقولك لو اتغيّر محتواها (سعر، إعلان حدث، توفّر...). الفحص يدوي أو عند فتح التطبيق."))
            val list = db.getWatches()
            for (w in list) {
                box.addView(ui.row(if (w.changed) "🔔" else "👁", w.label.ifBlank { hostOf(w.url) }, if (w.changed) "اتغيّرت!" else hostOf(w.url), null, pal(if (w.changed) 0 else 5)) {
                    d.dismiss(); io.execute { db.updateWatch(w.id, w.hash, false) }; newTab(w.url)
                })
                box.addView(ui.button("🗑 حذف", false) { d.dismiss(); io.execute { db.deleteWatch(w.id) }; h.postDelayed({ showWatches() }, 150) })
                box.addView(ui.space(6))
            }
            box.addView(ui.button("➕ راقب الصفحة الحالية") {
                val t = cur
                if (t != null && t.url.startsWith("http")) {
                    d.dismiss(); val u = t.url; val lb = t.title
                    io.execute { val hs = fetchHash(u); if (hs != null) { db.addWatch(u, lb, hs); h.post { toast("👁 بدأت المراقبة") } } else h.post { toast("تعذر قراءة الصفحة") } }
                } else toast("افتح صفحة عادية")
            })
            box.addView(ui.space(8))
            box.addView(ui.button("🔄 افحص الآن", false) {
                d.dismiss(); toast("⏳ جاري الفحص...")
                io.execute {
                    var changed = 0
                    for (w in db.getWatches().take(12)) { val hs = fetchHash(w.url) ?: continue; val ch = hs != w.hash; if (ch) changed++; db.updateWatch(w.id, hs, ch || w.changed) }
                    h.post { toast(if (changed > 0) "🔔 $changed صفحة اتغيّرت" else "مفيش تغييرات") }
                }
            })
        }
    }

    // ── جلسات الألعاب (حفظ/استرجاع الكوكيز)
    private fun showSessions() {
        val t = cur
        ui.sheet { box, d ->
            box.addView(ui.titleView("🎯 جلسات الألعاب"))
            box.addView(ui.infoView("يحفظ حالة دخولك لموقع اللعبة ويسترجعها بسرعة (محاولة ذكية، قد لا تنفع مع كل الألعاب)."))
            val all = try { JSONObject(Cfg.str("sessions", "{}")) } catch (_: Exception) { JSONObject() }
            for (name in all.keys().asSequence().toList()) {
                val o = all.getJSONObject(name)
                box.addView(ui.row("🎮", name, o.optString("host"), null, pal(3)) {
                    d.dismiss()
                    val cm = CookieManager.getInstance(); val base = "https://" + o.optString("host")
                    for (ck in o.optString("cookies").split("; ")) if (ck.contains("=")) cm.setCookie(base, ck + "; Max-Age=31536000; Path=/")
                    cm.flush(); toast("✅ تم الاسترجاع"); cur?.web?.loadUrl(base)
                })
                box.addView(ui.button("🗑 حذف: $name", false) { all.remove(name); Cfg.setStr("sessions", all.toString()); d.dismiss(); showSessions() })
                box.addView(ui.space(6))
            }
            box.addView(ui.button("💾 حفظ جلسة الموقع الحالي") {
                if (t == null || !t.url.startsWith("http")) { toast("افتح موقع اللعبة الأول"); return@button }
                val ck = CookieManager.getInstance().getCookie("https://" + t.host)
                if (ck.isNullOrBlank()) { toast("مفيش كوكيز للموقع ده"); return@button }
                d.dismiss()
                val inp = ui.input("اسم الجلسة", false, t.host)
                ui.sheet { b2, d2 ->
                    b2.addView(ui.titleView("اسم الجلسة")); b2.addView(inp); b2.addView(ui.space(10))
                    b2.addView(ui.button("حفظ") {
                        all.put(inp.text.toString().ifBlank { t.host }, JSONObject().put("host", t.host).put("cookies", ck))
                        Cfg.setStr("sessions", all.toString()); d2.dismiss(); toast("✅ اتحفظت")
                    })
                }
            })
        }
    }

    // ── بحث داخل الصفحة
    private fun openFind() {
        findBar.visibility = View.VISIBLE; findInput.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(findInput, 0)
    }

    private fun closeFind() {
        if (!::findBar.isInitialized || findBar.visibility != View.VISIBLE) return
        findBar.visibility = View.GONE; findInput.setText(""); cur?.web?.clearMatches()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(findInput.windowToken, 0)
        root.requestFocus()
    }

    // ── القراءة
    private fun toggleReader() {
        cur?.web?.evaluateJavascript(Scripts.READER) { r -> if (r == "\"none\"") toast("مفيش مقال واضح في الصفحة دي") }
    }

    private fun speakPage() {
        val engine = tts
        if (engine != null && ttsReady && engine.isSpeaking) { engine.stop(); toast("⏹ وقف القراءة"); return }
        cur?.web?.evaluateJavascript(Scripts.TEXT) { r ->
            val s = try { JSONTokener(r).nextValue() as? String ?: "" } catch (_: Exception) { "" }
            if (s.isBlank()) { toast("مفيش نص للقراءة"); return@evaluateJavascript }
            fun go(e: TextToSpeech) {
                e.language = Locale.getDefault()
                val chunks = s.chunked(3500)
                for ((i, c) in chunks.withIndex()) e.speak(c, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "mx$i")
                toast("🔊 بيقرأ... اضغط تاني للإيقاف")
            }
            if (tts == null) { tts = TextToSpeech(this) { st -> ttsReady = st == TextToSpeech.SUCCESS; if (ttsReady) tts?.let { go(it) } else toast("القراءة الصوتية غير متاحة") } }
            else if (ttsReady) go(tts!!)
        }
    }

    // ── الضغط المطوّل
    private fun onLongPress(tab: Tab): Boolean {
        val r = tab.web.hitTestResult
        val extra = r.extra ?: return false
        val isImg = r.type == WebView.HitTestResult.IMAGE_TYPE || r.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
        val isLink = r.type == WebView.HitTestResult.SRC_ANCHOR_TYPE
        if (!isImg && !isLink) return false
        val hh = hostOf(extra)
        ui.sheet { box, d ->
            box.addView(ui.titleView(if (isImg) "🖼 صورة" else "🔗 رابط"))
            box.addView(ui.infoView(extra.take(90)))
            if (isLink) box.addView(ui.row("🗂", "فتح في تبويب جديد", null, null, pal(0)) { d.dismiss(); newTab(extra) })
            if (isImg) {
                box.addView(ui.row("⬇", "حفظ الصورة", null, null, pal(2)) { d.dismiss(); startDownload(extra, tab.web.settings.userAgentString, null, null) })
                box.addView(ui.row("🗂", "فتح الصورة في تبويب", null, null, pal(0)) { d.dismiss(); newTab(extra) })
            }
            box.addView(ui.row("📋", "نسخ الرابط", null, null, pal(3)) { d.dismiss(); copyText(extra) })
            box.addView(ui.row("📤", "مشاركة", null, null, pal(5)) { d.dismiss(); shareUrl(extra) })
            if (hh.isNotEmpty()) box.addView(ui.row("🚫", "حجب $hh", "يتضاف لقاعدة البيانات", null, pal(1)) { d.dismiss(); AdBlocker.blockHost(hh); toast("🚫 اتحجب: $hh") })
        }
        return true
    }

    private fun shareUrl(url: String) {
        if (url.isBlank() || isInternal(url)) return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), "مشاركة الرابط"))
    }

    private fun copyText(s: String) {
        if (s.isBlank()) return
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("mx", s))
        toast("📋 اتنسخ")
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val REQ_FILE = 4101
        private const val REQ_STORAGE = 4102
        private const val REQ_VOICE = 4104
        private const val REQ_LOCK = 4105
        private const val REQ_WALL = 4106
        private const val REQ_PINRESET = 4120
        private const val REQ_BK_EXPORT = 4121
        private const val REQ_BK_IMPORT = 4122
        private val GAME_HOSTS = listOf("conqueronline.com", "playconqueronline.com")
        private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private val PAL = intArrayOf(
            Color.rgb(232, 30, 40), Color.rgb(255, 120, 40), Color.rgb(40, 190, 110), Color.rgb(60, 150, 255), Color.rgb(160, 90, 255),
            Color.rgb(230, 70, 150), Color.rgb(30, 190, 200), Color.rgb(248, 180, 40)
        )
    }
}

/** صورة معاينة تتثبّت من أعلى الصفحة (زي Brave) */
private class TopCropImage(ctx: Context) : ImageView(ctx) {
    init { scaleType = ScaleType.MATRIX }
    override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean { val ok = super.setFrame(l, t, r, b); fit(); return ok }
    override fun setImageBitmap(bm: Bitmap?) { super.setImageBitmap(bm); fit() }
    private fun fit() {
        val dr = drawable ?: return
        val dw = dr.intrinsicWidth; val dh = dr.intrinsicHeight
        if (dw <= 0 || dh <= 0 || width <= 0 || height <= 0) return
        val sc = maxOf(width.toFloat() / dw, height.toFloat() / dh)
        val m = android.graphics.Matrix()
        m.setScale(sc, sc); m.postTranslate((width - dw * sc) / 2f, 0f)
        imageMatrix = m
    }
}

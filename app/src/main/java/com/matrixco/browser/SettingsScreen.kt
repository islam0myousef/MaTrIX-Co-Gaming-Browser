package com.matrixco.browser

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.util.Locale

/** صفحة الإعدادات بملء الشاشة (بأسلوب Brave) — صفحات متداخلة بدون نوافذ */
class SettingsScreen(
    private val a: MainActivity,
    private val ui: UiKit,
    private val theme: MxTheme,
    private val host: FrameLayout,
    private val onClosed: () -> Unit
) {
    private val stack = ArrayList<String>()
    private val titleView: TextView
    private val body: FrameLayout
    private val overlay: LinearLayout

    private fun dp(v: Int) = ui.dp(v)
    private fun icon(type: String, color: Int = theme.accent2, size: Int = 22) = ImageView(a).apply { setImageDrawable(IconDrawable(type, color, dp(size))) }

    init {
        overlay = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.bg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            isClickable = true
        }
        val bar = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(34), dp(8), dp(6))
        }
        val backBtn = icon("back", theme.text, 24).apply {
            rotationY = 180f
            setPadding(dp(8), dp(8), dp(8), dp(8)); setOnClickListener { if (!back()) close() }
        }
        bar.addView(backBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        titleView = TextView(a).apply { textSize = 18f; setTextColor(theme.text); typeface = Typeface.DEFAULT_BOLD; setPadding(dp(6), 0, dp(6), 0) }
        bar.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val x = icon("close", theme.text, 22).apply { setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { close() } }
        bar.addView(x, LinearLayout.LayoutParams(dp(44), dp(44)))
        overlay.addView(bar)
        body = FrameLayout(a)
        overlay.addView(body, LinearLayout.LayoutParams(UiKit.MATCH, 0, 1f))
        host.addView(overlay, FrameLayout.LayoutParams(UiKit.MATCH, UiKit.MATCH))
    }

    fun open(page: String = "main") { push(page) }

    private fun push(page: String) { stack.add(page); render() }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.size - 1); render(); return true
    }

    fun close() { host.removeView(overlay); onClosed() }

    private fun render() {
        body.removeAllViews()
        val page = stack.last()
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(40)) }
        val title = when (page) {
            "main" -> "الإعدادات"; "privacy" -> "الدرع والخصوصية"; "spy" -> "كاشف التجسس"; "ai" -> "المساعد الذكي (Claude)"
            "appearance" -> "المظهر"; "search" -> "محرك البحث"; "storage" -> "المساحة والأداء"; "games" -> "الألعاب"
            "browsing" -> "التصفح"; "about" -> "حول MaTrIX Co"; "gxmods" -> "GX Mods"; "gxcontrol" -> "GX Control"; "filters" -> "عوامل تصفية المحتوى"; "iron" -> "الجدار الحديدي"; "lists" -> "قوائم الحجب"; "network" -> "الشبكة والـ VPN"; "gestures" -> "إيماءات التبويبات"; "keywords" -> "حجب بالكلمات"; "customrules" -> "عوامل تصفية مخصصة"; else -> "الإعدادات"
        }
        titleView.text = title
        when (page) {
            "main" -> main(col); "privacy" -> privacy(col); "spy" -> spy(col); "ai" -> ai(col); "appearance" -> appearance(col)
            "search" -> search(col); "storage" -> storage(col); "games" -> games(col); "browsing" -> browsing(col); "about" -> about(col)
            "gxmods" -> gxmods(col); "gxcontrol" -> gxcontrol(col)
            "iron" -> iron(col); "lists" -> blockLists(col); "network" -> network(col); "gestures" -> gestures(col); "filters" -> filters(col); "customrules" -> customRules(col); "keywords" -> keywordsPage(col)
        }
        val sv = ScrollView(a).apply { isVerticalScrollBarEnabled = false }
        sv.addView(col, ViewGroup.LayoutParams(UiKit.MATCH, ViewGroup.LayoutParams.WRAP_CONTENT))
        body.addView(sv, FrameLayout.LayoutParams(UiKit.MATCH, UiKit.MATCH))
    }

    // ───────── مكوّنات ─────────

    private fun section(col: LinearLayout, text: String) {
        col.addView(TextView(a).apply { this.text = text; textSize = 12f; setTextColor(theme.sub); setPadding(dp(8), dp(18), dp(8), dp(8)) })
    }

    private fun card(col: LinearLayout, rows: List<View>) {
        val c = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; background = Ui.round(theme.surface, dp(16).toFloat()) }
        for ((i, r) in rows.withIndex()) {
            c.addView(r, LinearLayout.LayoutParams(UiKit.MATCH, ViewGroup.LayoutParams.WRAP_CONTENT))
            if (i < rows.size - 1) c.addView(View(a).apply { setBackgroundColor(theme.bg) }, LinearLayout.LayoutParams(UiKit.MATCH, 1).apply { marginStart = dp(52) })
        }
        col.addView(c, LinearLayout.LayoutParams(UiKit.MATCH, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun rowBase(ic: String?, title: String, sub: String?, trailing: View?, click: (() -> Unit)?): View {
        val r = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            if (click != null) setOnClickListener { click() }
        }
        if (ic != null) r.addView(icon(ic), LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(16) })
        val t = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        t.addView(TextView(a).apply { text = title; textSize = 15f; setTextColor(theme.text) })
        if (sub != null) t.addView(TextView(a).apply { text = sub; textSize = 12f; setTextColor(theme.sub); setPadding(0, dp(2), 0, 0) })
        r.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (trailing != null) r.addView(trailing)
        return r
    }

    private fun nav(ic: String, title: String, sub: String? = null, go: () -> Unit): View =
        rowBase(ic, title, sub, icon("back", theme.sub, 18).apply { rotationY = 0f }, go)

    private fun action(ic: String, title: String, sub: String? = null, go: () -> Unit): View = rowBase(ic, title, sub, null, go)

    private fun toggle(ic: String, title: String, sub: String?, key: String, def: Boolean, after: ((Boolean) -> Unit)? = null): View {
        var state = Cfg.bool(key, def)
        val sw = ui.switchView(state)
        val r = rowBase(ic, title, sub, sw, null)
        r.setOnClickListener { state = !state; Cfg.setBool(key, state); ui.applySwitch(sw, state); AdBlocker.refreshRules(); after?.invoke(state) }
        return r
    }

    private fun radio(title: String, selected: Boolean, sub: String? = null, pick: () -> Unit): View {
        val mark: View = if (selected) icon("check", theme.accent, 20) else View(a).apply { layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)) }
        return rowBase(null, title, sub, mark, pick)
    }

    // ───────── الصفحات ─────────

    private fun main(col: LinearLayout) {
        section(col, "GX")
        card(col, listOf(
            nav("palette", "GX Mods", "خلفيات متحركة، ألوان تمييز، أصوات") { push("gxmods") },
            nav("bolt", "GX Control", "الذاكرة وتنويم التبويبات") { push("gxcontrol") },
            action("bolt", "Performance Lab", "قياس RAM وPSS وحالة التبويبات مباشرة") { close(); a.openPerformanceLab() }
        ))
        section(col, "الميزات")
        card(col, listOf(
            nav("shield_check", "الدرع والخصوصية", "إعلانات، متتبعات، أمان") { push("privacy") },
            nav("spy", "كاشف التجسس", "رصد البصمة والتسجيل والتتبع") { push("spy") },
            nav("sparkle", "المساعد الذكي (Claude)", "مفتاح API والموديل") { push("ai") }
        ))
        section(col, "عام")
        card(col, listOf(
            nav("search", "محرك البحث", a.engineLabel()) { push("search") },
            nav("palette", "المظهر", "الثيم والألوان") { push("appearance") },
            nav("globe", "التصفح", "HTTPS، الإيماءات، الصوت") { push("browsing") },
            nav("game", "الألعاب", "FPS وPing والزر العائم") { push("games") },
            nav("storage", "المساحة والأداء", "الكاش والصفحات المحفوظة") { push("storage") },
            action("download", "التنزيلات") { close(); a.openDownloadsManager() }
        ))
        section(col, "البيانات")
        card(col, listOf(
            action("trash", "مسح بيانات التصفح", "كوكيز، كاش، سجل") { a.confirmClear() },
            action("share", "نسخ احتياطي", "ملف فيه إعداداتك ومفضلتك وفلاترك وكلماتك") { a.exportBackup() },
            action("download", "استرجاع نسخة", "من ملف نسخة احتياطية") { close(); a.importBackup() }
        ))
        section(col, "المتصفح الافتراضي")
        card(col, listOf(action("globe", "اجعله المتصفح الافتراضي", if (a.isDefaultBrowser()) "✓ هو الافتراضي حاليًا" else "الروابط من التطبيقات الأخرى تفتح هنا") { a.askDefaultBrowser() }))
        section(col, "حول")
        card(col, listOf(nav("info", "حول MaTrIX Co", "الإصدار " + a.appVersion()) { push("about") }))
    }

    private fun privacy(col: LinearLayout) {
        section(col, "الدرع")
        card(col, listOf(
            toggle("shield_check", "تفعيل الدرع", "المفتاح الرئيسي لكل الحماية", Cfg.SHIELD, true) { a.reloadCurrent() },
            toggle("t:AD", "حجب الإعلانات", null, Cfg.ADS, true) { a.reloadCurrent() },
            toggle("eye", "حجب المتتبعات", null, Cfg.TRK, true) { a.reloadCurrent() },
            toggle("t:☠", "النطاقات الخبيثة + تحذير الصفحات", null, Cfg.MAL, true) { a.reloadCurrent() },
            toggle("t:◎", "ودجتس السوشيال", "قد تكسر تسجيل الدخول بحسابات خارجية", Cfg.SOC, false) { a.reloadCurrent() },
            toggle("close", "فلاتر الإزعاج", "كوكيز، نوافذ منبثقة، جدار مانع الإعلانات", Cfg.ANNOY, true) { a.reloadCurrent() }
        ))
        section(col, "الأمان")
        card(col, listOf(
            toggle("lock", "HTTPS أولًا", "يفتح كل موقع بتشفير ويرجع HTTP لو مش مدعوم", Cfg.HTTPS_ONLY, true),
            toggle("shield", "منع المحتوى المختلط", "http داخل صفحات https", Cfg.MIXED, true) { a.refreshHostSettings() },
            toggle("shield_check", "Safe Browsing من جوجل", "حماية إضافية من التصيّد", Cfg.SAFEB, true),
            toggle("download", "تحذير الملفات الخطرة", "apk / exe / jar ...", Cfg.DLWARN, true)
        ))
        section(col, "الخصوصية")
        card(col, listOf(
            toggle("incognito", "تشويش بصمة المتصفح", "Canvas ومعلومات الجهاز", Cfg.FP, true),
            toggle("t:🍪", "كوكيز الطرف الثالث", "الأفضل تفضل مقفولة", Cfg.TPCOOK, false) { a.applyThirdPartyCookies(it) },
            toggle("trash", "مسح تلقائي عند الخروج", "كوكيز + كاش + سجل", Cfg.AUTOCLEAR, false),
            toggle("lock", "قفل التطبيق", "بصمة أو PIN الجهاز عند الرجوع", Cfg.APPLOCK, false)
        ))
        section(col, "الجدار الحديدي")
        card(col, listOf(
            nav("shield", "الجدار الحديدي", "تقييم الخطر • سجل المنع • القائمة البيضاء") { push("iron") },
            nav("globe", "الشبكة والـ VPN", "Tor • بروكسي • DNS مشفّر • بدائل VPN مجانية") { push("network") }
        ))
        section(col, "قفل الإعدادات")
        run {
            val on = PinLock.enabled()
            val sw = ui.switchView(on)
            val r = rowBase("lock", "قفل الإعدادات بـ PIN", if (on) "مفعّل — مطلوب لفتح الإعدادات وتغيير الحماية" else "يمنع أي حد من تعديل الفلاتر والحماية", sw, null)
            r.setOnClickListener {
                if (!PinLock.enabled()) a.pinLock.setup { render() }
                else a.pinLock.ask("أدخل الـ PIN لإيقاف القفل") { a.pinLock.disable(); a.toastMsg("اتقفل القفل"); render() }
            }
            val items = arrayListOf<View>(r)
            if (on) {
                items.add(action("lock", "تغيير الـ PIN", "بيطلب الـ PIN الحالي الأول") {
                    a.pinLock.ask("أدخل الـ PIN الحالي") { a.pinLock.setup { render() } }
                })
                if (PinLock.bioAvailable()) items.add(toggle("lock", "فتح بالبصمة", "بديل للـ PIN لو بصمتك متسجّلة في الجهاز", PinLock.BIO, false))
            }
            card(col, items)
        }
        section(col, "القوائم")
        val names = RuleUpdater.activeLists(a)
        card(col, listOf(
            nav("shield_check", "عوامل تصفية المحتوى", "قوائم الإزعاج واللغات + قوائم وقواعد مخصصة") { push("filters") },
            action("reload", "تحديث القوائم الآن", RuleUpdater.getLastUpdate(a)?.let { "آخر تحديث: $it" } ?: "لم يتم بعد") { a.runUpdate(a.currentTab()) },
            nav("info", "قوائم الحجب (${names.size} شغّالة)", if (names.isEmpty()) "اضغط لاختيار القوائم" else names.joinToString(" • ")) { push("lists") }
        ))
    }

    // ───────── عوامل تصفية المحتوى (بأسلوب Brave) ─────────

    private fun listRow(l: FilterList, deletable: Boolean): View {
        val sw = ui.switchView(FilterEngine.isOn(l))
        val r = rowBase(null, l.title, l.source + "  •  " + FilterEngine.status(a, l), sw, null) as LinearLayout
        val sub = (r.getChildAt(0) as LinearLayout).getChildAt(1) as TextView
        fun setSub() { sub.text = l.source + "  •  " + FilterEngine.status(a, l) }
        r.setOnClickListener {
            val v = !FilterEngine.isOn(l)
            FilterEngine.setOn(l, v); ui.applySwitch(sw, v)
            if (v) {
                sub.text = l.source + "  •  ⏳ جاري التحميل..."
                FilterEngine.loadAsync(a, l) { ok -> setSub(); if (ok) a.reloadCurrent() }
            } else FilterEngine.disableAsync(a, l) { setSub(); a.reloadCurrent() }
        }
        if (deletable) r.setOnLongClickListener {
            ui.sheet { box, d ->
                box.addView(ui.titleView("حذف \"" + l.title + "\"؟"))
                box.addView(ui.button("حذف") { d.dismiss(); FilterEngine.removeCustom(a, l); render() })
            }
            true
        }
        return r
    }

    private fun filters(col: LinearLayout) {
        FilterEngine.loadPending(a) { if (stack.lastOrNull() == "filters") render() }
        section(col, "قوائم التصفية المخصصة")
        card(col, listOf(
            nav("code", "إنشاء عوامل تصفية مخصصة", "اكتب قواعدك بنفسك") { push("customrules") },
            nav("plus", "إضافة قائمة التصفية المخصصة", "من رابط قائمة Adblock") { addCustomList() },
            nav("shield", "حجب بالكلمات المفتاحية", "أي صفحة أو بحث فيه الكلمة بيتحجب") { push("keywords") }
        ))
        val custom = FilterEngine.customLists()
        if (custom.isNotEmpty()) {
            section(col, "قوائمك (اضغط مطولًا للحذف)")
            card(col, custom.map { listRow(it, true) })
        }
        section(col, "ترشيح القوائم")
        col.addView(TextView(a).apply {
            text = "قوائم المجتمع الشهيرة الإضافية. لاحظ أن تمكين عدد كبير للغاية من عوامل التصفية سيؤدي إلى تقليل سرعات التصفح."
            textSize = 13f; setTextColor(theme.sub); setPadding(dp(8), 0, dp(8), dp(10)); setLineSpacing(0f, 1.15f)
        })
        card(col, listOf(action("reload", "تحديث القوائم المفعّلة الآن", "بيتحدثوا تلقائيًا كل 24 ساعة") {
            a.toastMsg("⏳ جاري تحديث القوائم...")
            FilterEngine.refreshAsync(a) { ok, total -> a.toastMsg("تم تحديث $ok من $total"); if (stack.lastOrNull() == "filters") render() }
        }))
        section(col, "عامة")
        card(col, FilterEngine.catalog.filter { it.group == 0 }.map { listRow(it, false) })
        card(col, listOf(toggle("shield", "تحليل نص الصفحة (إضافي)", "بيحجب مواقع مش في القوائم — اقفله لو عايز القوائم بس", AdultGuard.SCAN_KEY, true)))
        card(col, listOf(action("trash", "مسح المواقع المحجوبة بالتحليل", "اتحجب " + FilterEngine.learnedCount(a) + " موقع تلقائيًا") {
            FilterEngine.clearLearned(a); a.toastMsg("تم المسح"); render()
        }))
        section(col, "حسب اللغة")
        card(col, FilterEngine.catalog.filter { it.group == 1 }.map { listRow(it, false) })
    }

    private fun keywordsPage(col: LinearLayout) {
        col.addView(TextView(a).apply {
            text = "اكتب كلمة أو جملة في كل سطر. أي صفحة فيها الكلمة في العنوان أو الرابط (أو 3 مرات في النص)، أو في كلمات البحث، بتتحجب.\nشغّال لأي موضوع تحبه، مش بس الإباحية."
            textSize = 13f; setTextColor(theme.sub); setPadding(dp(8), dp(10), dp(8), dp(10)); setLineSpacing(0f, 1.2f)
        })
        val et = ui.input("كلمة في كل سطر", true, Cfg.str(AdultGuard.KEY, ""))
        col.addView(et); col.addView(ui.space(12))
        col.addView(ui.button("حفظ") { Cfg.setStr(AdultGuard.KEY, et.text.toString()); a.toastMsg("✅ اتحفظت (" + AdultGuard.keywords().size + " كلمة)") })
    }

    private fun addCustomList() {
        ui.sheet { box, d ->
            box.addView(ui.titleView("إضافة قائمة تصفية"))
            val name = ui.input("اسم القائمة")
            val url = ui.input("https://example.com/list.txt")
            box.addView(name); box.addView(ui.space(8)); box.addView(url); box.addView(ui.space(12))
            box.addView(ui.button("إضافة") {
                val u = url.text.toString().trim()
                if (!u.startsWith("https://") && !u.startsWith("http://")) { a.toastMsg("الرابط لازم يبدأ بـ https://"); return@button }
                val l = FilterEngine.addCustom(name.text.toString().trim().ifEmpty { u.substringAfter("://").substringBefore('/') }, u)
                FilterEngine.setOn(l, true)
                d.dismiss(); render()
                a.toastMsg("⏳ جاري التحميل...")
                FilterEngine.loadAsync(a, l) { ok -> if (stack.lastOrNull() == "filters") render(); a.toastMsg(if (ok) "✅ تمت الإضافة" else "⚠ فشل التحميل"); if (ok) a.reloadCurrent() }
            })
        }
    }

    private fun customRules(col: LinearLayout) {
        col.addView(TextView(a).apply {
            text = "قاعدة في كل سطر:\n• ||ads.example.com^ ← يحجب النطاق\n• example.com ← يحجب النطاق\n• example.com##.popup ← يخفي العنصر في الموقع\n• ##.cookie-banner ← يخفي العنصر في كل المواقع\nالقواعد المعقدة (:has-text وغيرها) بتتجاهل."
            textSize = 13f; setTextColor(theme.sub); setPadding(dp(8), dp(10), dp(8), dp(10)); setLineSpacing(0f, 1.2f)
        })
        val et = ui.input("||ads.example.com^\nexample.com##.popup", true, Cfg.str(FilterEngine.RULES_KEY, ""))
        col.addView(et); col.addView(ui.space(12))
        col.addView(ui.button("حفظ وتطبيق") {
            Cfg.setStr(FilterEngine.RULES_KEY, et.text.toString())
            FilterEngine.applyCustomRulesAsync(a) { n -> a.toastMsg(if (n >= 0) "✅ اتطبق ($n قاعدة)" else "⚠ حصل خطأ"); a.reloadCurrent() }
        })
    }

    // ───────── قوائم الحجب (اختيار + مستويات + ترشيح) ─────────

    private fun blockLists(col: LinearLayout) {
        val lvl = RuleUpdater.currentLevel()
        val on = RuleUpdater.enabledSources()
        section(col, "مستوى الحماية (اختيار سريع)")
        val presets = listOf(
            Triple(1, "منخفض — خفيف وسريع", "قوائم أساسية قليلة، أقل استهلاك للذاكرة والبطارية"),
            Triple(2, "متوسط — الموصى به", "توازن كويس بين قوة الحجب والسرعة"),
            Triple(3, "عالي — أقوى حجب", "كل القوائم القوية: بيحجب أكتر بس بياخد ذاكرة ووقت تحديث أطول")
        )
        card(col, presets.map { (l, t, d) ->
            action("shield", t, (if (lvl == l) "✓ المستوى الحالي — " else "") + d + " (" + RuleUpdater.sources.count { it.level <= l } + " قائمة)") { RuleUpdater.applyPreset(l); render() }
        })
        if (lvl == 0) col.addView(TextView(a).apply { text = "اختيارك الحالي مخصص (غيّرت قوائم بإيدك)."; textSize = 12f; setTextColor(theme.sub); setPadding(dp(8), dp(4), dp(8), dp(4)) })
        card(col, listOf(action("reload", "تطبيق وتحديث الآن", "بيحمّل القوائم المختارة ويبني قاعدة الحجب (محتاج نت، ممكن ياخد دقيقة)") {
            a.toastMsg("⏳ بحمّل ${on.size} قايمة...")
            RuleUpdater.updateNow(a) { r -> a.toastMsg(r.message); if (stack.lastOrNull() == "lists") render() }
        }))
        val groups = listOf(1 to "أساسية", 2 to "موصى بها", 3 to "قوية (للمستوى العالي)", 9 to "اختيارية")
        for ((g, title) in groups) {
            val items = RuleUpdater.sources.filter { it.level == g }
            if (items.isEmpty()) continue
            section(col, title)
            card(col, items.map { s ->
                val st = RuleUpdater.statusOf(a, s.id)
                val stT = when {
                    st == null -> if (RuleUpdater.isOn(s)) "لسه ما اتحمّلتش" else "مقفولة"
                    st.startsWith("ok:") -> "✓ " + st.removePrefix("ok:") + " نطاق"
                    else -> "⚠ فشل (" + st.removePrefix("fail:") + ")"
                }
                toggle("shield", s.name, s.note + "  •  " + stT, "bl_on_" + s.id, s.level <= 2) { Cfg.setInt("bl_level", 0) }
            })
        }
        section(col, "قوائم تانية")
        card(col, listOf(nav("plus", "إضافة قائمة من رابط", "أي قائمة Adblock أو hosts بتحبها") { push("filters") }))
        col.addView(TextView(a).apply {
            text = "بعد ما تغيّر الاختيار اضغط \"تطبيق وتحديث الآن\" عشان الحجب يتغيّر فعليًا. الأحدث بتتحمّل كل 24 ساعة تلقائيًا."
            textSize = 12f; setTextColor(theme.sub); setPadding(dp(8), dp(8), dp(8), dp(4)); setLineSpacing(0f, 1.2f)
        })
    }

    // ───────── الشبكة والـ VPN (بدائل مجانية) ─────────

    private fun network(col: LinearLayout) {
        val vpn = NetGuard.vpnActive(a)
        card(col, listOf(action("globe", if (vpn) "✓ فيه VPN شغّال على الجهاز" else "مفيش VPN شغّال", if (NetGuard.rule() != null) "المتصفح بيستخدم بروكسي: " + NetGuard.rule() else "الاتصال مباشر") { render() }))
        col.addView(TextView(a).apply {
            text = "المتصفح نفسه مبيشغّلش VPN (ده محتاج سيرفرات)، لكن بيربطك بأقوى البدائل المجانية وبيوفّر Tor وبروكسي وDNS مشفّر."
            textSize = 12f; setTextColor(theme.sub); setPadding(dp(8), dp(8), dp(8), dp(4)); setLineSpacing(0f, 1.2f)
        })
        section(col, "VPN مجاني (تطبيقات موثوقة)")
        card(col, NetGuard.vpnApps.map { app -> action("globe", app.name, app.note) { NetGuard.openOrStore(a, app.pkg) } })
        section(col, "Tor (تصفح مجهول)")
        card(col, listOf(
            toggle("incognito", "التصفح عبر Tor", if (NetGuard.proxySupported()) "بيمرّر تصفح المتصفح على Orbot (127.0.0.1:8118) — شغّل Orbot الأول" else "إصدار WebView عندك مش بيدعم البروكسي", NetGuard.TOR, false) { NetGuard.apply(); a.reloadCurrent() },
            action("globe", "فتح / تثبيت Orbot", "تطبيق Tor الرسمي") { NetGuard.openOrStore(a, "org.torproject.android") }
        ))
        section(col, "بروكسي مخصص")
        card(col, listOf(action("code", "عنوان البروكسي", if (Cfg.str(NetGuard.CUSTOM, "").isBlank()) "مفيش (اضغط للإضافة)" else Cfg.str(NetGuard.CUSTOM, "")) {
            ui.sheet { box, d ->
                box.addView(ui.titleView("بروكسي مخصص"))
                val inp = ui.input("host:port أو socks5://host:port", false, Cfg.str(NetGuard.CUSTOM, ""))
                box.addView(inp); box.addView(ui.space(12))
                box.addView(ui.button("حفظ وتطبيق") { Cfg.setStr(NetGuard.CUSTOM, inp.text.toString().trim()); NetGuard.apply(); d.dismiss(); a.reloadCurrent(); render() })
                box.addView(ui.button("إلغاء البروكسي", false) { Cfg.setStr(NetGuard.CUSTOM, ""); NetGuard.apply(); d.dismiss(); a.reloadCurrent(); render() })
            }
        }))
        section(col, "DNS مشفّر (Private DNS)")
        col.addView(TextView(a).apply {
            text = "اضغط على أي واحد ينسخ العنوان، وبعدها: إعدادات أندرويد ← الشبكة ← DNS الخاص ← اسم مضيف لمزود DNS، والصقه. بيشفّر طلبات DNS لكل التطبيقات."
            textSize = 12f; setTextColor(theme.sub); setPadding(dp(8), dp(4), dp(8), dp(8)); setLineSpacing(0f, 1.2f)
        })
        card(col, NetGuard.dnsHosts.map { (h, note) ->
            action("lock", h, note) {
                try { (a.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("dns", h)) } catch (_: Exception) {}
                a.toastMsg("اتنسخ: $h")
            }
        })
        card(col, listOf(action("settings", "فتح إعدادات الشبكة", "عشان تلاقي DNS الخاص") {
            try { a.startActivity(android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)) } catch (_: Exception) { a.toastMsg("افتح إعدادات أندرويد ← الشبكة") }
        }))
    }

    // ───────── الجدار الحديدي ─────────

    private fun iron(col: LinearLayout) {
        val db = ShieldDatabase.getInstance(a)
        card(col, listOf(toggle("shield", "الجدار الحديدي", "منع تلقائي لأي صفحة فيها خطر — من غير سؤال ومن غير زر \"كمل\"", IronGuard.ON, true) { render() }))
        section(col, "مستوى الحساسية")
        val levels = listOf("حديدي — أي شك بيتمنع (عتبة 5)", "عالي (عتبة 15)", "متوازن (عتبة 35)")
        card(col, levels.mapIndexed { i, n ->
            action("shield", n, if (Cfg.int(IronGuard.LEVEL, 0) == i) "✓ المستوى الحالي" else null) { Cfg.setInt(IronGuard.LEVEL, i); render() }
        })
        col.addView(TextView(a).apply {
            text = "الدرجة من 0 لـ 100، ولو وصلت للعتبة الصفحة بتتمنع. بيقيّم: غياب HTTPS، العنوان IP، الأسماء المقلّدة وpunycode، الروابط المريبة، التحويلات الكتير، قوائم التصيد، شهادة الأمان، النماذج المنتحلة، الإطارات المخفية، والسكريبتات المشفّرة."
            textSize = 12f; setTextColor(theme.sub); setPadding(dp(8), dp(8), dp(8), dp(4)); setLineSpacing(0f, 1.2f)
        })
        section(col, "القائمة البيضاء (بتتعدّل من هنا بس)")
        val allow = db.riskAllowList()
        val rows = ArrayList<View>()
        rows.add(action("plus", "إضافة موقع", "بيطلب الـ PIN لو القفل شغّال") {
            a.pinLock.ask {
                ui.sheet { box, d ->
                    box.addView(ui.titleView("إضافة للقائمة البيضاء"))
                    val inp = ui.input("example.com")
                    box.addView(inp); box.addView(ui.space(12))
                    box.addView(ui.button("إضافة") {
                        val h = inp.text.toString().trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/')
                        if (h.contains('.')) { db.riskAllowAdd(h); d.dismiss(); render() } else a.toastMsg("اكتب اسم موقع صحيح")
                    })
                }
            }
        })
        for (h in allow) rows.add(action("check", h, "اضغط للإزالة") {
            a.pinLock.ask { db.riskAllowRemove(h); a.toastMsg("اتشال: $h"); render() }
        })
        card(col, rows)
        section(col, "سجل المنع")
        val log = db.riskLog(40)
        val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US)
        if (log.isEmpty()) col.addView(TextView(a).apply { text = "مفيش حاجة اتمنعت لسه."; textSize = 13f; setTextColor(theme.sub); setPadding(dp(8), dp(4), dp(8), dp(8)) })
        else {
            card(col, log.map { r ->
                action("shield", r[1], r[2] + "  •  " + r[3] + "/100  •  " + fmt.format(java.util.Date(r[0].toLong()))) {
                    ui.sheet { box, d ->
                        box.addView(ui.titleView(r[1]))
                        box.addView(TextView(a).apply { text = r[2] + "\nالدرجة: " + r[3] + "/100\n" + r[4]; textSize = 12f; setTextColor(theme.sub); setPadding(0, dp(4), 0, dp(12)) })
                        box.addView(ui.button("أضف للقائمة البيضاء") { d.dismiss(); a.pinLock.ask { db.riskAllowAdd(r[1]); a.toastMsg("اتضاف: " + r[1]); render() } })
                    }
                }
            })
            card(col, listOf(action("trash", "مسح السجل") { db.clearRiskLog(); render() }))
        }
    }

    // ───────── إيماءات التبويبات ─────────

    private val gestureOpts = listOf("close" to "إغلاق", "closeclear" to "إغلاق ومسح بيانات الموقع", "pin" to "تثبيت", "suspend" to "تجميد", "gamemode" to "تفعيل وضع الألعاب", "none" to "بدون إجراء")

    private fun gestures(col: LinearLayout) {
        val dirs = listOf(Triple("left", "شمال", "close"), Triple("right", "يمين", "pin"), Triple("up", "فوق", "closeclear"), Triple("down", "تحت", "suspend"))
        card(col, dirs.map { (k, label, def) ->
            val cur = Cfg.str("tg_$k", def)
            action("tabs", "السحب $label", gestureOpts.firstOrNull { it.first == cur }?.second ?: "") {
                ui.sheet { box, d ->
                    box.addView(ui.titleView("السحب $label"))
                    for ((id, name) in gestureOpts) box.addView(ui.button(name, id != cur) { Cfg.setStr("tg_$k", id); d.dismiss(); render() })
                }
            }
        })
        card(col, listOf(action("reload", "استعادة الافتراضي") { for (t in dirs) Cfg.setStr("tg_" + t.first, t.third); render() }))
        col.addView(TextView(a).apply {
            text = "السحب شمال/يمين بيشتغل على الكارت كله، وفوق/تحت من شريط عنوان الكارت (عشان ما يتعارضش مع سكرول القايمة). التاب المثبّت مبيتقفلش بالسحب ولا بـ \"إغلاق الكل\". بعد أي إغلاق فيه \"تراجع\" 5 ثواني."
            textSize = 12f; setTextColor(theme.sub); setPadding(dp(8), dp(8), dp(8), dp(4)); setLineSpacing(0f, 1.2f)
        })
    }

    private fun spy(col: LinearLayout) {
        card(col, listOf(toggle("spy", "تفعيل كاشف التجسس", "≥90% حجب تلقائي، 50–89% يسألك", Cfg.SPY, true) { a.reloadCurrent() }))
        section(col, "كيف يعمل؟")
        col.addView(TextView(a).apply {
            text = "بيراقب الصفحة وقت التحميل: بصمة Canvas/Audio، WebRTC، قراءة الحافظة، إطارات خفية، تسجيل الجلسات، مراقبة الكيبورد، وطلبات الموقع والكاميرا. كل حالة ليها نسبة ثقة بتتحسب من نوع السلوك + هل الجهة خارجية + هل هي في قوائم الحجب. تقدير مش يقين."
            textSize = 13f; setTextColor(theme.sub); setPadding(dp(8), 0, dp(8), dp(8))
        })
        section(col, "السجل")
        card(col, listOf(action("history", "سجل الرصد", "كل الحالات والقرارات") { close(); a.openInternalPage("/spy") }))
    }

    private fun ai(col: LinearLayout) {
        col.addView(TextView(a).apply { text = "بيستخدم مفتاحك الشخصي من Anthropic. نص الصفحة بيتبعت للخدمة وقت الاستخدام، والتكلفة على حسابك."; textSize = 12f; setTextColor(theme.sub); setPadding(dp(8), dp(10), dp(8), dp(10)) })
        val key: EditText = ui.input("sk-ant-...", false, Cfg.str(Cfg.APIKEY, ""))
        val model: EditText = ui.input("اسم الموديل", false, Cfg.str(Cfg.MODEL, "claude-sonnet-5-5"))
        col.addView(key); col.addView(ui.space(8)); col.addView(model); col.addView(ui.space(12))
        col.addView(ui.button("حفظ") {
            Cfg.setStr(Cfg.APIKEY, key.text.toString().trim()); Cfg.setStr(Cfg.MODEL, model.text.toString().trim().ifEmpty { "claude-sonnet-5-5" }); a.toastMsg("اتحفظ")
        })
    }

    private fun appearance(col: LinearLayout) {
        val cur = Cfg.str(Cfg.THEME, "auto")
        val rows = ArrayList<View>()
        for (th in Themes.picker) {
            val dot = TextView(a).apply { background = Ui.oval(th.accent) }
            val r = rowBase(null, if (th.id == "auto") "GX Neon (الافتراضي)" else th.name, null, if (th.id == cur) icon("check", theme.accent, 20) else null) { a.applyTheme(th.id) }
            (r as LinearLayout).addView(dot, 0, LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(14) })
            rows.add(r)
        }
        card(col, rows)
    }

    private fun search(col: LinearLayout) {
        val cur = a.engineLabel()
        card(col, listOf("Google", "DuckDuckGo", "Bing").map { e -> radio(e, e == cur) { Cfg.setStr(Cfg.ENGINE, e); render() } })
    }

    private fun storage(col: LinearLayout) {
        val info = TextView(a).apply { text = "جاري الحساب..."; textSize = 13f; setTextColor(theme.sub); setPadding(dp(8), dp(10), dp(8), dp(4)) }
        col.addView(info)
        a.computeStorageAsync { s -> info.text = "الإجمالي " + Storage.fmt(s.total) + "\nكاش " + Storage.fmt(s.cache) + " • بيانات مواقع " + Storage.fmt(s.web) + "\nأوفلاين " + Storage.fmt(s.offline) + " • قاعدة الدرع " + Storage.fmt(s.db) }
        section(col, "حد الكاش (تنظيف تلقائي)")
        val cur = Cfg.int(Cfg.CACHE_MB, 150)
        card(col, listOf(50, 100, 150, 300).map { mb -> radio("$mb MB", mb == cur) { Cfg.setInt(Cfg.CACHE_MB, mb); render() } })
        section(col, "أدوات")
        card(col, listOf(
            action("bolt", "فتح Performance Lab", "قياسات مباشرة لاستهلاك التطبيق والذاكرة") { close(); a.openPerformanceLab() },
            action("trash", "تنظيف الكاش الآن") { a.cleanCacheNow(a.currentTab()) },
            action("t:📴", "الصفحات المحفوظة") { close(); a.openInternalPage("/offline") },
            toggle("t:🖼", "توفير الباقة (بدون صور)", null, Cfg.DATASAVER, false) { a.applyDataSaver(it) }
        ))
    }

    private fun games(col: LinearLayout) {
        card(col, listOf(
            toggle("game", "عدّاد FPS وTCP", "معدل الإطارات ووقت اتصال TCP للمضيف", Cfg.FPS, true),
            toggle("bolt", "الزر العائم", "أدوات سريعة فوق الصفحة", Cfg.SHOWFAB, true) { a.applyFabVisibility(it) }
        ))
    }

    private fun browsing(col: LinearLayout) {
        card(col, listOf(
            nav("tabs", "إيماءات التبويبات", "سحب الكارت لإغلاق/تثبيت/تجميد") { push("gestures") },
            toggle("back", "إيماءات الحواف", "اسحب من الحافة للرجوع/التقدّم", Cfg.GESTURES, true),
            toggle("t:♪", "تكملة الصوت في الخلفية", "تجريبي — يعتمد على الجهاز", Cfg.BGAUDIO, false),
            toggle("moon", "تعتيم المواقع", "وضع ليلي لأي موقع", Cfg.DARK, false) { a.applyDarkPages(it) }
        ))
    }


    private fun gxmods(col: LinearLayout) {
        section(col, "خلفية الصفحة الرئيسية")
        val cw = Cfg.str(Cfg.WALL, "gx")
        val opts = listOf("gx" to "MaTrIX GX Neon (الافتراضية)", "none" to "بدون", "grid" to "Neon Grid (متحركة)", "embers" to "Embers — جمرات (متحركة)", "matrix" to "Matrix (متحركة)", "aurora" to "Aurora — تدرّج متحرك", "custom" to "صورة من المعرض")
        card(col, opts.map { (k, name) ->
            radio(name, k == cw, if (k == "custom") "هتبقى كمان خلفية صفحة الساعة والطبيعة" else null) {
                if (k == "custom") a.pickWallpaper() else { Cfg.setStr(Cfg.WALL, k); a.reloadHomeIfShown(); render() }
            }
        })
        section(col, "شكل خط الساعة")
        val cf = Cfg.str(Cfg.CLOCK_FONT, "oswald")
        val fonts = listOf("oswald" to "Oswald ExtraLight — رفيع وطويل", "teko" to "Teko Light — رفيع مرصوص", "robotoc" to "Roboto Condensed Light", "bebas" to "Bebas Neue — عريض مرصوص", "anton" to "Anton — عريض جدًا", "montserrat" to "Montserrat Thin — أنيق", "digital" to "VT323 — ساعة ديجيتال / LED حقيقية", "bold" to "Inter Black — عريض وكبير بلمسة زجاجية (iOS)")
        card(col, fonts.map { (k, name) ->
            radio(name, k == cf) { Cfg.setStr(Cfg.CLOCK_FONT, k); a.reloadHomeIfShown(); render() }
        })
        section(col, "لون التمييز")
        val colors = listOf(Color.rgb(250, 30, 78), Color.rgb(255, 140, 30), Color.rgb(250, 200, 40), Color.rgb(40, 210, 110), Color.rgb(30, 200, 230), Color.rgb(60, 130, 255), Color.rgb(150, 80, 255), Color.rgb(240, 80, 200))
        val sw = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_LTR; setPadding(dp(4), dp(6), dp(4), dp(6)) }
        val curAcc = Cfg.int(Cfg.ACCENT, 0)
        for (c in colors) {
            val dot = TextView(a).apply {
                gravity = Gravity.CENTER; setTextColor(Color.WHITE); text = if (c == curAcc) "✓" else ""
                background = Ui.round(c, dp(18).toFloat(), if (c == curAcc) Color.WHITE else 0, dp(2))
                setOnClickListener { Fx.tap(it); Cfg.setInt(Cfg.ACCENT, c); a.applyTheme(Cfg.str(Cfg.THEME, "auto")) }
            }
            sw.addView(dot, LinearLayout.LayoutParams(0, dp(38), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        col.addView(sw)
        card(col, listOf(action("reload", "لون الثيم الأصلي", "إلغاء اللون المخصص") { Cfg.setInt(Cfg.ACCENT, 0); a.applyTheme(Cfg.str(Cfg.THEME, "auto")) }))
        section(col, "الأصوات")
        val cs = Cfg.str(Cfg.SOUND, "off")
        card(col, listOf("off" to "بدون صوت", "pulse" to "Pulse — نبضات ناعمة", "retro" to "Retro — أسلوب ألعاب قديمة").map { (k, name) ->
            radio(name, k == cs) { Cfg.setStr(Cfg.SOUND, k); Fx.play("open"); render() }
        })
        card(col, listOf(toggle("t:📳", "الاهتزاز عند اللمس", "في الشريط السفلي وزر GX", Cfg.HAPTIC, true)))
        section(col, "الصفحة الرئيسية")
        card(col, listOf(toggle("globe", "GX Corner", "أخبار الألعاب + الإصدارات القادمة + العروض المجانية", Cfg.GXCORNER, true) { a.reloadHomeIfShown() }))
    }

    private fun gxcontrol(col: LinearLayout) {
        card(col, listOf(
            toggle("storage", "محدِّد الذاكرة (RAM Limiter)", "ينوّم التبويبات الخلفية لما المتاح من RAM يقل عن 15%", Cfg.RAMLIM, false),
            toggle("bolt", "عدّاد FPS وTCP", "داخل وضع اللعب", Cfg.FPS, true)
        ))
        section(col, "تنويم التبويبات الخاملة بعد")
        val cur = Cfg.int(Cfg.TABSLEEP, 10)
        card(col, listOf(0 to "متوقف", 5 to "5 دقايق", 10 to "10 دقايق", 30 to "30 دقيقة", 60 to "ساعة").map { (m, name) ->
            radio(name, m == cur) { Cfg.setInt(Cfg.TABSLEEP, m); render() }
        })
        col.addView(TextView(a).apply {
            text = "التبويب النايم بيفضل ظاهر في القائمة وبيتحمّل من جديد لما تفتحه. محدِّد CPU وسرعة الشبكة (اللي في نسخة الكمبيوتر) مش ممكنين داخل متصفح أندرويد."
            textSize = 12f; setTextColor(theme.sub); setPadding(dp(8), dp(12), dp(8), dp(8))
        })
    }

    private fun about(col: LinearLayout) {
        col.addView(TextView(a).apply {
            text = "MaTrIX Co — متصفح ألعاب بدرع حماية وكاشف تجسس.\nالإصدار " + a.appVersion() + " (GX Edition)\n\nالأيقونات والواجهة مرسومة بالكود بدون مكتبات خارجية، والقوائم بتتحدث تلقائيًا كل 24 ساعة."
            textSize = 14f; setTextColor(theme.sub); setPadding(dp(8), dp(14), dp(8), dp(8))
        })
        section(col, "التحديثات")
        card(col, listOf(
            action("reload", "فحص التحديثات الآن", "الإصدار الحالي " + a.appVersion()) { a.updateUi.check(true) },
            toggle("reload", "فحص تلقائي للتحديثات", "مرتين في اليوم كحد أقصى، لما تفتح التطبيق", "upd_auto", true)
        ))
    }
}

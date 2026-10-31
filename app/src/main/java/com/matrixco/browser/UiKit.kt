package com.matrixco.browser

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** أدوات بناء الواجهة — تصميم هادئ ورسمي (مسطّح) */
class UiKit(private val act: Activity) {
    lateinit var theme: MxTheme

    companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    fun dp(v: Int) = Ui.dp(act, v)

    fun glow(tv: TextView, color: Int, radius: Float = 10f): TextView { tv.setShadowLayer(radius, 0f, 0f, color); return tv }

    fun sheet(full: Boolean = false, build: (LinearLayout, Dialog) -> Unit): Dialog {
        val d = Dialog(act)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val sv = ScrollView(act).apply { isVerticalScrollBarEnabled = false }
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(14), dp(12), dp(14), dp(16))
            background = Ui.topRound(theme.surface, dp(20).toFloat())
        }
        sv.addView(box, ViewGroup.LayoutParams(MATCH, WRAP))
        build(box, d)
        d.setContentView(sv)
        d.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setGravity(Gravity.BOTTOM)
            val maxW = dp(560)
            val sw = act.resources.displayMetrics.widthPixels
            w.setLayout(if (sw > maxW) maxW else MATCH, if (full) MATCH else WRAP)
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setDimAmount(0.5f)
        }
        d.show()
        return d
    }

    fun titleView(text: String) = TextView(act).apply {
        this.text = text; textSize = 16f; setTextColor(theme.text); typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(4), dp(2), dp(4), dp(10))
    }

    fun infoView(text: String) = TextView(act).apply {
        this.text = text; textSize = 12f; setTextColor(theme.sub)
        setPadding(dp(4), 0, dp(4), dp(10))
    }

    fun sectionView(text: String) = TextView(act).apply {
        this.text = text; textSize = 11f; setTextColor(theme.sub); typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(6), dp(12), dp(6), dp(6))
    }

    /** أيقونة دائرية مسطّحة */
    fun iconTile(emoji: String, color: Int, size: Int = 40): TextView = TextView(act).apply {
        text = emoji; textSize = size * 0.42f; gravity = Gravity.CENTER
        setTextColor(theme.text)
        background = Ui.oval(theme.surface2)
        minimumWidth = dp(size); minimumHeight = dp(size)
    }

    fun row(icon: String, title: String, sub: String? = null, checked: Boolean? = null, color: Int = theme.accent, onClick: () -> Unit): View {
        val r = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(12), dp(8))
            background = Ui.round(theme.surface2, dp(12).toFloat())
            setOnClickListener { onClick() }
        }
        r.addView(iconTile(icon, color, 34), LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(10) })
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(act).apply { text = title; textSize = 14f; setTextColor(theme.text) })
        if (sub != null) col.addView(TextView(act).apply { text = sub; textSize = 11f; setTextColor(theme.sub) })
        r.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (checked != null) r.addView(switchView(checked))
        r.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(6) }
        return r
    }

    /** مفتاح تشغيل قياسي بألوان الثيم */
    fun switchView(on: Boolean): Switch = Switch(act).apply {
        isClickable = false; isFocusable = false
        applySwitch(this, on)
    }

    fun applySwitch(sw: Switch, on: Boolean) {
        sw.isChecked = on
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(theme.accent, theme.sub))
        sw.trackTintList = ColorStateList(states, intArrayOf(theme.accent.withAlpha(110), theme.sub.withAlpha(70)))
    }

    fun toggleRow(icon: String, title: String, sub: String?, initial: Boolean, color: Int = theme.accent, onChange: (Boolean) -> Unit): View {
        var state = initial
        val v = row(icon, title, sub, state, color) {} as LinearLayout
        val sw = v.getChildAt(2) as Switch
        v.setOnClickListener { state = !state; applySwitch(sw, state); onChange(state) }
        return v
    }

    fun button(text: String, primary: Boolean = true, onClick: () -> Unit): TextView = TextView(act).apply {
        this.text = text; textSize = 13f; gravity = Gravity.CENTER
        setTextColor(if (primary) Color.WHITE else theme.text)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = if (primary) Ui.round(theme.accent, dp(12).toFloat()) else Ui.round(theme.surface2, dp(12).toFloat())
        setOnClickListener { onClick() }
    }

    fun input(hint: String, multiline: Boolean = false, text: String = ""): EditText = EditText(act).apply {
        this.hint = hint; setText(text)
        textSize = 14f
        setTextColor(theme.text); setHintTextColor(theme.sub)
        background = Ui.round(theme.surface2, dp(12).toFloat())
        setPadding(dp(12), dp(10), dp(12), dp(10))
        if (multiline) {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 5; gravity = Gravity.TOP or Gravity.START
            typeface = Typeface.MONOSPACE
        } else inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }

    fun space(h: Int) = View(act).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(h)) }

    /** بلاطة في شبكة الأدوات (مسطّحة وصغيرة) */
    fun gridTile(emoji: String, label: String, color: Int, onClick: () -> Unit): View {
        val c = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(2), dp(8), dp(2), dp(6))
            setOnClickListener { onClick() }
        }
        c.addView(iconTile(emoji, color, 46), LinearLayout.LayoutParams(dp(46), dp(46)))
        c.addView(TextView(act).apply {
            text = label; textSize = 10f; setTextColor(theme.sub); gravity = Gravity.CENTER; maxLines = 2
            setPadding(0, dp(5), 0, 0)
        }, LinearLayout.LayoutParams(MATCH, WRAP))
        return c
    }

    fun gridRows(tiles: List<View>, cols: Int = 4): List<LinearLayout> {
        val rows = ArrayList<LinearLayout>()
        var i = 0
        while (i < tiles.size) {
            val r = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
            for (j in 0 until cols) {
                val v = tiles.getOrNull(i + j)
                if (v != null) r.addView(v, LinearLayout.LayoutParams(0, WRAP, 1f))
                else r.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
            }
            rows.add(r); i += cols
        }
        return rows
    }
}

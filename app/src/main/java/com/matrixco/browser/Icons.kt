package com.matrixco.browser

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.sin

/** أيقونات خطية رسمية (24×24) مرسومة بالكود — بدون أي ملفات صور */
class IconDrawable(private val type: String, var color: Int, private val sizePx: Int, var label: String = "") : Drawable() {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 1.9f
    }
    private val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val r = RectF()

    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx
    override fun setAlpha(alpha: Int) { p.alpha = alpha; f.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf; f.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private fun line(x1: Float, y1: Float, x2: Float, y2: Float, c: Canvas) = c.drawLine(x1, y1, x2, y2, p)

    private fun poly(c: Canvas, close: Boolean, vararg pts: Float) {
        path.reset(); path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        if (close) path.close()
        c.drawPath(path, p)
    }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.width() <= 0) return
        p.color = color; f.color = color
        c.save()
        c.translate(b.left.toFloat(), b.top.toFloat())
        val s = b.width() / 24f
        c.scale(s, s)
        when (type) {
            "back" -> poly(c, false, 15f, 5f, 8f, 12f, 15f, 19f)
            "forward" -> poly(c, false, 9f, 5f, 16f, 12f, 9f, 19f)
            "chevron_down" -> poly(c, false, 6f, 9f, 12f, 15f, 18f, 9f)
            "chevron_up" -> poly(c, false, 6f, 15f, 12f, 9f, 18f, 15f)
            "check" -> poly(c, false, 5f, 12.5f, 10f, 17f, 19f, 7f)
            "close" -> { line(6f, 6f, 18f, 18f, c); line(18f, 6f, 6f, 18f, c) }
            "plus" -> { line(12f, 5f, 12f, 19f, c); line(5f, 12f, 19f, 12f, c) }
            "dots" -> { f.style = Paint.Style.FILL; c.drawCircle(12f, 5f, 1.8f, f); c.drawCircle(12f, 12f, 1.8f, f); c.drawCircle(12f, 19f, 1.8f, f) }
            "reload" -> {
                r.set(5f, 5f, 19f, 19f); c.drawArc(r, -50f, 300f, false, p)
                poly(c, false, 16.6f, 2.8f, 16.6f, 6.7f, 20.5f, 6.7f)
            }
            "tabs" -> {
                r.set(4f, 4f, 20f, 20f); c.drawRoundRect(r, 4f, 4f, p)
                if (label.isNotEmpty()) {
                    f.textSize = 10f; f.textAlign = Paint.Align.CENTER; f.isFakeBoldText = true
                    c.drawText(label, 12f, 15.2f, f)
                }
            }
            "bookmark" -> poly(c, true, 7f, 4f, 17f, 4f, 17f, 20f, 12f, 16f, 7f, 20f)
            "bookmark_fill" -> { f.style = Paint.Style.FILL; path.reset(); path.moveTo(7f, 4f); path.lineTo(17f, 4f); path.lineTo(17f, 20f); path.lineTo(12f, 16f); path.lineTo(7f, 20f); path.close(); c.drawPath(path, f) }
            "home" -> { poly(c, false, 4f, 11f, 12f, 4f, 20f, 11f); poly(c, false, 6.5f, 9.5f, 6.5f, 20f, 10.5f, 20f, 10.5f, 14f, 13.5f, 14f, 13.5f, 20f, 17.5f, 20f, 17.5f, 9.5f) }
            "search" -> { c.drawCircle(11f, 11f, 6f, p); line(15.5f, 15.5f, 20f, 20f, c) }
            "star" -> {
                path.reset()
                for (i in 0 until 10) {
                    val ang = Math.toRadians((-90 + i * 36).toDouble()); val rad = if (i % 2 == 0) 9f else 4f
                    val x = 12f + rad * cos(ang).toFloat(); val y = 12.5f + rad * sin(ang).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close(); c.drawPath(path, p)
            }
            "shield" -> {
                path.reset(); path.moveTo(12f, 3f); path.lineTo(19f, 6f); path.lineTo(19f, 12f)
                path.cubicTo(19f, 16f, 16f, 19f, 12f, 21f); path.cubicTo(8f, 19f, 5f, 16f, 5f, 12f); path.lineTo(5f, 6f); path.close()
                c.drawPath(path, p)
            }
            "shield_check" -> {
                path.reset(); path.moveTo(12f, 3f); path.lineTo(19f, 6f); path.lineTo(19f, 12f)
                path.cubicTo(19f, 16f, 16f, 19f, 12f, 21f); path.cubicTo(8f, 19f, 5f, 16f, 5f, 12f); path.lineTo(5f, 6f); path.close()
                c.drawPath(path, p); poly(c, false, 8.7f, 12f, 11f, 14.3f, 15.5f, 9.5f)
            }
            "lock" -> { r.set(6f, 11f, 18f, 20f); c.drawRoundRect(r, 2f, 2f, p); r.set(8f, 3.5f, 16f, 14f); c.drawArc(r, 180f, 180f, false, p) }
            "lock_open" -> { r.set(6f, 11f, 18f, 20f); c.drawRoundRect(r, 2f, 2f, p); r.set(8f, 3.5f, 16f, 14f); c.drawArc(r, 180f, 140f, false, p) }
            "incognito" -> {
                poly(c, false, 6f, 10f, 8f, 4f, 16f, 4f, 18f, 10f); line(3f, 10f, 21f, 10f, c)
                c.drawCircle(8f, 16f, 2.8f, p); c.drawCircle(16f, 16f, 2.8f, p); line(10.8f, 16f, 13.2f, 16f, c)
            }
            "history" -> { r.set(4f, 4f, 20f, 20f); c.drawArc(r, 20f, 320f, false, p); poly(c, false, 12f, 8f, 12f, 12f, 15f, 14f); poly(c, false, 2.5f, 4.5f, 5f, 8.2f, 8.5f, 6f) }
            "download" -> { poly(c, false, 12f, 4f, 12f, 15f); poly(c, false, 7f, 11f, 12f, 16f, 17f, 11f); line(5f, 20f, 19f, 20f, c) }
            "settings" -> {
                c.drawCircle(12f, 12f, 3.2f, p)
                for (i in 0 until 8) {
                    val a = Math.toRadians((i * 45).toDouble()); val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                    line(12f + 6.2f * ca, 12f + 6.2f * sa, 12f + 8.6f * ca, 12f + 8.6f * sa, c)
                }
                c.drawCircle(12f, 12f, 6.2f, p)
            }
            "share" -> {
                c.drawCircle(18f, 5.5f, 2.5f, p); c.drawCircle(6f, 12f, 2.5f, p); c.drawCircle(18f, 18.5f, 2.5f, p)
                line(8.2f, 10.8f, 15.8f, 6.7f, c); line(8.2f, 13.2f, 15.8f, 17.3f, c)
            }
            "desktop" -> { r.set(3f, 4f, 21f, 16f); c.drawRoundRect(r, 2f, 2f, p); line(8f, 20f, 16f, 20f, c); line(12f, 16f, 12f, 20f, c) }
            "book" -> { poly(c, false, 12f, 6f, 12f, 19f); poly(c, false, 12f, 6f, 6f, 5f, 3f, 6f, 3f, 18f, 6f, 17f, 12f, 19f); poly(c, false, 12f, 6f, 18f, 5f, 21f, 6f, 21f, 18f, 18f, 17f, 12f, 19f) }
            "camera" -> { r.set(3f, 7f, 21f, 19f); c.drawRoundRect(r, 3f, 3f, p); c.drawCircle(12f, 13f, 3.6f, p); poly(c, false, 9f, 7f, 10.5f, 5f, 13.5f, 5f, 15f, 7f) }
            "globe" -> { c.drawCircle(12f, 12f, 8.5f, p); r.set(8f, 3.5f, 16f, 20.5f); c.drawOval(r, p); line(3.5f, 12f, 20.5f, 12f, c) }
            "game" -> { r.set(3f, 8f, 21f, 17f); c.drawRoundRect(r, 4.5f, 4.5f, p); line(8f, 10.5f, 8f, 14.5f, c); line(6f, 12.5f, 10f, 12.5f, c); c.drawCircle(15f, 11.5f, 0.9f, p); c.drawCircle(17.5f, 13.5f, 0.9f, p) }
            "bolt" -> poly(c, true, 13f, 3f, 5f, 14f, 11f, 14f, 10f, 21f, 19f, 9f, 13f, 9f)
            "sparkle" -> {
                path.reset(); path.moveTo(12f, 3f); path.quadTo(12f, 12f, 21f, 12f); path.quadTo(12f, 12f, 12f, 21f); path.quadTo(12f, 12f, 3f, 12f); path.quadTo(12f, 12f, 12f, 3f); path.close()
                c.drawPath(path, p)
            }
            "eye" -> { r.set(3f, 7f, 21f, 17f); c.drawOval(r, p); c.drawCircle(12f, 12f, 2.8f, p) }
            "code" -> { poly(c, false, 8f, 7f, 3f, 12f, 8f, 17f); poly(c, false, 16f, 7f, 21f, 12f, 16f, 17f) }
            "storage" -> { r.set(4f, 4f, 20f, 9f); c.drawRoundRect(r, 2f, 2f, p); r.set(4f, 11f, 20f, 16f); c.drawRoundRect(r, 2f, 2f, p); c.drawCircle(7.5f, 6.5f, 0.6f, p); c.drawCircle(7.5f, 13.5f, 0.6f, p); line(4f, 19.5f, 20f, 19.5f, c) }
            "spy" -> { c.drawCircle(10.5f, 10.5f, 5.5f, p); line(14.6f, 14.6f, 20f, 20f, c); line(8f, 10.5f, 13f, 10.5f, c) }
            "info" -> { c.drawCircle(12f, 12f, 8.5f, p); line(12f, 11f, 12f, 16.5f, c); c.drawCircle(12f, 7.8f, 0.5f, p) }
            "trash" -> { line(5f, 7f, 19f, 7f, c); poly(c, false, 7f, 7f, 8f, 20f, 16f, 20f, 17f, 7f); poly(c, false, 9.5f, 7f, 9.5f, 4f, 14.5f, 4f, 14.5f, 7f) }
            "palette" -> { c.drawCircle(12f, 12f, 8.5f, p); f.style = Paint.Style.FILL; c.drawCircle(8.5f, 10f, 1.2f, f); c.drawCircle(12f, 7.8f, 1.2f, f); c.drawCircle(15.8f, 10f, 1.2f, f) }
            "translate" -> { poly(c, false, 4f, 6f, 14f, 6f); line(9f, 4f, 9f, 6f, c); poly(c, false, 6f, 6f, 8f, 12f, 12f, 12f); poly(c, false, 12f, 20f, 15.5f, 10f, 19f, 20f); line(13.2f, 17f, 17.8f, 17f, c) }
            "full" -> { poly(c, false, 4f, 9f, 4f, 4f, 9f, 4f); poly(c, false, 15f, 4f, 20f, 4f, 20f, 9f); poly(c, false, 20f, 15f, 20f, 20f, 15f, 20f); poly(c, false, 9f, 20f, 4f, 20f, 4f, 15f) }
            "clock" -> { c.drawCircle(12f, 12f, 8.5f, p); poly(c, false, 12f, 7f, 12f, 12f, 15.5f, 14f) }
            "moon" -> { path.reset(); path.moveTo(19f, 14.5f); path.cubicTo(11f, 18f, 5f, 11f, 9.5f, 4.5f); path.cubicTo(5f, 5f, 3f, 10f, 4.5f, 14f); path.cubicTo(6.5f, 19.5f, 15f, 21f, 19f, 14.5f); c.drawPath(path, p) }
            else -> { f.style = Paint.Style.FILL; f.textSize = 16f; f.textAlign = Paint.Align.CENTER; c.drawText(type.removePrefix("t:"), 12f, 17.5f, f) }
        }
        c.restore()
    }
}

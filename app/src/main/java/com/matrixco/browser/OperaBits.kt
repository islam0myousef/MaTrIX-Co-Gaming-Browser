package com.matrixco.browser

import android.content.Context
import android.view.MotionEvent
import android.widget.LinearLayout
import kotlin.math.abs

/** شريط سفلي بإيماءات Opera: dir = 0 سحب لفوق، 1 شمال (التبويب التالي)، -1 يمين (السابق) */
class SwipeBar(ctx: Context, private val onSwipe: (Int) -> Unit) : LinearLayout(ctx) {
    private var sx = 0f
    private var sy = 0f
    private var done = false
    private val th = 40f * ctx.resources.displayMetrics.density

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; done = false }
            MotionEvent.ACTION_MOVE -> if (!done) {
                val dx = e.rawX - sx
                val dy = e.rawY - sy
                if (dy < -th && abs(dy) > abs(dx)) { done = true; onSwipe(0); return true }
                if (abs(dx) > th * 2 && abs(dx) > abs(dy) * 1.5f) { done = true; onSwipe(if (dx < 0) 1 else -1); return true }
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean = true
}

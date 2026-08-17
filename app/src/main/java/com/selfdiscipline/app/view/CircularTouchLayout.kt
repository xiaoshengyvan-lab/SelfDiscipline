package com.selfdiscipline.app.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.hypot

/**
 * 圆形触碰区域：仅圆形内部响应点击/长按，圆外触摸事件直接穿透、不触发。
 */
class CircularTouchLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val cx = width / 2f
        val cy = height / 2f
        val r = width.coerceAtMost(height) / 2f
        val d = hypot((ev.x - cx).toDouble(), (ev.y - cy).toDouble())
        if (d > r) return false // 圆外：不消费事件
        return super.dispatchTouchEvent(ev)
    }
}

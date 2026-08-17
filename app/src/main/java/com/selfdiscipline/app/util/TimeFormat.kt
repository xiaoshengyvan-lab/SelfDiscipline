package com.selfdiscipline.app.util

import java.util.Locale

/**
 * 时长格式化工具
 */
object TimeFormat {

    /** 毫秒 -> "X小时X分钟" / "X分钟" */
    fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60_000L
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> "${h}小时${m}分钟"
            h > 0 -> "${h}小时"
            else -> "${m}分钟"
        }
    }

    /** 毫秒 -> 倒计时 "mm:ss" 或 "hh:mm:ss" */
    fun formatCountdown(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }
}

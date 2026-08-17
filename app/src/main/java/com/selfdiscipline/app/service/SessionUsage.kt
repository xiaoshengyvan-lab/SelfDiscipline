package com.selfdiscipline.app.service

import android.content.Context
import android.os.SystemClock
import com.selfdiscipline.app.data.AppSettings

/**
 * 会话使用时长（自手机解锁/亮屏后累计的屏幕使用时间）。
 *
 * 事件驱动（由 UsageMonitorService 中的屏幕广播触发）：
 *  - 亮屏：距上次息屏超过 2 分钟视为新会话，从头计时；短暂亮屏（通知等）延续原会话
 *  - 息屏：把当前段时长累计入账并暂停计时
 *  - 解锁（USER_PRESENT）：强制开启新会话
 *
 * 使用 elapsedRealtime 计时，不查询 UsageStatsManager，不持有唤醒锁，几乎零耗电。
 * 状态持久化到 SharedPreferences，服务被杀后仍可恢复。
 */
object SessionUsage {

    /** 亮屏距上次息屏超过该值（毫秒）视为新会话 */
    private const val RESET_GAP_MS = 120_000L

    /** 当前会话累计使用时长（毫秒） */
    fun currentMs(context: Context): Long {
        val s = AppSettings(context)
        val now = SystemClock.elapsedRealtime()
        val acc = s.sessionAccumulatedMs
        return if (s.sessionScreenOn) {
            acc + (now - s.sessionStartElapsed).coerceAtLeast(0L)
        } else {
            acc
        }
    }

    /** 亮屏事件 */
    fun onScreenOn(context: Context) {
        val s = AppSettings(context)
        if (s.sessionScreenOn) return
        val now = SystemClock.elapsedRealtime()
        val gap = now - s.lastScreenOffElapsed
        if (s.lastScreenOffElapsed == 0L || gap >= RESET_GAP_MS) {
            // 新会话：从头计时
            s.sessionAccumulatedMs = 0L
        }
        // 短暂亮屏则延续已累计的时长，从当前时刻继续计
        s.sessionStartElapsed = now
        s.sessionScreenOn = true
    }

    /** 息屏事件 */
    fun onScreenOff(context: Context) {
        val s = AppSettings(context)
        if (!s.sessionScreenOn) return
        val now = SystemClock.elapsedRealtime()
        s.sessionAccumulatedMs += (now - s.sessionStartElapsed).coerceAtLeast(0L)
        s.sessionStartElapsed = now
        s.sessionScreenOn = false
        s.lastScreenOffElapsed = now
    }

    /** 解锁事件（真正的从锁屏打开）：强制开启新会话 */
    fun onUserPresent(context: Context) {
        val s = AppSettings(context)
        val now = SystemClock.elapsedRealtime()
        s.sessionAccumulatedMs = 0L
        s.sessionStartElapsed = now
        s.sessionScreenOn = true
    }
}

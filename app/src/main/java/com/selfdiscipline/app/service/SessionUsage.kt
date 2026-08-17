package com.selfdiscipline.app.service

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import com.selfdiscipline.app.data.AppSettings

/**
 * 会话使用时长（自手机解锁/亮屏后累计的屏幕使用时间）。
 *
 * 事件驱动（由 UsageMonitorService 中的屏幕广播触发）：
 *  - 亮屏：距上次息屏超过 2 分钟视为新会话，从头计时；短暂亮屏（通知等）延续原会话
 *  - 息屏：把当前段时长累计入账并暂停计时
 *  - 解锁（USER_PRESENT）：仅在未计时时开启新会话，避免部分机型反复广播导致清零
 *  - resumeIfNeeded：服务启动时同步当前屏幕状态，确保亮屏且未锁屏时立即开始计时
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

    /**
     * 解锁事件（从锁屏打开）：
     * 仅在尚未计时时开启新会话；若屏幕已亮且正在计时则忽略，
     * 避免部分机型/ROM 在每次亮屏时都广播 USER_PRESENT 导致计时被反复清零。
     */
    fun onUserPresent(context: Context) {
        val s = AppSettings(context)
        if (s.sessionScreenOn) return
        val now = SystemClock.elapsedRealtime()
        s.sessionAccumulatedMs = 0L
        s.sessionStartElapsed = now
        s.sessionScreenOn = true
    }

    /**
     * 服务启动/应用回到前台时同步当前屏幕状态：
     * 若屏幕亮着且未锁屏但未在计时，则立即开始新会话
     * （解决“服务被杀后重启/升级后未经历一次息屏-亮屏周期，导致一直为 0”的问题）。
     */
    fun resumeIfNeeded(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked) return

        val s = AppSettings(context)
        if (s.sessionScreenOn) return
        val now = SystemClock.elapsedRealtime()
        s.sessionAccumulatedMs = 0L
        s.sessionStartElapsed = now
        s.sessionScreenOn = true
    }
}

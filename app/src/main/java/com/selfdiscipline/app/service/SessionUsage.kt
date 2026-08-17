package com.selfdiscipline.app.service

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import com.selfdiscipline.app.data.AppSettings

/**
 * 会话使用时长（自手机解锁后累计的屏幕使用时间）。
 *
 * 计时偏差修复（本版）：
 *  - 锁屏界面 / 息屏显示（AOD）/ 通知点亮不再计入：锁屏点亮时置「等待解锁」，解锁后才开始计时
 *  - 快速锁屏再解锁会正确刷新：解锁（USER_PRESENT）一律开启新会话，而不是沿用 2 分钟间隔规则
 *  - 无锁屏设备仍用「距上次息屏 ≥ 2 分钟」判定新会话，通知短暂亮屏不会打断
 *
 * 提醒后的会话规则：
 *  - 达到阈值提醒后「冻结」计时：本次会话不再累计
 *  - 锁屏再解锁（新会话）后自动恢复计时并刷新为 0，同时恢复提醒
 *
 * 使用 elapsedRealtime 计时，不查询 UsageStatsManager，不持有唤醒锁，几乎零耗电。
 * 状态持久化到 SharedPreferences，服务被杀后仍可恢复。
 */
object SessionUsage {

    /** 无锁屏设备：亮屏距上次息屏超过该值（毫秒）视为新会话 */
    private const val RESET_GAP_MS = 120_000L

    /** 当前会话累计使用时长（毫秒）；冻结后返回固定值，不再累加 */
    fun currentMs(context: Context): Long {
        val s = AppSettings(context)
        if (s.sessionFrozen) return s.sessionAccumulatedMs
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
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked) {
            // 锁屏点亮（含息屏显示/通知唤醒）：等待解锁，不计时
            s.sessionPendingUnlock = true
            return
        }
        val gap = now - s.lastScreenOffElapsed
        if (s.lastScreenOffElapsed == 0L || gap >= RESET_GAP_MS) {
            // 新会话：从头计时并恢复提醒
            startNewSession(s, now)
        } else {
            // 短暂亮屏（通知等）延续原会话
            s.sessionStartElapsed = now
        }
        s.sessionScreenOn = true
    }

    /** 息屏事件 */
    fun onScreenOff(context: Context) {
        val s = AppSettings(context)
        if (s.sessionScreenOn) {
            val now = SystemClock.elapsedRealtime()
            if (!s.sessionFrozen) {
                s.sessionAccumulatedMs += (now - s.sessionStartElapsed).coerceAtLeast(0L)
            }
            s.sessionStartElapsed = now
            s.sessionScreenOn = false
            s.lastScreenOffElapsed = now
        }
        s.sessionPendingUnlock = false
    }

    /**
     * 解锁事件（从锁屏打开）：
     *  - 等待解锁中 → 开启新会话（刷新时长）
     *  - 未在计时 → 开启新会话
     *  - 已在计时（无锁屏设备反复广播）→ 忽略，避免清零
     */
    fun onUserPresent(context: Context) {
        val s = AppSettings(context)
        if (s.sessionScreenOn && !s.sessionPendingUnlock) return
        startNewSession(s, SystemClock.elapsedRealtime())
    }

    /**
     * 服务启动/应用回到前台时同步当前屏幕状态：
     * 若屏幕亮着且未锁屏但未在计时，则立即开始新会话。
     */
    fun resumeIfNeeded(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked) return

        val s = AppSettings(context)
        if (s.sessionScreenOn) return
        startNewSession(s, SystemClock.elapsedRealtime())
    }

    /** 提醒触发后冻结计时：本次会话不再累计（下次解锁自动恢复） */
    fun freeze(context: Context) {
        val s = AppSettings(context)
        if (s.sessionScreenOn) {
            val now = SystemClock.elapsedRealtime()
            s.sessionAccumulatedMs += (now - s.sessionStartElapsed).coerceAtLeast(0L)
            s.sessionStartElapsed = now
        }
        s.sessionFrozen = true
    }

    /** 开启新会话：清零时长、恢复计时与提醒 */
    private fun startNewSession(s: AppSettings, now: Long) {
        s.sessionAccumulatedMs = 0L
        s.sessionStartElapsed = now
        s.sessionScreenOn = true
        s.sessionPendingUnlock = false
        s.sessionFrozen = false
        s.sessionMuted = false
        s.snoozeUntilMs = 0L
    }
}

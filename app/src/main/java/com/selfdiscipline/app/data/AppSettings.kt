package com.selfdiscipline.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用设置（SharedPreferences 封装）：
 *  - 自律提醒总开关
 *  - 使用时长提醒阈值（分钟）
 *  - 两次提醒的最小间隔（分钟）
 *  - 是否忽略本应用自身的使用时间（保留字段）
 *  - 上次提醒时间戳（用于间隔节流）
 *  - 会话时长状态（亮屏/息屏事件驱动，供 SessionUsage 持久化）
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    /** 自律监控总开关 */
    var monitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITORING, false)
        set(value) = prefs.edit().putBoolean(KEY_MONITORING, value).apply()

    /** 使用时长达到该值（分钟）后触发提醒 */
    var dailyThresholdMinutes: Int
        get() = prefs.getInt(KEY_THRESHOLD, 60)
        set(value) = prefs.edit().putInt(KEY_THRESHOLD, value.coerceIn(5, 1440)).apply()

    /** 两次提醒之间的最小间隔（分钟），避免频繁打扰 */
    var remindIntervalMinutes: Int
        get() = prefs.getInt(KEY_INTERVAL, 30)
        set(value) = prefs.edit().putInt(KEY_INTERVAL, value.coerceIn(5, 720)).apply()

    /** 是否把“使用本 App 的时间”排除在统计之外（保留字段） */
    var excludeSelf: Boolean
        get() = prefs.getBoolean(KEY_EXCLUDE_SELF, true)
        set(value) = prefs.edit().putBoolean(KEY_EXCLUDE_SELF, value).apply()

    /** 上次提醒时间戳（毫秒） */
    var lastRemindAt: Long
        get() = prefs.getLong(KEY_LAST_REMIND, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_REMIND, value).apply()

    // ---------- 本次会话提醒节流 ----------

    /** 再次提醒时间点（墙钟毫秒，0 表示未设置；新会话自动清除） */
    var snoozeUntilMs: Long
        get() = prefs.getLong(KEY_SNOOZE_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_SNOOZE_UNTIL, value).apply()

    /** 本次会话是否已关闭提醒（下次解锁自动恢复） */
    var sessionMuted: Boolean
        get() = prefs.getBoolean(KEY_SESSION_MUTED, false)
        set(value) = prefs.edit().putBoolean(KEY_SESSION_MUTED, value).apply()

    /** 提醒后是否已冻结计时（本次会话不再累计，下次解锁重置） */
    var sessionFrozen: Boolean
        get() = prefs.getBoolean(KEY_SESSION_FROZEN, false)
        set(value) = prefs.edit().putBoolean(KEY_SESSION_FROZEN, value).apply()

    // ---------- 专注计时状态（跨页面/进程保留） ----------

    /** 正在专注的任务 id（-1 表示无） */
    var focusTaskId: Long
        get() = prefs.getLong(KEY_FOCUS_TASK_ID, -1L)
        set(value) = prefs.edit().putLong(KEY_FOCUS_TASK_ID, value).apply()

    /** 专注截止时刻（elapsedRealtime，毫秒；0 表示无） */
    var focusDeadlineElapsed: Long
        get() = prefs.getLong(KEY_FOCUS_DEADLINE, 0L)
        set(value) = prefs.edit().putLong(KEY_FOCUS_DEADLINE, value).apply()

    /** 专注任务名称（任务被删除时兜底显示） */
    var focusTaskTitle: String
        get() = prefs.getString(KEY_FOCUS_TITLE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FOCUS_TITLE, value).apply()

    /** 专注任务时长（分钟） */
    var focusTaskDurationMinutes: Int
        get() = prefs.getInt(KEY_FOCUS_DURATION, 30)
        set(value) = prefs.edit().putInt(KEY_FOCUS_DURATION, value).apply()

    /** 清除专注状态（完成或计时结束时调用） */
    fun clearFocus() {
        prefs.edit()
            .putLong(KEY_FOCUS_TASK_ID, -1L)
            .putLong(KEY_FOCUS_DEADLINE, 0L)
            .putString(KEY_FOCUS_TITLE, "")
            .apply()
    }

    // ---------- 会话时长状态 ----------

    /** 已累计的会话使用时长（息屏时入账，毫秒） */
    var sessionAccumulatedMs: Long
        get() = prefs.getLong(KEY_SESSION_ACC, 0L)
        set(value) = prefs.edit().putLong(KEY_SESSION_ACC, value).apply()

    /** 当前计时段起点（elapsedRealtime，毫秒） */
    var sessionStartElapsed: Long
        get() = prefs.getLong(KEY_SESSION_START, 0L)
        set(value) = prefs.edit().putLong(KEY_SESSION_START, value).apply()

    /** 屏幕是否亮着 */
    var sessionScreenOn: Boolean
        get() = prefs.getBoolean(KEY_SESSION_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_SESSION_ON, value).apply()

    /** 上次息屏时刻（elapsedRealtime，毫秒），用于判断短亮屏 */
    var lastScreenOffElapsed: Long
        get() = prefs.getLong(KEY_LAST_SCREEN_OFF, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SCREEN_OFF, value).apply()

    private companion object {
        const val KEY_MONITORING = "monitoring_enabled"
        const val KEY_THRESHOLD = "daily_threshold_minutes"
        const val KEY_INTERVAL = "remind_interval_minutes"
        const val KEY_EXCLUDE_SELF = "exclude_self"
        const val KEY_LAST_REMIND = "last_remind_at"
        const val KEY_SNOOZE_UNTIL = "snooze_until_ms"
        const val KEY_SESSION_MUTED = "session_muted"
        const val KEY_SESSION_FROZEN = "session_frozen"
        const val KEY_FOCUS_TASK_ID = "focus_task_id"
        const val KEY_FOCUS_DEADLINE = "focus_deadline_elapsed"
        const val KEY_FOCUS_TITLE = "focus_task_title"
        const val KEY_FOCUS_DURATION = "focus_task_duration"
        const val KEY_SESSION_ACC = "session_accumulated_ms"
        const val KEY_SESSION_START = "session_start_elapsed"
        const val KEY_SESSION_ON = "session_screen_on"
        const val KEY_LAST_SCREEN_OFF = "last_screen_off_elapsed"
    }
}

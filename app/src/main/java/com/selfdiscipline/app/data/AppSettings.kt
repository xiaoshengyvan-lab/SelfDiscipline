package com.selfdiscipline.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用设置（SharedPreferences 封装）：
 *  - 自律提醒总开关
 *  - 每日使用时长提醒阈值（分钟）
 *  - 两次提醒的最小间隔（分钟）
 *  - 是否忽略本应用自身的使用时间
 *  - 上次提醒时间戳（用于间隔节流）
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    /** 自律监控总开关 */
    var monitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITORING, false)
        set(value) = prefs.edit().putBoolean(KEY_MONITORING, value).apply()

    /** 每日使用时长达到该值（分钟）后触发提醒 */
    var dailyThresholdMinutes: Int
        get() = prefs.getInt(KEY_THRESHOLD, 60)
        set(value) = prefs.edit().putInt(KEY_THRESHOLD, value.coerceIn(5, 1440)).apply()

    /** 两次提醒之间的最小间隔（分钟），避免频繁打扰 */
    var remindIntervalMinutes: Int
        get() = prefs.getInt(KEY_INTERVAL, 30)
        set(value) = prefs.edit().putInt(KEY_INTERVAL, value.coerceIn(5, 720)).apply()

    /** 是否把“使用本 App 的时间”排除在统计之外 */
    var excludeSelf: Boolean
        get() = prefs.getBoolean(KEY_EXCLUDE_SELF, true)
        set(value) = prefs.edit().putBoolean(KEY_EXCLUDE_SELF, value).apply()

    /** 上次提醒时间戳（毫秒） */
    var lastRemindAt: Long
        get() = prefs.getLong(KEY_LAST_REMIND, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_REMIND, value).apply()

    private companion object {
        const val KEY_MONITORING = "monitoring_enabled"
        const val KEY_THRESHOLD = "daily_threshold_minutes"
        const val KEY_INTERVAL = "remind_interval_minutes"
        const val KEY_EXCLUDE_SELF = "exclude_self"
        const val KEY_LAST_REMIND = "last_remind_at"
    }
}

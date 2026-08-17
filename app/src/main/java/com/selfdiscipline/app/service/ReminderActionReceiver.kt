package com.selfdiscipline.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import com.selfdiscipline.app.data.AppSettings

/**
 * 提醒通知动作处理：
 *  - 稍后提醒（10/30/60 分钟）：到点前不再弹提醒
 *  - 关闭本次提醒：本次会话不再提醒，下次锁屏解锁后自动恢复
 */
class ReminderActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE = "com.selfdiscipline.app.action.REMIND_SNOOZE"
        const val ACTION_MUTE = "com.selfdiscipline.app.action.REMIND_MUTE"
        const val EXTRA_MINUTES = "extra_minutes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val settings = AppSettings(context)
        when (intent.action) {
            ACTION_SNOOZE -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 10)
                settings.snoozeUntilMs = System.currentTimeMillis() + minutes * 60_000L
            }
            ACTION_MUTE -> {
                settings.sessionMuted = true
            }
        }
        // 取消提醒通知
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ReminderNotifier.NOTIFICATION_ID)
    }
}

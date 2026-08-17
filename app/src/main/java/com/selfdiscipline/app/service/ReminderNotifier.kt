package com.selfdiscipline.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.selfdiscipline.app.R
import com.selfdiscipline.app.data.Task
import com.selfdiscipline.app.reminder.ReminderActivity
import com.selfdiscipline.app.util.TimeFormat

/**
 * 自律提醒通知：
 * 使用全屏通知（full-screen intent）弹出提醒页，提醒内容包含“该去做什么”。
 * Android 10+ 会直接全屏弹出；旧版本以最高优先级横幅通知呈现。
 */
object ReminderNotifier {

    const val CHANNEL_REMINDER = "reminder_channel"
    const val NOTIFICATION_ID = 2001

    fun showReminder(context: Context, usageMillis: Long, task: Task?) {
        ensureChannel(context)

        val intent = Intent(context, ReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReminderActivity.EXTRA_USAGE, usageMillis)
            putExtra(ReminderActivity.EXTRA_TASK_ID, task?.id ?: -1L)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (task != null) {
            "自律提醒：该去${task.title}啦"
        } else {
            "自律提醒：放下手机休息一下吧"
        }
        val text = if (task != null) {
            "本次解锁已使用 ${TimeFormat.formatDuration(usageMillis)}，" +
                "建议完成「${task.title}」（约 ${task.durationMinutes} 分钟）"
        } else {
            "本次解锁已使用 ${TimeFormat.formatDuration(usageMillis)}，" +
                "去休息一下，做点别的事情吧"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .addAction(0, "10分钟后提醒", snoozeIntent(context, 10))
            .addAction(0, "关闭本次提醒", muteIntent(context))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // 通知权限被用户拒绝时静默失败，避免崩溃
        }
    }

    /** 稍后提醒动作 */
    private fun snoozeIntent(context: Context, minutes: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            100 + minutes,
            Intent(context, ReminderActionReceiver::class.java)
                .setAction(ReminderActionReceiver.ACTION_SNOOZE)
                .putExtra(ReminderActionReceiver.EXTRA_MINUTES, minutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** 关闭本次会话提醒动作 */
    private fun muteIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            200,
            Intent(context, ReminderActionReceiver::class.java)
                .setAction(ReminderActionReceiver.ACTION_MUTE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_REMINDER,
                "自律提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "使用时长超限时弹出的自律提醒"
            }
            nm.createNotificationChannel(channel)
        }
    }
}

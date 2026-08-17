package com.selfdiscipline.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.selfdiscipline.app.MainActivity
import com.selfdiscipline.app.R
import com.selfdiscipline.app.data.AppSettings
import com.selfdiscipline.app.data.TaskRepository
import com.selfdiscipline.app.util.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 后台使用时长监控前台服务。
 *
 * 每 60 秒统计一次当日手机使用时长：
 *  - 更新前台通知与主界面（广播 ACTION_UPDATE）
 *  - 超过阈值且满足最小间隔时，触发自律提醒（全屏通知）
 *
 * Android 14+ 使用 specialUse 前台服务类型。
 */
class UsageMonitorService : Service() {

    companion object {
        private const val TAG = "UsageMonitorService"

        const val CHANNEL_MONITOR = "usage_monitor_channel"
        const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 60_000L

        const val ACTION_STOP = "com.selfdiscipline.app.action.STOP_MONITOR"
        const val ACTION_UPDATE = "com.selfdiscipline.app.action.USAGE_UPDATE"
        const val EXTRA_USAGE_MILLIS = "extra_usage_millis"

        /** 启动监控（从前台调用） */
        fun start(context: Context) {
            val intent = Intent(context, UsageMonitorService::class.java)
            context.startForegroundService(intent)
        }

        /** 停止监控（由通知栏“关闭监控”按钮调用） */
        fun stop(context: Context) {
            val intent = Intent(context, UsageMonitorService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings by lazy { AppSettings(this) }
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                settings.monitoringEnabled = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (!started) {
            started = true
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(0L),
                fgsType()
            )
            startMonitoring()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Android 14+ 需要 specialUse 前台服务类型 */
    private fun fgsType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

    private fun startMonitoring() {
        scope.launch {
            while (isActive) {
                try {
                    val usage = UsageStatsHelper.getTodayUsageMillis(this@UsageMonitorService, settings.excludeSelf)
                    updateForegroundNotification(usage)
                    broadcastUsage(usage)
                    maybeRemind(usage)
                } catch (e: Exception) {
                    Log.w(TAG, "monitor loop error", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * 判断是否需要弹出自律提醒：
     *  - 开关已开启
     *  - 当日使用时长 >= 阈值
     *  - 距离上次提醒超过最小间隔
     * 提醒内容会带上“下一个最该做的任务”。
     */
    private suspend fun maybeRemind(usageMillis: Long) {
        if (!settings.monitoringEnabled) return
        val threshold = settings.dailyThresholdMinutes * 60_000L
        if (usageMillis < threshold) return

        val now = System.currentTimeMillis()
        if (now - settings.lastRemindAt < settings.remindIntervalMinutes * 60_000L) return

        settings.lastRemindAt = now
        val task = TaskRepository.get(this).nextPending()
        ReminderNotifier.showReminder(this, usageMillis, task)
    }

    private fun broadcastUsage(usageMillis: Long) {
        val intent = Intent(ACTION_UPDATE)
            .setPackage(packageName)
            .putExtra(EXTRA_USAGE_MILLIS, usageMillis)
        sendBroadcast(intent)
    }

    private fun updateForegroundNotification(usageMillis: Long) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(usageMillis))
    }

    private fun buildNotification(usageMillis: Long): Notification {
        ensureChannel()

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, UsageMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("自律助手 · 监控中")
            .setContentText("今日已使用手机 ${TimeFormat.formatDuration(usageMillis)}")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "关闭监控", stopIntent)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_MONITOR,
                "使用时长监控",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "后台监控手机使用时长时显示的常驻通知"
            }
            nm.createNotificationChannel(channel)
        }
    }
}

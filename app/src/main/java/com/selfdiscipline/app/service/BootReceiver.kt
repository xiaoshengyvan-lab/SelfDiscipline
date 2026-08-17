package com.selfdiscipline.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.selfdiscipline.app.data.AppSettings

/**
 * 开机广播：若自律开关已开启且已授予使用情况访问权限，则自动恢复后台监控。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settings = AppSettings(context)
            if (settings.monitoringEnabled && UsageStatsHelper.hasUsageAccess(context)) {
                UsageMonitorService.start(context)
            }
        }
    }
}

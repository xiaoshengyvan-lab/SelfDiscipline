package com.selfdiscipline.app.service

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.util.Calendar

/**
 * 手机使用时长统计工具（基于 UsageStatsManager）。
 */
object UsageStatsHelper {

    /** 是否已授予「使用情况访问」权限 */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** 跳转到系统「使用情况访问」设置页 */
    fun openUsageAccessSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    /**
     * 统计当日（0 点起）手机屏幕前台使用总时长。
     *
     * @param excludeSelf 是否排除本应用自身的使用时间
     * @return 毫秒
     */
    fun getTodayUsageMillis(context: Context, excludeSelf: Boolean = true): Long {
        if (!hasUsageAccess(context)) return 0L

        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val begin = cal.timeInMillis
            val end = System.currentTimeMillis() + 1000L

            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
            val selfPackage = context.packageName
            val launcher = defaultLauncherPackage(context)

            var total = 0L
            for (s in stats) {
                if (excludeSelf && s.packageName == selfPackage) continue
                if (s.packageName == launcher) continue
                total += s.totalTimeInForeground
            }
            total
        } catch (e: SecurityException) {
            0L
        }
    }

    /** 获取默认桌面启动器包名（桌面使用时间不计入“使用手机”时长） */
    private fun defaultLauncherPackage(context: Context): String? {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo =
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
    }
}

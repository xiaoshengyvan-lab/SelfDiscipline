package com.selfdiscipline.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.selfdiscipline.app.data.AppSettings
import com.selfdiscipline.app.databinding.ActivityMainBinding
import com.selfdiscipline.app.reminder.ReminderActivity
import com.selfdiscipline.app.service.UsageMonitorService
import com.selfdiscipline.app.service.UsageStatsHelper
import com.selfdiscipline.app.settings.SettingsActivity
import com.selfdiscipline.app.task.TaskListActivity
import com.selfdiscipline.app.util.TimeFormat

/**
 * 主界面：
 *  - 自律提醒开关（可开启/关闭）
 *  - 今日使用时长实时展示 + 进度
 *  - 使用情况访问权限引导
 *  - 任务清单 / 提醒设置入口
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val settings by lazy { AppSettings(this) }
    private var updatingSwitch = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "未授予通知权限，自律提醒将无法弹出", Toast.LENGTH_LONG).show()
            }
        }

    /** 接收后台服务广播的实时使用时长 */
    private val usageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val usage = intent?.getLongExtra(UsageMonitorService.EXTRA_USAGE_MILLIS, -1L) ?: -1L
            if (usage >= 0) showUsage(usage)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()
        setupSwitch()
        setupCards()
        updatePermissionUi()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            usageReceiver,
            IntentFilter(UsageMonitorService.ACTION_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(usageReceiver)
    }

    override fun onResume() {
        super.onResume()
        syncSwitchState()
        updatePermissionUi()
        // 开关为开且已授权时，确保后台监控在运行（含从授权页返回的场景）
        if (binding.switchMonitor.isChecked && UsageStatsHelper.hasUsageAccess(this)) {
            if (!settings.monitoringEnabled) {
                settings.monitoringEnabled = true
                UsageMonitorService.start(this)
                Toast.makeText(this, "自律监控已开启", Toast.LENGTH_SHORT).show()
            }
        }
        refreshUsage()
    }

    // ---------- 权限 ----------

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun updatePermissionUi() {
        val granted = UsageStatsHelper.hasUsageAccess(this)
        binding.tvPermissionStatus.text =
            if (granted) "✅ 已授予：可以检测手机使用时长"
            else "⚠️ 未授予：无法检测手机使用时长"
        binding.btnGrantPermission.visibility = if (granted) View.GONE else View.VISIBLE
    }

    // ---------- 开关 ----------

    private fun setupSwitch() {
        binding.switchMonitor.setOnCheckedChangeListener { _, checked ->
            if (updatingSwitch) return@setOnCheckedChangeListener
            if (checked) enableMonitoring() else disableMonitoring()
        }
    }

    private fun syncSwitchState() {
        updatingSwitch = true
        binding.switchMonitor.isChecked = settings.monitoringEnabled
        updatingSwitch = false
    }

    private fun enableMonitoring() {
        if (!UsageStatsHelper.hasUsageAccess(this)) {
            Toast.makeText(this, "请先授予「使用情况访问」权限", Toast.LENGTH_SHORT).show()
            UsageStatsHelper.openUsageAccessSettings(this)
            return
        }
        settings.monitoringEnabled = true
        UsageMonitorService.start(this)
        Toast.makeText(this, "自律监控已开启", Toast.LENGTH_SHORT).show()
    }

    private fun disableMonitoring() {
        settings.monitoringEnabled = false
        stopService(Intent(this, UsageMonitorService::class.java))
        Toast.makeText(this, "自律监控已关闭", Toast.LENGTH_SHORT).show()
    }

    // ---------- 界面 ----------

    private fun setupCards() {
        binding.cardTasks.setOnClickListener {
            startActivity(Intent(this, TaskListActivity::class.java))
        }
        binding.cardSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        // 测试入口：直接弹出自律提醒，方便快速验证提醒效果
        binding.cardTestReminder.setOnClickListener {
            val testUsage = settings.dailyThresholdMinutes * 60_000L
            startActivity(
                Intent(this, ReminderActivity::class.java)
                    .putExtra(ReminderActivity.EXTRA_USAGE, testUsage)
                    .putExtra(ReminderActivity.EXTRA_TASK_ID, -1L)
            )
        }
        binding.btnGrantPermission.setOnClickListener {
            UsageStatsHelper.openUsageAccessSettings(this)
        }
    }

    private fun refreshUsage() {
        if (UsageStatsHelper.hasUsageAccess(this)) {
            val usage = UsageStatsHelper.getTodayUsageMillis(this, settings.excludeSelf)
            showUsage(usage)
        } else {
            showUsage(0L)
        }
    }

    private fun showUsage(usageMillis: Long) {
        binding.tvUsage.text = TimeFormat.formatDuration(usageMillis)

        val threshold = settings.dailyThresholdMinutes * 60_000L
        val progress = if (threshold > 0) (usageMillis.toFloat() / threshold).coerceIn(0f, 1f) else 0f
        binding.progressUsage.progress = (progress * 100).toInt()

        binding.tvUsageHint.text = if (usageMillis >= threshold) {
            "已达到提醒阈值（${settings.dailyThresholdMinutes} 分钟），该放下手机啦！"
        } else {
            "提醒阈值 ${settings.dailyThresholdMinutes} 分钟，还差 ${TimeFormat.formatDuration(threshold - usageMillis)}"
        }
    }
}

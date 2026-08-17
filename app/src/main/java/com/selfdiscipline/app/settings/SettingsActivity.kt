package com.selfdiscipline.app.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.selfdiscipline.app.data.AppSettings
import com.selfdiscipline.app.databinding.ActivitySettingsBinding
import com.selfdiscipline.app.reminder.ReminderActivity
import com.selfdiscipline.app.service.UsageMonitorService
import com.selfdiscipline.app.service.UsageStatsHelper
import com.selfdiscipline.app.task.TaskListActivity
import com.selfdiscipline.app.util.DeviceCompat

/**
 * 设置页：
 *  - 自律提醒开关（开启/关闭）
 *  - 提醒设置：使用时长阈值、提醒间隔
 *  - 我的任务清单入口
 *  - 模拟触发自律提醒（测试）入口
 *  - 权限引导（仅未授权时显示）、电池优化、系统适配（仅小米系）
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val settings by lazy { AppSettings(this) }
    private var updatingSwitch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.etThreshold.setText(settings.dailyThresholdMinutes.toString())
        binding.etInterval.setText(settings.remindIntervalMinutes.toString())

        setupSwitch()
        setupEntries()
        updatePermissionBanner()
        updateBatteryUi()
        setupDeviceCompat()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionBanner()
        updateBatteryUi()
        syncSwitchState()
    }

    // ---------- 自律开关 ----------

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
            syncSwitchState()
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

    // ---------- 入口 ----------

    private fun setupEntries() {
        binding.cardTaskList.setOnClickListener {
            startActivity(Intent(this, TaskListActivity::class.java))
        }
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
        binding.btnBattery.setOnClickListener { requestIgnoreBatteryOptimization() }
        binding.btnSave.setOnClickListener { save() }
    }

    private fun save() {
        val threshold = binding.etThreshold.text?.toString()?.toIntOrNull() ?: 60
        val interval = binding.etInterval.text?.toString()?.toIntOrNull() ?: 30
        settings.dailyThresholdMinutes = threshold
        settings.remindIntervalMinutes = interval
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
    }

    // ---------- 权限 / 电池 / 系统适配 ----------

    private fun updatePermissionBanner() {
        val granted = UsageStatsHelper.hasUsageAccess(this)
        // 仅未授权时显示
        binding.cardPermission.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun updateBatteryUi() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val ignoring = pm.isIgnoringBatteryOptimizations(packageName)
        binding.tvBatteryStatus.text =
            if (ignoring) "已允许 · 后台更稳定" else "未允许 · 建议开启"
        binding.btnBattery.visibility = if (ignoring) View.GONE else View.VISIBLE
    }

    private fun setupDeviceCompat() {
        if (!DeviceCompat.isXiaomiDevice()) {
            binding.cardDeviceCompat.visibility = View.GONE
            return
        }
        binding.cardDeviceCompat.visibility = View.VISIBLE
        binding.tvDeviceCompatStatus.text =
            "检测到 ${DeviceCompat.deviceName()}，建议完成：自启动 → 后台弹出界面 → 省电策略"
        binding.btnAutoStart.setOnClickListener { DeviceCompat.openAutoStartSettings(this) }
        binding.btnPermEditor.setOnClickListener { DeviceCompat.openPermissionEditor(this) }
        binding.btnAppDetails.setOnClickListener { DeviceCompat.openAppDetails(this) }
    }

    private fun requestIgnoreBatteryOptimization() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

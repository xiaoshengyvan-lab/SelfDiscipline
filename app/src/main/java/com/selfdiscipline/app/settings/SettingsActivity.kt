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
import com.selfdiscipline.app.service.UsageMonitorService
import com.selfdiscipline.app.service.UsageStatsHelper
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
        setupRemindCollapse()
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
        // 会话计时基于屏幕事件，无需「使用情况访问」权限即可工作
        settings.monitoringEnabled = true
        UsageMonitorService.start(this)
        Toast.makeText(this, "自律监控已开启", Toast.LENGTH_SHORT).show()
    }

    private fun disableMonitoring() {
        settings.monitoringEnabled = false
        stopService(Intent(this, UsageMonitorService::class.java))
        Toast.makeText(this, "自律监控已关闭", Toast.LENGTH_SHORT).show()
    }

    // ---------- 提醒设置折叠 ----------

    private fun setupRemindCollapse() {
        var expanded = false
        binding.remindHeader.setOnClickListener {
            expanded = !expanded
            binding.layoutRemindFields.visibility = if (expanded) View.VISIBLE else View.GONE
            binding.ivRemindArrow.animate().rotation(if (expanded) 180f else 0f).setDuration(200).start()
        }
    }

    // ---------- 入口 ----------

    private fun setupEntries() {
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

    /**
     * 系统适配卡片：按设备品牌展示对应权限引导与「动态岛通知」说明
     * （小米超级岛 / vivo 焦点通知 / 荣耀灵动胶囊 / OPPO 流体云 / 华为实况窗）
     */
    private fun setupDeviceCompat() {
        val family = DeviceCompat.deviceFamily()
        if (family == "other") {
            binding.cardDeviceCompat.visibility = View.GONE
            return
        }
        binding.cardDeviceCompat.visibility = View.VISIBLE

        when (family) {
            "honor" -> {
                binding.tvDeviceCompatStatus.text =
                    "检测到 ${DeviceCompat.deviceName()}（荣耀），建议完成：应用启动管理 → 电池优化 → 灵动胶囊"
                binding.tvCompatHint.text =
                    "提示：提醒页全屏弹出需在「应用管理 → 权限」中允许「应用内其他界面」（后台弹窗）"
                binding.btnAutoStart.text = "自启动"
                binding.btnPermEditor.text = "电池优化"
                binding.btnAppDetails.text = "应用详情"
                binding.btnAutoStart.setOnClickListener { DeviceCompat.openAutoStartSettings(this) }
                binding.btnPermEditor.setOnClickListener { requestIgnoreBatteryOptimization() }
                binding.btnAppDetails.setOnClickListener { DeviceCompat.openAppDetails(this) }
            }
            "xiaomi" -> {
                binding.tvDeviceCompatStatus.text =
                    "检测到 ${DeviceCompat.deviceName()}，建议完成：自启动 → 后台弹出界面 → 省电策略"
                binding.tvCompatHint.text =
                    "提示：提醒全屏弹出需允许「后台弹出界面」权限"
                binding.btnAutoStart.text = "自启动"
                binding.btnPermEditor.text = "后台弹出界面"
                binding.btnAppDetails.text = "省电策略"
                binding.btnAutoStart.setOnClickListener { DeviceCompat.openAutoStartSettings(this) }
                binding.btnPermEditor.setOnClickListener { DeviceCompat.openPermissionEditor(this) }
                binding.btnAppDetails.setOnClickListener { DeviceCompat.openAppDetails(this) }
            }
            else -> {
                // vivo / oppo / huawei：通用引导
                binding.tvDeviceCompatStatus.text =
                    "检测到 ${DeviceCompat.deviceName()}，建议完成：自启动 → 电池优化"
                binding.tvCompatHint.text =
                    "提示：提醒全屏弹出需允许应用「后台弹窗 / 悬浮窗」类权限"
                binding.btnAutoStart.text = "自启动"
                binding.btnPermEditor.text = "电池优化"
                binding.btnAppDetails.text = "应用详情"
                binding.btnAutoStart.setOnClickListener { DeviceCompat.openAutoStartSettings(this) }
                binding.btnPermEditor.setOnClickListener { requestIgnoreBatteryOptimization() }
                binding.btnAppDetails.setOnClickListener { DeviceCompat.openAppDetails(this) }
            }
        }

        // 动态岛类通知引导（通知到达时上岛/上胶囊显示）
        val islandLabel = when (family) {
            "xiaomi" -> "超级岛：提醒通知到达时上岛显示"
            "honor" -> "灵动胶囊：提醒通知到达时上胶囊显示"
            "vivo" -> "焦点通知：提醒通知到达时以焦点通知显示"
            "oppo" -> "流体云：提醒通知到达时上流体云显示"
            "huawei" -> "实况窗：提醒通知到达时上实况窗显示"
            else -> null
        }
        if (islandLabel != null) {
            binding.layoutSuperIsland.visibility = View.VISIBLE
            binding.tvSuperIslandText.text = islandLabel
            binding.btnSuperIsland.setOnClickListener { openAppNotificationSettings() }
        } else {
            binding.layoutSuperIsland.visibility = View.GONE
        }
    }

    /** 打开本应用通知设置页（各品牌动态岛开关均在此附近） */
    private fun openAppNotificationSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
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

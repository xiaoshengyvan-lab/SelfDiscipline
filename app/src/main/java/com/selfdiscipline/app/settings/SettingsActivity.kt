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
import com.selfdiscipline.app.service.UsageStatsHelper
import com.selfdiscipline.app.util.DeviceCompat

/**
 * 提醒设置：
 *  - 每日使用时长阈值（分钟）：使用达到该时长后提醒
 *  - 提醒最小间隔（分钟）：避免频繁打扰
 *  - 是否忽略本应用自身使用时间
 *  - 使用情况访问权限、电池优化引导
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val settings by lazy { AppSettings(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.etThreshold.setText(settings.dailyThresholdMinutes.toString())
        binding.etInterval.setText(settings.remindIntervalMinutes.toString())
        binding.switchExcludeSelf.isChecked = settings.excludeSelf

        binding.btnSave.setOnClickListener { save() }
        binding.btnGrantPermission.setOnClickListener {
            UsageStatsHelper.openUsageAccessSettings(this)
        }
        binding.btnBattery.setOnClickListener { requestIgnoreBatteryOptimization() }

        updatePermissionUi()
        updateBatteryUi()
        setupDeviceCompat()
    }

    /**
     * 系统适配卡片：仅小米系（MIUI / HyperOS）设备显示，
     * 提供自启动、后台弹出界面、省电策略等专属权限页跳转。
     */
    private fun setupDeviceCompat() {
        if (!DeviceCompat.isXiaomiDevice()) {
            binding.cardDeviceCompat.visibility = View.GONE
            return
        }
        binding.cardDeviceCompat.visibility = View.VISIBLE
        binding.tvDeviceCompatStatus.text =
            "检测到 ${DeviceCompat.deviceName()} 系统。" +
                "为保证后台监控与提醒正常，请依次完成：自启动 → 后台弹出界面 → 省电策略（无限制）"
        binding.btnAutoStart.setOnClickListener { DeviceCompat.openAutoStartSettings(this) }
        binding.btnPermEditor.setOnClickListener { DeviceCompat.openPermissionEditor(this) }
        binding.btnAppDetails.setOnClickListener { DeviceCompat.openAppDetails(this) }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUi()
        updateBatteryUi()
    }

    private fun save() {
        val threshold = binding.etThreshold.text?.toString()?.toIntOrNull() ?: 60
        val interval = binding.etInterval.text?.toString()?.toIntOrNull() ?: 30
        settings.dailyThresholdMinutes = threshold
        settings.remindIntervalMinutes = interval
        settings.excludeSelf = binding.switchExcludeSelf.isChecked
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun updatePermissionUi() {
        val granted = UsageStatsHelper.hasUsageAccess(this)
        binding.tvPermissionStatus.text =
            if (granted) "✅ 已授予：可以检测手机使用时长"
            else "⚠️ 未授予：无法检测手机使用时长"
        binding.btnGrantPermission.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun updateBatteryUi() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val ignoring = pm.isIgnoringBatteryOptimizations(packageName)
        binding.tvBatteryStatus.text =
            if (ignoring) "✅ 已允许：后台监控更稳定"
            else "⚠️ 未允许：系统可能限制后台监控，建议开启"
        binding.btnBattery.visibility = if (ignoring) View.GONE else View.VISIBLE
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

package com.selfdiscipline.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 国产 ROM 设备检测与专属权限页跳转工具。
 *
 * 支持：
 *  - 小米系（MIUI / HyperOS / 澎湃 OS）：自启动、权限管理（后台弹出界面）、省电策略
 *  - 荣耀系（MagicOS）：应用启动管理（自启动）、电池优化、应用详情
 *
 * 国产 ROM 对后台服务管控较严，为保证后台监控与提醒正常，用户需手动授予对应权限。
 * 跳转失败时统一回退到系统应用详情页。
 */
object DeviceCompat {

    /** 设备家族：xiaomi / honor / other */
    fun deviceFamily(): String = when {
        isHonorDevice() -> "honor"
        isXiaomiDevice() -> "xiaomi"
        else -> "other"
    }

    /** 是否为小米系设备（小米 / 红米 / POCO，含 MIUI 与 HyperOS） */
    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        val brand = Build.BRAND?.lowercase() ?: ""
        val display = Build.DISPLAY?.lowercase() ?: ""
        val fingerprint = Build.FINGERPRINT?.lowercase() ?: ""
        return manufacturer.contains("xiaomi") ||
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ||
            display.contains("miui") || display.contains("hyperos") ||
            fingerprint.contains("xiaomi") ||
            isMiuiRom() || isHyperOs()
    }

    /** 是否为荣耀设备（MagicOS） */
    fun isHonorDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        val brand = Build.BRAND?.lowercase() ?: ""
        val display = Build.DISPLAY?.lowercase() ?: ""
        val fingerprint = Build.FINGERPRINT?.lowercase() ?: ""
        return manufacturer.contains("honor") || brand.contains("honor") ||
            display.contains("magicos") || fingerprint.contains("honor")
    }

    /** 是否为 MagicOS（荣耀系统） */
    fun isMagicOs(): Boolean =
        (Build.DISPLAY?.lowercase() ?: "").contains("magicos")

    /** 是否为 HyperOS（澎湃 OS） */
    fun isHyperOs(): Boolean {
        val osVersion = getSystemProperty("ro.mi.os.version.name")
        val display = Build.DISPLAY?.lowercase() ?: ""
        return !osVersion.isNullOrBlank() || display.contains("hyperos")
    }

    /** 是否为 MIUI */
    fun isMiuiRom(): Boolean {
        val uiVersion = getSystemProperty("ro.miui.ui.version.name")
        return !uiVersion.isNullOrBlank()
    }

    /** 检测到的系统名，用于提示文案 */
    fun deviceName(): String = when {
        isMagicOs() -> "MagicOS"
        isHonorDevice() -> "Honor"
        isHyperOs() -> "HyperOS"
        isMiuiRom() -> "MIUI"
        else -> "Android"
    }

    // ---------- 权限页跳转 ----------

    /** 打开系统应用详情页（万能兜底页） */
    fun openAppDetails(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // 忽略：极端情况下无法跳转
        }
    }

    /** 跳转自启动 / 应用启动管理页（按设备分支），失败则打开应用详情页 */
    fun openAutoStartSettings(context: Context) {
        val candidates = when (deviceFamily()) {
            "honor" -> listOf(
                // 荣耀 MagicOS 手机管家（组件名随版本可能有差异，逐个尝试）
                "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmanager.StartupManagerActivity",
                "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                // 部分荣耀机型沿用华为手机管家
                "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
            "xiaomi" -> listOf(
                "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            else -> emptyList()
        }
        openFirstAvailable(context, candidates)
    }

    /** 跳转应用权限管理页（小米系：包含“后台弹出界面”等开关），失败则打开应用详情页 */
    fun openPermissionEditor(context: Context) {
        try {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                .putExtra("extra_pkgname", context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppDetails(context)
        }
    }

    // ---------- 内部工具 ----------

    /** 按顺序尝试打开组件，全部失败则回退到应用详情页 */
    private fun openFirstAvailable(context: Context, candidates: List<Pair<String, String>>) {
        for ((pkg, cls) in candidates) {
            try {
                context.startActivity(
                    Intent().setComponent(ComponentName(pkg, cls))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            } catch (e: Exception) {
                // 尝试下一个
            }
        }
        openAppDetails(context)
    }

    /** 读取系统属性（如 ro.miui.ui.version.name / ro.mi.os.version.name） */
    private fun getSystemProperty(name: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, name) as? String
        } catch (e: Exception) {
            null
        }
    }
}

package com.selfdiscipline.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 小米 MIUI / HyperOS（澎湃 OS）设备检测与专属权限页跳转工具。
 *
 * 小米系 ROM 对后台服务管控较严，为保证本应用的后台监控与提醒正常，
 * 用户通常需要手动授予：自启动、后台弹出界面、省电策略（无限制）等权限。
 * 本工具用于检测设备并尽量直达对应设置页（无法直达时回退到应用详情页）。
 */
object DeviceCompat {

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
        isHyperOs() -> "HyperOS"
        isMiuiRom() -> "MIUI"
        else -> "Android"
    }

    // ---------- 权限页跳转 ----------

    /** 打开系统应用详情页（万能兜底页，可手动进入省电策略等入口） */
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

    /** 跳转自启动管理页（MIUI/HyperOS 专属），失败则打开应用详情页 */
    fun openAutoStartSettings(context: Context) {
        try {
            val component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            context.startActivity(
                Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            openAppDetails(context)
        }
    }

    /** 跳转应用权限管理页（包含“后台弹出界面”等开关），失败则打开应用详情页 */
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

    /** 读取小米系统属性（如 ro.miui.ui.version.name / ro.mi.os.version.name） */
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

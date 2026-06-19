package com.example.locationapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog

object BatteryOptimizationHelper {
    private const val TAG = "Battery"

    fun showGuidanceDialog(ctx: Context) {
        val mfr = Build.MANUFACTURER.lowercase()
        val hint = when {
            mfr.contains("xiaomi") || mfr.contains("redmi") -> "小米: 设置→应用→授权管理→自启动\n"
            mfr.contains("huawei") || mfr.contains("honor") -> "华为: 手机管家→启动管理\n"
            else -> ""
        }
        try {
            AlertDialog.Builder(ctx)
                .setTitle("后台保活设置")
                .setMessage("${hint}请关闭电池优化并开启自启动")
                .setPositiveButton("关闭电池优化") { _, _ -> openBattery(ctx) }
                .setNeutralButton("自启动管理") { _, _ -> openAuto(ctx) }
                .setNegativeButton("稍后", null).show()
        } catch (e: Exception) { Log.e(TAG, "dialog: ${e.message}") }
    }

    private fun openBattery(ctx: Context) {
        try { ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${ctx.packageName}") }) }
        catch (e: Exception) { openDetails(ctx) }
    }

    private fun openAuto(ctx: Context) {
        val mfr = Build.MANUFACTURER.lowercase()
        val intent = when {
            mfr.contains("xiaomi") || mfr.contains("redmi") -> Intent().apply { component = android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity") }
            mfr.contains("huawei") || mfr.contains("honor") -> Intent().apply { component = android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity") }
            else -> null
        }
        try { if (intent != null) ctx.startActivity(intent) else openDetails(ctx) }
        catch (e: Exception) { openDetails(ctx) }
    }

    private fun openDetails(ctx: Context) { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", ctx.packageName, null) }) }
}

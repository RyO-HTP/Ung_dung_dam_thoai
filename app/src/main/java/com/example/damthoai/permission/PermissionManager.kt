package com.example.damthoai.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Task 1: Module quản lý hệ thống cấp quyền trên Android (Android Permission Management Module)
 * Task 2: Xin quyền Micro và Camera (Microphone & Camera Permissions)
 * Task 3: Xin quyền Mạng và Bluetooth (Network & Bluetooth Permissions)
 * Task 4: Xin quyền cho phép ứng dụng Chạy nền (Background Execution & Battery Optimization Exemption)
 */
object PermissionManager {

    // --- Task 2: Micro & Camera Permissions ---
    val AUDIO_CAMERA_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    )

    fun hasAudioAndCameraPermissions(context: Context): Boolean {
        return AUDIO_CAMERA_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // --- Task 3: Network & Bluetooth Permissions ---
    val NETWORK_BLUETOOTH_PERMISSIONS = mutableListOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_WIFI_STATE
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }.toTypedArray()

    fun hasNetworkAndBluetoothPermissions(context: Context): Boolean {
        return NETWORK_BLUETOOTH_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // --- Task 4: Background Execution & Battery Optimization Permissions ---
    val BACKGROUND_EXECUTION_PERMISSIONS = mutableListOf(
        Manifest.permission.WAKE_LOCK,
        Manifest.permission.FOREGROUND_SERVICE
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(Manifest.permission.FOREGROUND_SERVICE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
            add(Manifest.permission.FOREGROUND_SERVICE_CAMERA)
        }
    }.toTypedArray()

    fun hasBackgroundExecutionPermissions(context: Context): Boolean {
        val basePermissionsGranted = BACKGROUND_EXECUTION_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        val batteryExemptionGranted = isIgnoringBatteryOptimizations(context)
        return basePermissionsGranted && batteryExemptionGranted
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }
    }

    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = activity.packageName
            val powerManager = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager?.isIgnoringBatteryOptimizations(packageName) == false) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to battery optimization settings
                    try {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        activity.startActivity(intent)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        }
    }

    // --- All Essential Permissions Combined ---
    val ALL_REQUIRED_PERMISSIONS: Array<String> by lazy {
        (AUDIO_CAMERA_PERMISSIONS + NETWORK_BLUETOOTH_PERMISSIONS + BACKGROUND_EXECUTION_PERMISSIONS).distinct().toTypedArray()
    }

    fun hasAllPermissions(context: Context): Boolean {
        return hasAudioAndCameraPermissions(context) &&
                hasNetworkAndBluetoothPermissions(context) &&
                hasBackgroundExecutionPermissions(context)
    }

    fun requestPermissions(activity: Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, ALL_REQUIRED_PERMISSIONS, requestCode)
    }
}

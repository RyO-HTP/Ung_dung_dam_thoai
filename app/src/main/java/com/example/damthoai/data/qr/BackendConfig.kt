/**
 * Path: app/src/main/java/com/example/damthoai/data/qr/BackendConfig.kt
 *
 * Khối KDoc tiếng Việt:
 * Cấu hình địa chỉ máy chủ (Backend URL) và lưu trữ SharedPreferences.
 * 
 * ĐIỂM NỐI TV5: đặt BackendConfig.baseUrl khi biết địa chỉ máy chủ thực tế.
 */
package com.example.damthoai.data.qr

import android.content.Context
import android.content.SharedPreferences

object BackendConfig {
    private const val PREFS_NAME = "damthoai_backend_config"
    private const val KEY_BASE_URL = "base_url"

    var baseUrl: String? = null
        private set

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        baseUrl = prefs.getString(KEY_BASE_URL, null)
    }

    fun setBaseUrl(context: Context, url: String?) {
        baseUrl = url
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BASE_URL, url).apply()
    }
}

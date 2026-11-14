/**
 * Path: app/src/main/java/com/example/damthoai/data/qr/BackendConfig.kt
 *
 * Khối KDoc tiếng Việt:
 * Cấu hình địa chỉ máy chủ (Backend URL) và lưu trữ SharedPreferences.
 * 
 * ĐIỂM NỐI TV5: đặt BackendConfig.baseUrl khi biết địa chỉ máy chủ thực tế.
 */
package com.example.damthoai.data.qr

import com.example.damthoai.platform.createSettings

object BackendConfig {
    private const val PREFS_NAME = "damthoai_backend_config"
    private const val KEY_BASE_URL = "base_url"

    var baseUrl: String? = null
        private set

    private val prefs by lazy { createSettings(PREFS_NAME) }

    fun init() {
        baseUrl = prefs.getStringOrNull(KEY_BASE_URL)
    }

    fun setBaseUrl(url: String?) {
        baseUrl = url
        if (url == null) prefs.remove(KEY_BASE_URL) else prefs.putString(KEY_BASE_URL, url)
    }
}

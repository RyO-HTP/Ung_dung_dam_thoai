/**
 * Path: app/src/main/java/com/example/damthoai/data/qr/BackendConfig.kt
 *
 * Khối KDoc tiếng Việt:
 * Cấu hình địa chỉ máy chủ (Backend URL) từ BuildConfig và lưu trữ SharedPreferences.
 */
package com.example.damthoai.data.qr

import android.content.Context
import com.example.damthoai.BuildConfig

object BackendConfig {
    private const val PREFS_NAME = "damthoai_backend_config"
    private const val KEY_BASE_URL = "base_url"

    val DEFAULT_BASE_URL: String = BuildConfig.BACKEND_BASE_URL.trim().removeSuffix("/")

    @Volatile
    var baseUrl: String? = DEFAULT_BASE_URL.takeIf { it.isNotBlank() }
        private set

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_BASE_URL, null)?.trim()?.removeSuffix("/")?.takeIf { it.isNotEmpty() }
        baseUrl = saved ?: DEFAULT_BASE_URL.takeIf { it.isNotBlank() }
    }

    fun setBaseUrl(context: Context, url: String?) {
        val normalized = url?.trim()?.removeSuffix("/")?.takeIf { it.isNotEmpty() }
        baseUrl = normalized ?: DEFAULT_BASE_URL.takeIf { it.isNotBlank() }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (normalized != null) {
            prefs.edit().putString(KEY_BASE_URL, normalized).apply()
        } else {
            prefs.edit().remove(KEY_BASE_URL).apply()
        }
    }

    internal fun setBaseUrlForTest(url: String?) {
        baseUrl = url?.trim()?.removeSuffix("/")?.takeIf { it.isNotEmpty() }
    }
}

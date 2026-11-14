package com.example.damthoai.platform

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

/** Context ứng dụng cho code đa nền tảng. Gán trong MainActivity/Application trước khi dùng. */
@SuppressLint("StaticFieldLeak")
object AndroidAppContext {
    lateinit var context: Context
        private set

    fun init(context: Context) {
        this.context = context.applicationContext
    }
}

actual fun createSettings(name: String): Settings =
    SharedPreferencesSettings(AndroidAppContext.context.getSharedPreferences(name, Context.MODE_PRIVATE))

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun monotonicMillis(): Long = try {
    SystemClock.elapsedRealtime()
} catch (_: RuntimeException) {
    System.nanoTime() / 1_000_000 // unit test JVM
}

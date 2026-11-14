package com.example.damthoai.util

/**
 * Ghi log dùng chung cho Android và iOS (thay cho android.util.Log trong code đa nền tảng).
 * Android: chuyển sang android.util.Log (xem trong Logcat). iOS: in ra console Xcode qua NSLog.
 */
object Log {
    fun v(tag: String, msg: String) = platformLog(LogLevel.VERBOSE, tag, msg, null)
    fun d(tag: String, msg: String) = platformLog(LogLevel.DEBUG, tag, msg, null)
    fun i(tag: String, msg: String) = platformLog(LogLevel.INFO, tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = platformLog(LogLevel.WARN, tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = platformLog(LogLevel.ERROR, tag, msg, t)
}

enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }

internal expect fun platformLog(level: LogLevel, tag: String, msg: String, t: Throwable?)

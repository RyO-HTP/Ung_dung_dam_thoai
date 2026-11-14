package com.example.damthoai.util

internal actual fun platformLog(level: LogLevel, tag: String, msg: String, t: Throwable?) {
    // Unit test JVM không có android.util.Log thật -> bỏ qua lỗi "not mocked".
    try {
        when (level) {
            LogLevel.VERBOSE -> android.util.Log.v(tag, msg, t)
            LogLevel.DEBUG -> android.util.Log.d(tag, msg, t)
            LogLevel.INFO -> android.util.Log.i(tag, msg, t)
            LogLevel.WARN -> android.util.Log.w(tag, msg, t)
            LogLevel.ERROR -> android.util.Log.e(tag, msg, t)
        }
    } catch (_: RuntimeException) {
        println("[$level] $tag: $msg")
    }
}

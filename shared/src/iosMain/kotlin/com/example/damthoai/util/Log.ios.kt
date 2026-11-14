package com.example.damthoai.util

import platform.Foundation.NSLog

internal actual fun platformLog(level: LogLevel, tag: String, msg: String, t: Throwable?) {
    val suffix = t?.let { " | ${it.message}" } ?: ""
    NSLog("[%@] %@: %@", level.name, tag, msg + suffix)
}

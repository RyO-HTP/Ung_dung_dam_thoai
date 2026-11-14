package com.example.damthoai.platform

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSDate
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970

actual fun createSettings(name: String): Settings =
    NSUserDefaultsSettings(NSUserDefaults(suiteName = name))

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun monotonicMillis(): Long = (NSProcessInfo.processInfo.systemUptime * 1000).toLong()

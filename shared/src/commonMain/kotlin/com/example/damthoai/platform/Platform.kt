package com.example.damthoai.platform

import com.russhwolf.settings.Settings

/** Kho key-value bền vững theo tên (Android SharedPreferences / iOS NSUserDefaults suite). */
expect fun createSettings(name: String): Settings

/** Thời gian thực (ms từ 1/1/1970), thay cho System.currentTimeMillis(). */
expect fun currentTimeMillis(): Long

/** Đồng hồ đơn điệu (ms), dùng đo khoảng thời gian / chống dội phím. */
expect fun monotonicMillis(): Long

/** Định dạng số thực với [decimals] chữ số thập phân (thay cho String.format("%.1f")). */
fun Double.formatDecimals(decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val rounded = kotlin.math.round(this * factor).toLong()
    val negative = rounded < 0
    val abs = kotlin.math.abs(rounded)
    val intPart = abs / factor
    val frac = (abs % factor).toString().padStart(decimals, '0')
    val sign = if (negative) "-" else ""
    return if (decimals == 0) "$sign$intPart" else "$sign$intPart.$frac"
}

/** Thêm số 0 phía trước cho đủ [length] chữ số (thay cho "%02d"). */
fun Int.pad(length: Int = 2): String = toString().padStart(length, '0')
fun Long.pad(length: Int = 2): String = toString().padStart(length, '0')

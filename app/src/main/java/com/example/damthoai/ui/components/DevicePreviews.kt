/**
 * Path: app/src/main/java/com/example/damthoai/ui/components/DevicePreviews.kt
 *
 * Khối KDoc tiếng Việt:
 * Annotation Multipreview tổ hợp các cấu hình thiết bị và độ lớn font chữ khác nhau
 * phục vụ kiểm tra giao diện responsive (320dp, 360dp, 390dp, 412dp, Tablet 600dp, FontScale 1.3).
 */
package com.example.damthoai.ui.components

import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "320x568 (Small Phone)", widthDp = 320, heightDp = 568, backgroundColor = 0xFF070B24, showBackground = true)
@Preview(name = "360x640", widthDp = 360, heightDp = 640, backgroundColor = 0xFF070B24, showBackground = true)
@Preview(name = "360x800", widthDp = 360, heightDp = 800, backgroundColor = 0xFF070B24, showBackground = true)
@Preview(name = "390x844 (Standard Phone)", widthDp = 390, heightDp = 844, backgroundColor = 0xFF070B24, showBackground = true)
@Preview(name = "412x915 (Large Phone)", widthDp = 412, heightDp = 915, backgroundColor = 0xFF070B24, showBackground = true)
@Preview(name = "600x960 (Tablet Portrait)", widthDp = 600, heightDp = 960, backgroundColor = 0xFF070B24, showBackground = true)
@Preview(name = "390x844 (FontScale 1.3)", widthDp = 390, heightDp = 844, fontScale = 1.3f, backgroundColor = 0xFF070B24, showBackground = true)
annotation class DevicePreviews

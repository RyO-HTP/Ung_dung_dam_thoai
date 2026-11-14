package com.example.damthoai.util

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

actual fun plainTextClipEntry(text: String): ClipEntry =
    ClipEntry(ClipData.newPlainText("text", text))

actual suspend fun ClipEntry.readPlainText(): String? =
    clipData.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

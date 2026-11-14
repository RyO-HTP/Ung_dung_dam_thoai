package com.example.damthoai.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Bọc [Clipboard] (API suspend thay cho LocalClipboardManager đã deprecated) thành
 * hàm thường để gọi thẳng từ onClick. Cách tạo/đọc [ClipEntry] khác nhau giữa
 * Android và iOS nên nằm ở [plainTextClipEntry] / [readPlainText] (expect/actual).
 */
class TextClipboard internal constructor(
    private val clipboard: Clipboard,
    private val scope: CoroutineScope
) {
    fun copy(text: String) {
        scope.launch { clipboard.setClipEntry(plainTextClipEntry(text)) }
    }

    fun paste(onText: (String) -> Unit) {
        scope.launch {
            clipboard.getClipEntry()?.readPlainText()?.let(onText)
        }
    }
}

@Composable
fun rememberTextClipboard(): TextClipboard {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) { TextClipboard(clipboard, scope) }
}

expect fun plainTextClipEntry(text: String): ClipEntry

expect suspend fun ClipEntry.readPlainText(): String?

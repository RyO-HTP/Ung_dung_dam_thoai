package com.example.damthoai.util

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry.withPlainText(text)

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun ClipEntry.readPlainText(): String? = getPlainText()

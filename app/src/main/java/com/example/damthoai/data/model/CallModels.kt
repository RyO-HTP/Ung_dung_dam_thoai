package com.example.damthoai.data.model

import androidx.compose.ui.graphics.Color

data class CallChannel(
    val iconRes: Int,
    val name: String,
    val valueLabel: String,
    val volume: Float,
    val speaker: String,
    val speakerColor: Color,
    val tag: String? = null,
    val isDucked: Boolean = tag?.contains("HẠ ÂM") == true,
    val userVolume: Float = volume
)

data class CallContent(
    val statusTitle: String,
    val statusDescription: String,
    val statusIconRes: Int,
    val statusColor: Color,
    val statusBackground: Color,
    val channels: List<CallChannel>
)

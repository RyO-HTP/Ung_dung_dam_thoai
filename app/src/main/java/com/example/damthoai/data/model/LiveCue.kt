package com.example.damthoai.data.model

data class LiveCue(
    val title: String,
    val message: String,
    val channelName: String,
    val isPriority: Boolean = false
)

package com.example.damthoai.data.model

data class EmergencyCallParticipant(
    val member: Member,
    val status: String,
    val isSpeaking: Boolean = false
)

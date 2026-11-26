package com.example.damthoai.data.model

enum class ChannelValidationError {
    EMPTY_NAME,
    DUPLICATE_NAME,
    CHANNEL_LIMIT_REACHED,
    EVENT_NOT_FOUND,
    CHANNEL_NOT_FOUND
}

sealed interface ChannelMutationResult {
    data class Success(val channel: Channel) : ChannelMutationResult
    data class Rejected(val reason: ChannelValidationError) : ChannelMutationResult
}

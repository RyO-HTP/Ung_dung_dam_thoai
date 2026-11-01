package com.example.damthoai.data

import android.content.Context

/**
 * Lưu các cờ phục vụ xác định luồng khởi động ứng dụng.
 *
 * ĐIỂM NỐI TV5: thay hasJoinedEvent bằng phiên sự kiện thật.
 */
object AppStartPrefs {
    private const val PREFERENCES_NAME = "app_start_preferences"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_HAS_JOINED_EVENT = "has_joined_event"

    /** Đọc trạng thái người dùng đã hoàn tất giới thiệu hay chưa. */
    fun isOnboardingDone(context: Context): Boolean =
        preferences(context).getBoolean(KEY_ONBOARDING_DONE, false)

    /** Ghi trạng thái hoàn tất giới thiệu. */
    fun setOnboardingDone(context: Context, done: Boolean) {
        preferences(context).edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    /** Đọc trạng thái người dùng hiện đang tham gia sự kiện hay không. */
    fun hasJoinedEvent(context: Context): Boolean =
        preferences(context).getBoolean(KEY_HAS_JOINED_EVENT, false)

    /** Ghi trạng thái tham gia sự kiện. */
    fun setHasJoinedEvent(context: Context, joined: Boolean) {
        preferences(context).edit().putBoolean(KEY_HAS_JOINED_EVENT, joined).apply()
    }

    /** Xóa toàn bộ cờ luồng khởi động để kiểm thử lại trải nghiệm người mới. */
    fun clearAll(context: Context) {
        preferences(context).edit().clear().apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

package com.example.damthoai.hardware.audio

import android.util.Log

/**
 * Hợp đồng để TV2 nối thao tác mute và chọn tuyến với pipeline âm thanh thực tế.
 *
 * ĐIỂM NỐI TV2: cài đặt interface này và đưa instance vào
 * [AudioRouteGuard.installController]. Trong khi chưa có phần cài đặt của TV2, guard dùng
 * [LoggingAudioRouteController], chỉ ghi log và giữ trạng thái logic, không thể mute AudioTrack.
 */
interface AudioRouteController {
    fun currentRoute(): AudioRoute
    fun muteOutput()
    fun unmuteOutput()
    fun routeTo(route: AudioRoute)
}

/**
 * Bản giả để app biên dịch và mô phỏng trạng thái trước khi TV2 nối pipeline thực.
 */
internal class LoggingAudioRouteController : AudioRouteController {

    private var route = AudioRoute.SPEAKER

    override fun currentRoute(): AudioRoute = route

    override fun muteOutput() {
        Log.w(TAG, "Stub audio route controller: muteOutput chưa nối pipeline âm thanh")
    }

    override fun unmuteOutput() {
        Log.w(TAG, "Stub audio route controller: unmuteOutput chưa nối pipeline âm thanh")
    }

    override fun routeTo(route: AudioRoute) {
        this.route = route
        Log.w(TAG, "Stub audio route controller: yêu cầu tuyến $route chưa nối pipeline âm thanh")
    }

    private companion object {
        const val TAG = "AudioRouteController"
    }
}

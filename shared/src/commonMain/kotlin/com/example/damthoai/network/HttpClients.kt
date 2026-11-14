package com.example.damthoai.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import kotlinx.serialization.json.Json

/**
 * Tạo HttpClient với engine của từng nền tảng (Android: OkHttp, iOS: Darwin/NSURLSession).
 * [webSocketPingMs] > 0: bật heartbeat WebSocket chống rớt mạng.
 */
expect fun createHttpClient(
    webSocketPingMs: Long = 0,
    config: HttpClientConfig<*>.() -> Unit = {}
): HttpClient

/** JSON dùng chung, khớp hành vi Gson cũ: gửi cả giá trị mặc định, bỏ trường null, bỏ qua khóa lạ. */
val SignalingJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
    isLenient = true
}

package com.example.damthoai.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import java.util.concurrent.TimeUnit

actual fun createHttpClient(
    webSocketPingMs: Long,
    config: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(OkHttp) {
    engine {
        config {
            readTimeout(0, TimeUnit.MILLISECONDS) // Giữ kết nối WebSocket liên tục
            if (webSocketPingMs > 0) pingInterval(webSocketPingMs, TimeUnit.MILLISECONDS) // Heartbeat chống rớt mạng
            retryOnConnectionFailure(true)
        }
    }
    if (webSocketPingMs > 0) install(WebSockets)
    config()
}

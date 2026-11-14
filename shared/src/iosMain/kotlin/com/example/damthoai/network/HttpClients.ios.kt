package com.example.damthoai.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.WebSockets

actual fun createHttpClient(
    webSocketPingMs: Long,
    config: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(Darwin) {
    if (webSocketPingMs > 0) {
        install(WebSockets) {
            pingIntervalMillis = webSocketPingMs // Heartbeat chống rớt mạng
        }
    }
    config()
}

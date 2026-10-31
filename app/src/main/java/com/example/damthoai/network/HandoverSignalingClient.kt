package com.example.damthoai.network

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * =================================================================================
 * WEBSOCKET CLIENT CHO SEAMLESS HANDOVER & ICE GATHERING (Tuần 3)
 * Tương thích trực tiếp với server.js (cổng 8080) của Thành viên 1
 * =================================================================================
 */
class HandoverSignalingClient(
    private val clientScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job()),
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "HandoverSignalingClient"
        const val DEFAULT_SERVER_URL = "ws://10.0.2.2:8080" // Hoặc ws://localhost:8080
    }

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var currentUrl: String = DEFAULT_SERVER_URL
    private var currentUserId: String? = null
    private var shouldReconnect = true
    private var reconnectJob: Job? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    /**
     * Kết nối tới máy chủ WebSocket Tuần 3 (server.js - cổng 8080)
     */
    fun connect(serverUrl: String = DEFAULT_SERVER_URL, userId: String) {
        currentUrl = serverUrl
        currentUserId = userId
        shouldReconnect = true
        reconnectJob?.cancel()

        disconnectInternal()

        val request = try {
            Request.Builder().url(serverUrl).build()
        } catch (e: Exception) {
            Log.e(TAG, "URL WebSocket không hợp lệ: $serverUrl", e)
            return
        }

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Đã kết nối tới Signaling Server Tuần 3 ($serverUrl)")
                _isConnected.value = true

                // Gửi gói tin đăng ký (Register)
                val regMsg = mapOf("type" to "register", "user_id" to userId)
                webSocket.send(gson.toJson(regMsg))
                Log.d(TAG, "Đã gửi đăng ký User [$userId]")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Nhận tin nhắn: $text")
                _incomingMessages.tryEmit(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket đã đóng.")
                _isConnected.value = false
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Lỗi WebSocket Tuần 3: ${t.message}", t)
                _isConnected.value = false
                scheduleReconnect()
            }
        })
    }

    /**
     * Báo cáo chuyển mạng Seamless Handover lên Server (Tính năng chính Tuần 3)
     */
    fun sendNetworkChange(networkType: NetworkType): Boolean {
        val uid = currentUserId ?: return false
        val msg = mapOf(
            "type" to "network_change",
            "user_id" to uid,
            "network_type" to networkType.name
        )
        val json = gson.toJson(msg)
        Log.d(TAG, "[HANDOVER] Gửi thông báo chuyển mạng: $json")
        return webSocket?.send(json) ?: false
    }

    /**
     * Gửi ICE Candidate cho đối tác qua Server Tuần 3
     */
    fun sendIceCandidate(targetId: String, candidate: String): Boolean {
        val uid = currentUserId ?: return false
        val msg = mapOf(
            "type" to "ice_candidate",
            "user_id" to uid,
            "target_id" to targetId,
            "candidate" to candidate
        )
        return webSocket?.send(gson.toJson(msg)) ?: false
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        val uid = currentUserId ?: return
        reconnectJob?.cancel()
        reconnectJob = clientScope.launch {
            delay(3000)
            if (shouldReconnect) {
                Log.d(TAG, "Thử kết nối lại tới $currentUrl...")
                connect(currentUrl, uid)
            }
        }
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        disconnectInternal()
        _isConnected.value = false
    }

    private fun disconnectInternal() {
        try {
            webSocket?.close(1000, "Đóng phiên")
        } catch (ignored: Exception) {}
        webSocket = null
    }
}

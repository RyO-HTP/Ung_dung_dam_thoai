package com.example.damthoai.data.remote

import android.util.Log
import com.example.damthoai.data.model.LiveCue
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

data class LiveCuePayload(
    val type: String?,
    val eventId: Long,
    val revision: Long,
    val title: String?,
    val message: String?,
    val channelName: String?,
    val isPriority: Boolean,
    val liveCueHeader: String?,
    val liveCueTitle: String?,
    val liveCueSubtitle: String?
)

object LiveCueClient {
    private const val TAG = "LiveCueClient"
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentLiveCue = MutableStateFlow<LiveCue?>(null)
    val currentLiveCue: StateFlow<LiveCue?> = _currentLiveCue.asStateFlow()

    private var webSocket: WebSocket? = null
    private var isRunning = false
    private var reconnectAttempt = 0

    fun start(userId: String, eventId: Long, department: String = "ALL_EVENT") {
        if (isRunning) return
        isRunning = true
        reconnectAttempt = 0
        connect(userId, eventId, department)
    }

    private fun connect(userId: String, eventId: Long, department: String) {
        if (!isRunning) return
        scope.launch {
            try {
                val baseUrl = ApiClient.currentBaseUrl.replace("http://", "ws://").replace("https://", "wss://")
                val wsUrl = "$baseUrl/ws/live-cue?userId=$userId&eventId=$eventId&dept=$department"
                val request = Request.Builder().url(wsUrl).build()

                webSocket?.close(1000, "Switch socket")
                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "WebSocket LiveCue đã kết nối")
                        reconnectAttempt = 0
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val payload = gson.fromJson(text, LiveCuePayload::class.java)
                            if (payload != null) {
                                if (payload.type == "ERROR") {
                                    return
                                }
                                val title = payload.liveCueTitle ?: payload.title
                                val message = payload.liveCueSubtitle ?: payload.message ?: ""
                                if (title.isNullOrBlank() && message.isBlank()) {
                                    _currentLiveCue.value = null
                                    return
                                }
                                val channelName = payload.channelName ?: "Toàn sự kiện"
                                val isPriority = payload.isPriority
                                _currentLiveCue.value = LiveCue(
                                    title = title ?: "",
                                    message = message,
                                    channelName = channelName,
                                    isPriority = isPriority
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Lỗi parse LiveCue payload: ${e.message}")
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "WebSocket LiveCue lỗi/mất kết nối: ${t.message}")
                        reconnect(userId, eventId, department)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket LiveCue đóng: $reason")
                        reconnect(userId, eventId, department)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi kết nối LiveCue WS: ${e.message}")
                reconnect(userId, eventId, department)
            }
        }
    }

    private fun reconnect(userId: String, eventId: Long, department: String) {
        if (!isRunning) return
        scope.launch {
            reconnectAttempt++
            val delaySec = minOf(1 shl reconnectAttempt, 15)
            delay(delaySec * 1000L)
            if (isRunning) {
                connect(userId, eventId, department)
            }
        }
    }

    fun stop() {
        isRunning = false
        webSocket?.close(1000, "Client stop")
        webSocket = null
        _currentLiveCue.value = null
    }

    suspend fun fireCue(cueId: String, title: String, message: String, userId: String, eventId: Long): Result<Any> {
        val body = mapOf(
            "userId" to userId,
            "eventId" to eventId,
            "cueId" to cueId,
            "title" to title,
            "message" to message
        )
        return ApiClient.post<Any>("/api/v1/live-cue/fire", body)
    }
}

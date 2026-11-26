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

    fun start(userId: String, department: String = "STAGE") {
        if (isRunning) return
        isRunning = true
        scope.launch {
            while (isRunning) {
                val baseUrl = ApiClient.currentBaseUrl.replace("http://", "ws://").replace("https://", "wss://")
                val wsUrl = "$baseUrl/ws/live-cue?userId=$userId&dept=$department"
                val request = Request.Builder().url(wsUrl).build()

                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "WebSocket LiveCue đã kết nối")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val payload = gson.fromJson(text, LiveCuePayload::class.java)
                            if (payload != null) {
                                val title = payload.liveCueTitle ?: payload.title ?: "Tiết mục"
                                val message = payload.liveCueSubtitle ?: payload.message ?: ""
                                val channelName = payload.channelName ?: "Toàn sự kiện"
                                val isPriority = payload.isPriority
                                _currentLiveCue.value = LiveCue(
                                    title = title,
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
                        reconnect()
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket LiveCue đóng: $reason")
                        reconnect()
                    }
                })
                delay(10000)
            }
        }
    }

    private fun reconnect() {
        if (!isRunning) return
        scope.launch {
            delay(3000)
        }
    }

    fun stop() {
        isRunning = false
        webSocket?.close(1000, "Client stop")
        webSocket = null
    }

    suspend fun fireCue(cueId: String, title: String, message: String, actorName: String): Result<Any> {
        val body = mapOf(
            "cueId" to cueId,
            "title" to title,
            "message" to message,
            "actorName" to actorName
        )
        return ApiClient.post<Any>("/api/v1/live-cue/fire", body)
    }
}

package com.example.damthoai.data.remote

import android.util.Log
import com.example.damthoai.data.SpeakingStateRepository
import com.example.damthoai.hardware.PttController
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

object FloorClient {
    private const val TAG = "FloorClient"
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var webSocket: WebSocket? = null
    private var isRunning = false
    private var reconnectAttempt = 0

    fun start(userId: String, eventId: Long) {
        if (isRunning) return
        isRunning = true
        reconnectAttempt = 0
        connect(userId, eventId)
    }

    private fun connect(userId: String, eventId: Long) {
        if (!isRunning) return
        scope.launch {
            try {
                val baseUrl = ApiClient.currentBaseUrl.replace("http://", "ws://").replace("https://", "wss://")
                val wsUrl = "$baseUrl/ws/floor?userId=$userId&eventId=$eventId"
                val request = Request.Builder().url(wsUrl).build()

                webSocket?.close(1000, "Switch socket")
                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "WebSocket Floor đã kết nối")
                        reconnectAttempt = 0
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val json = gson.fromJson(text, JsonObject::class.java) ?: return
                            val type = json.get("type")?.asString

                            if (type == "FLOOR_SNAPSHOT") {
                                val holders = json.getAsJsonArray("holders")
                                if (holders != null) {
                                    for (h in holders) {
                                        val obj = h.asJsonObject
                                        val chCode = obj.get("channelCode")?.asString ?: "ch_01"
                                        val holderObj = obj.getAsJsonObject("activeHolder")
                                        if (holderObj != null) {
                                            val name = holderObj.get("displayName")?.asString ?: "Thành viên"
                                            val level = holderObj.get("userLevel")?.asInt ?: 3
                                            SpeakingStateRepository.onRemoteSpeaker(chCode, name, "Cấp $level", true)
                                        }
                                    }
                                }
                            } else if (type == "FLOOR_DECISION") {
                                val status = json.get("status")?.asString
                                val chCode = json.get("channelCode")?.asString ?: "ch_01"
                                val revokedUserId = json.get("revokedUserId")?.asString

                                if (revokedUserId != null && revokedUserId == userId) {
                                    PttController.forceReleasePtt()
                                }

                                when (status) {
                                    "FLOOR_GRANTED", "FLOOR_PREEMPTED" -> {
                                        val holderObj = json.getAsJsonObject("activeHolder")
                                        if (holderObj != null) {
                                            val hUserId = holderObj.get("userId")?.asString
                                            if (hUserId != userId) {
                                                val name = holderObj.get("displayName")?.asString ?: "Thành viên"
                                                val level = holderObj.get("userLevel")?.asInt ?: 3
                                                SpeakingStateRepository.onRemoteSpeaker(chCode, name, "Cấp $level", true)
                                            }
                                        }
                                    }
                                    "FLOOR_RELEASED" -> {
                                        SpeakingStateRepository.onRemoteSpeaker(chCode, "", "", false)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Lỗi parse Floor payload: ${e.message}")
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "WebSocket Floor lỗi: ${t.message}")
                        reconnect(userId, eventId)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket Floor đóng: $reason")
                        reconnect(userId, eventId)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi kết nối Floor WS: ${e.message}")
                reconnect(userId, eventId)
            }
        }
    }

    private fun reconnect(userId: String, eventId: Long) {
        if (!isRunning) return
        scope.launch {
            reconnectAttempt++
            val delaySec = minOf(1 shl reconnectAttempt, 15)
            delay(delaySec * 1000L)
            if (isRunning) {
                connect(userId, eventId)
            }
        }
    }

    fun stop() {
        isRunning = false
        webSocket?.close(1000, "Client stop")
        webSocket = null
    }
}

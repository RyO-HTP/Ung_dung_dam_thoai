package com.example.damthoai.data.remote

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object EventSession {
    private const val PREFS_NAME = "damthoai_event_session"
    private const val KEY_SESSION_JSON = "session_json"

    data class SessionData(
        val eventId: Long,
        val eventName: String,
        val pinCode: String,
        val userId: String,
        val userCode: String,
        val displayName: String,
        val roleTitle: String,
        val level: Int,
        val priority: Int,
        val primaryChannelId: String,
        val listeningChannels: List<Int>,
        val signalingUrl: String? = null,
        val signalingToken: String? = null,
        val adminCode: String? = null
    )

    private val gson = Gson()
    private var cachedSession: SessionData? = null

    private val _sessionState = MutableSharedFlow<SessionData?>(extraBufferCapacity = 1)
    val sessionState: SharedFlow<SessionData?> = _sessionState.asSharedFlow()

    // Reactive session for UI (header kênh, v.v.)
    private val _sessionFlow = MutableStateFlow<SessionData?>(null)
    val sessionFlow: StateFlow<SessionData?> = _sessionFlow.asStateFlow()

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SESSION_JSON, null)
        cachedSession = if (!json.isNullOrBlank()) {
            try {
                gson.fromJson(json, SessionData::class.java)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        _sessionFlow.value = cachedSession
        _sessionState.tryEmit(cachedSession)
    }

    fun hasSession(): Boolean = cachedSession != null

    fun getSession(): SessionData? = cachedSession

    fun saveSession(context: Context, session: SessionData) {
        cachedSession = session
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SESSION_JSON, gson.toJson(session)).apply()
        _sessionFlow.value = session
        _sessionState.tryEmit(session)
    }

    /** Cập nhật kênh primary (mic) sau khi user chọn mic / switch channel. */
    fun updatePrimaryChannel(context: Context?, channelId: String, listeningChannels: List<Int>? = null) {
        val current = cachedSession ?: return
        val updated = current.copy(
            primaryChannelId = channelId,
            listeningChannels = listeningChannels ?: current.listeningChannels
        )
        cachedSession = updated
        _sessionFlow.value = updated
        _sessionState.tryEmit(updated)
        context?.applicationContext?.let { ctx ->
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_SESSION_JSON, gson.toJson(updated)).apply()
        }
    }

    fun clearSession(context: Context) {
        cachedSession = null
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SESSION_JSON).apply()
        _sessionFlow.value = null
        _sessionState.tryEmit(null)
    }
}

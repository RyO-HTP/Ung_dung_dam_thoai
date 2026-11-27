package com.example.damthoai.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
    }

    fun hasSession(): Boolean = cachedSession != null

    fun getSession(): SessionData? = cachedSession

    fun saveSession(context: Context, session: SessionData) {
        cachedSession = session
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SESSION_JSON, gson.toJson(session)).apply()
    }

    fun clearSession(context: Context) {
        cachedSession = null
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SESSION_JSON).apply()
    }
}

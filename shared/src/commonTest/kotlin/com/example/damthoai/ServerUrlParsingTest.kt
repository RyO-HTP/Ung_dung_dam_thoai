package com.example.damthoai

import com.example.damthoai.network.ServerLocator
import com.example.damthoai.network.SignalingJson
import com.example.damthoai.network.model.IceServerConfig
import com.example.damthoai.network.model.RegisterMessage
import com.example.damthoai.network.model.WebRtcConfigMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phân tích URL máy chủ và JSON signaling — chạy trên cả JVM (Android) lẫn Kotlin/Native (iOS). */
class ServerUrlParsingTest {

    @Test
    fun portOf_readsPortOrFallsBackToDefault() {
        assertEquals(9000, ServerLocator.portOf("ws://192.168.1.8:9000"))
        assertEquals(8765, ServerLocator.portOf("ws://192.168.1.8"))
        assertEquals(8765, ServerLocator.portOf(""))
        assertEquals(8765, ServerLocator.portOf(null))
        assertEquals(443, ServerLocator.portOf("wss://abc.trycloudflare.com"))
        assertEquals(8765, ServerLocator.portOf("ws://example.com"))
    }

    @Test
    fun hostOf_handlesBlankAndInvalid() {
        assertEquals("192.168.1.8", ServerLocator.hostOf("ws://192.168.1.8:8765"))
        assertEquals("ung-dung-dam-thoai.onrender.com", ServerLocator.hostOf("wss://ung-dung-dam-thoai.onrender.com/path"))
        assertNull(ServerLocator.hostOf(""))
        assertNull(ServerLocator.hostOf(null))
        assertNull(ServerLocator.hostOf("ws://bad host:8765"))
    }

    @Test
    fun isLanUrl_andHealthUrl() {
        assertTrue(ServerLocator.isLanUrl("ws://192.168.1.8:8765"))
        assertTrue(ServerLocator.isLanUrl("ws://172.20.1.1:8765"))
        assertFalse(ServerLocator.isLanUrl("wss://ung-dung-dam-thoai.onrender.com"))
        assertEquals("https://abc.onrender.com/healthz", ServerLocator.healthUrlOf("wss://abc.onrender.com"))
        assertEquals("http://192.168.1.8:8765/healthz", ServerLocator.healthUrlOf("ws://192.168.1.8:8765"))
    }

    @Test
    fun registerMessage_keepsGsonCompatibleShape() {
        val json = SignalingJson.encodeToString(
            RegisterMessage.serializer(),
            RegisterMessage(userId = "User_1", eventPin = "123456")
        )
        // Server Python cần "type" luôn có mặt; trường null bị bỏ như Gson
        assertTrue(json.contains("\"type\":\"register\""), json)
        assertTrue(json.contains("\"user_id\":\"User_1\""), json)
        assertTrue(json.contains("\"is_organizer\":false"), json)
        assertFalse(json.contains("display_name"), json)
    }

    @Test
    fun iceServers_acceptStringOrListUrls() {
        val msg = SignalingJson.decodeFromString(
            WebRtcConfigMessage.serializer(),
            """{"type":"webrtc_config","config":{"iceServers":[
                {"urls":"stun:stun.l.google.com:19302"},
                {"urls":["turn:a.example:3478","turn:b.example:3478"],"username":"u","credential":"p"},
                {"urls":[]}
            ]},"extra":1}"""
        )
        val specs = msg.config?.iceServers.orEmpty().mapNotNull(IceServerConfig::toIceServerSpec)
        assertEquals(2, specs.size)
        assertEquals(listOf("stun:stun.l.google.com:19302"), specs[0].urls)
        assertEquals("u", specs[1].username)
        assertEquals(2, specs[1].urls.size)
    }
}

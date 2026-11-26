package com.example.damthoai.network

import com.example.damthoai.network.model.IceCandidatePayload
import com.example.damthoai.network.model.RegisterMessage
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * TV1: gói tin app gửi/nhận phải khớp đúng định dạng mà signaling_server.py dùng.
 */
class SignalingMessageParserTest {
    private val gson = Gson()

    @Test
    fun parsesPeersList() {
        val event = SignalingMessageParser.parse(gson, """{"type":"peers","peers":["A","B"]}""")
        assertEquals(SignalingEvent.PeersReceived(listOf("A", "B")), event)
    }

    @Test
    fun peersWithoutListBecomesEmpty() {
        val event = SignalingMessageParser.parse(gson, """{"type":"peers"}""")
        assertEquals(SignalingEvent.PeersReceived(emptyList()), event)
    }

    @Test
    fun offerUsesServerFieldNames() {
        val json = JsonParser.parseString(SignalingMessageParser.offer(gson, "A", "B", "v=0")).asJsonObject
        assertEquals("offer", json["type"].asString)
        assertEquals("B", json["target_id"].asString)
        assertEquals("v=0", json["sdp"].asString)
    }

    @Test
    fun offerRoundTrip() {
        val event = SignalingMessageParser.parse(
            gson, """{"type":"offer","target_id":"B","sender_id":"A","sdp":"v=0"}"""
        )
        assertEquals(SignalingEvent.OfferReceived("A", "v=0"), event)
    }

    @Test
    fun iceCandidateRoundTrip() {
        val sent = SignalingMessageParser.iceCandidate(gson, "A", "B", "0", 0, "candidate:1 1 udp")
        // Server ghi đè sender_id rồi chuyển tiếp nguyên vẹn
        val forwarded = JsonParser.parseString(sent).asJsonObject.apply { addProperty("sender_id", "A") }.toString()
        assertEquals(
            SignalingEvent.IceCandidateReceived("A", "0", 0, "candidate:1 1 udp"),
            SignalingMessageParser.parse(gson, forwarded)
        )
    }

    @Test
    fun iceCandidatesRemovedRoundTrip() {
        val list = listOf(IceCandidatePayload("0", 0, "candidate:9"))
        val sent = SignalingMessageParser.iceCandidatesRemoved(gson, "A", "B", list)
        assertEquals(SignalingEvent.IceCandidatesRemoved("A", list), SignalingMessageParser.parse(gson, sent))
    }

    @Test
    fun hangupRoundTrip() {
        val sent = SignalingMessageParser.hangup(gson, "A", "B")
        assertEquals(SignalingEvent.HangupReceived("A"), SignalingMessageParser.parse(gson, sent))
    }

    @Test
    fun registerCarriesDeviceKeyAndToken() {
        val json = JsonParser.parseString(gson.toJson(RegisterMessage(userId = "A", deviceKey = "khoa-may-A-1234567", token = "ma-nhom"))).asJsonObject
        assertEquals("register", json["type"].asString)
        assertEquals("A", json["user_id"].asString)
        assertEquals("khoa-may-A-1234567", json["device_key"].asString)
        assertEquals("ma-nhom", json["token"].asString)
    }

    @Test
    fun registerOmitsTokenWhenServerHasNone() {
        val json = JsonParser.parseString(gson.toJson(RegisterMessage(userId = "A", deviceKey = "khoa-may-A-1234567"))).asJsonObject
        assertFalse(json.has("token"))
    }

    @Test
    fun rejectionCodesHaveReadableMessages() {
        assertEquals("Sai hoặc thiếu mã truy cập máy chủ", SignalingClient.describeRejection(4001))
        assertEquals("Tên này đang được máy khác dùng, hãy đổi tên", SignalingClient.describeRejection(4009))
    }
}

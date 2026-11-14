package com.example.damthoai

import io.ktor.http.encodeURLParameter
import com.example.damthoai.data.qr.QrParseResult
import com.example.damthoai.data.qr.QrTokenParser
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class QrTokenParserTest {

    @Test
    fun parseMockString_returnsValid() {
        val result = QrTokenParser.parse("MOCK_QR_EVENT_889900")
        assertTrue(result is QrParseResult.Valid)
        val valid = result as QrParseResult.Valid
        assertEquals("Sự kiện Demo Đàm Thoại", valid.payload.eventName)
        assertEquals(889900L, valid.payload.eventId)
    }

    @Test
    fun parseNonDamThoaiString_returnsNotDamThoai() {
        val result = QrTokenParser.parse("https://google.com")
        assertEquals(QrParseResult.NotDamThoai, result)
    }

    @Test
    fun parseMalformedToken_returnsMalformed() {
        val result = QrTokenParser.parse("damthoai://join?token=invalid_token_without_dot")
        assertEquals(QrParseResult.Malformed, result)
    }

    @Test
    fun parsePinDeepLink_returnsValidWithDecodedChannel() {
        val result = QrTokenParser.parse("damthoai://join?channel=ch%5F02&pin=240826")
        assertTrue(result is QrParseResult.Valid)
        val valid = result as QrParseResult.Valid
        assertEquals("240826", valid.payload.pinCode)
        assertEquals("ch_02", valid.payload.primaryChCode)
    }

    @Test
    fun parsePinDeepLink_readsOrganizerEventName() {
        val encoded = "Đêm nhạc Hội trường A".encodeURLParameter(spaceToPlus = true)
        val result = QrTokenParser.parse("damthoai://join?channel=ch_01&pin=123456&name=$encoded")
        assertTrue(result is QrParseResult.Valid)
        assertEquals("Đêm nhạc Hội trường A", (result as QrParseResult.Valid).payload.eventName)
    }

    @Test
    fun parsePinDeepLinkWithInvalidPin_returnsMalformed() {
        val result = QrTokenParser.parse("damthoai://join?channel=ch_01&pin=abc")
        assertEquals(QrParseResult.Malformed, result)
    }

    @Test
    fun parseDeepLink_doesNotMatchParamWithSameSuffix() {
        // "spin=" không được hiểu nhầm là tham số "pin"
        val result = QrTokenParser.parse("damthoai://join?spin=123456&token=invalid_token_without_dot")
        assertEquals(QrParseResult.Malformed, result)
    }
}

package com.example.damthoai

import com.example.damthoai.data.qr.QrParseResult
import com.example.damthoai.data.qr.QrTokenParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}

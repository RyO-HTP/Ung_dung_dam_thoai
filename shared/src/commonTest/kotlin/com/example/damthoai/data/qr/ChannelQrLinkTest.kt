package com.example.damthoai.data.qr

import com.example.damthoai.data.Seat
import com.example.damthoai.data.TeamChannels
import com.example.damthoai.data.TeamRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ChannelQrLinkTest {
    private fun seatOf(link: String): Seat? {
        val result = QrTokenParser.parse(link, nowEpochSeconds = 1_000L)
        assertIs<QrParseResult.Valid>(result)
        return result.payload.seat()
    }

    @Test
    fun moiKenhVaiTroChoMotMaKhacNhau() {
        val a = ChannelQrLink.build("123456", "Đêm nhạc", "ch_02", "Sân khấu", TeamRole.THANH_VIEN)
        val b = ChannelQrLink.build("123456", "Đêm nhạc", "ch_03", "Kỹ thuật", TeamRole.THANH_VIEN)
        val c = ChannelQrLink.build("123456", "Đêm nhạc", "ch_02", "Sân khấu", TeamRole.TRUONG_NHOM)
        assertNotEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun quetQrVaoDungKenhDungVaiTro() {
        val link = ChannelQrLink.build("123456", "Đêm nhạc", "ch_03", "Kỹ thuật – Âm thanh", TeamRole.TRUONG_NHOM)
        assertEquals(Seat(TeamRole.TRUONG_NHOM, "ch_03", "Kỹ thuật – Âm thanh"), seatOf(link))

        val result = QrTokenParser.parse(link, nowEpochSeconds = 1_000L) as QrParseResult.Valid
        assertEquals("123456", result.payload.pinCode)
        assertEquals("Đêm nhạc", result.payload.eventName)
    }

    @Test
    fun qrTruongBanLuonVaoBanChiHuy() {
        val link = ChannelQrLink.build("123456", "Đêm nhạc", "ch_02", "Sân khấu", TeamRole.TRUONG_BAN)
        assertEquals(TeamChannels.COMMAND_ID, seatOf(link)?.groupId)
        assertEquals(TeamRole.TRUONG_BAN, seatOf(link)?.role)
    }

    @Test
    fun qrCuKhongKemVaiTro() {
        assertNull(seatOf("damthoai://join?channel=ch_02&pin=123456&name=Abc"))
    }
}

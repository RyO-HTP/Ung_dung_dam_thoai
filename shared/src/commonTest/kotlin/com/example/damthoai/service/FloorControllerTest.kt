package com.example.damthoai.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FloorControllerTest {

    private class Harness(scope: TestScope, var online: Boolean = true) {
        val requests = mutableListOf<String>()
        val releases = mutableListOf<String>()
        val keepalives = mutableListOf<String>()
        var micOpen = false
        val controller = FloorController(
            scope = scope.backgroundScope,
            myUserId = { "me" },
            isOnline = { online },
            sendRequest = { requests += it },
            sendRelease = { releases += it },
            setMicOpen = { micOpen = it },
            sendKeepalive = { keepalives += it }
        )
    }

    @Test
    fun dangGiuLuotThiBaoVanDangNoiMoi2Giay() = runTest {
        val h = Harness(this)
        h.controller.press("ch_02")
        h.controller.onGranted("ch_02")
        advanceTimeBy(FloorController.KEEPALIVE_INTERVAL_MS * 3 + 1)
        runCurrent()
        assertEquals(listOf("ch_02", "ch_02", "ch_02"), h.keepalives)

        // Nhả PTT: ngừng báo
        h.controller.release()
        advanceTimeBy(FloorController.KEEPALIVE_INTERVAL_MS * 3)
        runCurrent()
        assertEquals(3, h.keepalives.size)
    }

    @Test
    fun biChenNgangThiNgungBaoVanDangNoi() = runTest {
        val h = Harness(this)
        h.controller.press("ch_02")
        h.controller.onGranted("ch_02")
        h.controller.onState("ch_02", holderId = "boss", preemptedId = "me", reason = "preempted")
        advanceTimeBy(FloorController.KEEPALIVE_INTERVAL_MS * 3)
        runCurrent()
        assertTrue(h.keepalives.isEmpty())
    }

    @Test
    fun chuaDuocLuotThiKhongBaoVanDangNoi() = runTest {
        val h = Harness(this)
        h.controller.press("ch_02")
        h.controller.onDenied("ch_02", holderId = "other")
        advanceTimeBy(FloorController.KEEPALIVE_INTERVAL_MS * 3)
        runCurrent()
        assertTrue(h.keepalives.isEmpty())
    }

    @Test
    fun micChiMoKhiServerChoLuot() = runTest {
        val h = Harness(this)
        h.controller.press("ch_02")
        assertEquals(listOf("ch_02"), h.requests)
        assertFalse(h.micOpen, "Chưa được cho lượt thì mic phải tắt")
        assertEquals(FloorStatus.Requesting("ch_02"), h.controller.status.value)

        h.controller.onGranted("ch_02")
        assertTrue(h.micOpen)
        assertEquals(FloorStatus.Talking("ch_02"), h.controller.status.value)

        h.controller.release()
        assertFalse(h.micOpen)
        assertEquals(listOf("ch_02"), h.releases)
        assertEquals(FloorStatus.Idle, h.controller.status.value)
    }

    @Test
    fun kenhBanThiMicTatVaBaoBan() = runTest {
        val h = Harness(this)
        h.controller.press("ch_02")
        h.controller.onDenied("ch_02", holderId = "other")
        assertFalse(h.micOpen)
        assertEquals(FloorStatus.Busy("ch_02", "other"), h.controller.status.value)

        // Nhả PTT khi đang bận: không gửi release (mình không giữ lượt)
        h.controller.release()
        assertTrue(h.releases.isEmpty())
        assertEquals(FloorStatus.Idle, h.controller.status.value)
    }

    @Test
    fun biTruongBanChenNgangThiTatMic() = runTest {
        val h = Harness(this)
        h.controller.press("ch_01")
        h.controller.onGranted("ch_01")
        h.controller.onState("ch_01", holderId = "boss", preemptedId = "me", reason = "preempted")
        assertFalse(h.micOpen)
        assertEquals(FloorStatus.Cut("ch_01", "boss"), h.controller.status.value)
    }

    @Test
    fun hetGioNoiThiTatMic() = runTest {
        val h = Harness(this)
        h.controller.press("ch_02")
        h.controller.onGranted("ch_02")
        h.controller.onState("ch_02", holderId = null, preemptedId = null, reason = "timeout")
        assertFalse(h.micOpen)
        assertEquals(FloorStatus.Cut("ch_02", null), h.controller.status.value)
    }

    @Test
    fun luotKenhKhacKhongAnhHuong() = runTest {
        val h = Harness(this)
        h.controller.press("ch_02")
        h.controller.onGranted("ch_02")
        h.controller.onState("ch_01", holderId = "boss", preemptedId = null, reason = "")
        assertTrue(h.micOpen)
    }

    @Test
    fun serverKhongTraLoiThiVanMoMic() = runTest {
        val h = Harness(this)
        h.controller.press("ch_02")
        advanceTimeBy(FloorController.REPLY_TIMEOUT_MS - 1)
        runCurrent()
        assertFalse(h.micOpen)
        advanceTimeBy(2)
        runCurrent()
        assertTrue(h.micOpen, "Máy chủ cũ không trả lời: vẫn phải nói được")
    }

    @Test
    fun khongCoMayChuThiMoMicNgay() = runTest {
        val h = Harness(this, online = false)
        h.controller.press("ch_02")
        assertTrue(h.micOpen)
        assertTrue(h.requests.isEmpty())
    }

    @Test
    fun doiKenhKhiDangNoiThiXinLuotKenhMoi() = runTest {
        val h = Harness(this)
        h.controller.press("ch_02")
        h.controller.onGranted("ch_02")
        h.controller.switchChannel("ch_01")
        assertEquals(listOf("ch_02"), h.releases)
        assertEquals(listOf("ch_02", "ch_01"), h.requests)
        assertFalse(h.micOpen)
        assertEquals(FloorStatus.Requesting("ch_01"), h.controller.status.value)
    }

    @Test
    fun duocChoLuotSauKhiDaNhaThiKhongMoMic() = runTest {
        val h = Harness(this)
        h.controller.press("ch_02")
        h.controller.release()
        h.controller.onGranted("ch_02")
        assertFalse(h.micOpen)
    }
}

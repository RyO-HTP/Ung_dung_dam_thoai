package com.example.damthoai.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TeamChannelsTest {
    private val ban = Seat(TeamRole.TRUONG_BAN, TeamChannels.COMMAND_ID)
    private val ban2 = Seat(TeamRole.TRUONG_BAN, TeamChannels.COMMAND_ID)
    private val nhomA = Seat(TeamRole.TRUONG_NHOM, "ch_02")
    private val nhomB = Seat(TeamRole.TRUONG_NHOM, "ch_03")
    private val tvA = Seat(TeamRole.THANH_VIEN, "ch_02")
    private val tvA2 = Seat(TeamRole.THANH_VIEN, "ch_02")
    private val tvB = Seat(TeamRole.THANH_VIEN, "ch_03")

    @Test
    fun truongBanNoiVoiTruongNhomVaTruongBan() {
        assertEquals(setOf(TeamChannels.COMMAND_ID), TeamChannels.sharedChannels(ban, nhomA))
        assertEquals(setOf(TeamChannels.COMMAND_ID), TeamChannels.sharedChannels(nhomB, ban))
        assertEquals(setOf(TeamChannels.COMMAND_ID), TeamChannels.sharedChannels(ban, ban2))
        assertTrue(TeamChannels.sharedChannels(ban, tvA).isEmpty())
    }

    @Test
    fun truongNhomNoiVoiThanhVienCungNhomVaTruongNhomKhac() {
        assertEquals(setOf("ch_02"), TeamChannels.sharedChannels(nhomA, tvA))
        assertEquals(setOf("ch_02"), TeamChannels.sharedChannels(tvA, nhomA))
        assertTrue(TeamChannels.sharedChannels(nhomA, tvB).isEmpty())
        // Cùng cấp: các trưởng nhóm nói với nhau qua Ban chỉ huy
        assertEquals(setOf(TeamChannels.COMMAND_ID), TeamChannels.sharedChannels(nhomA, nhomB))
        // Hai trưởng nhóm cùng nhóm: chung cả Ban chỉ huy lẫn kênh nhóm
        assertEquals(
            setOf(TeamChannels.COMMAND_ID, "ch_02"),
            TeamChannels.sharedChannels(nhomA, Seat(TeamRole.TRUONG_NHOM, "ch_02"))
        )
    }

    @Test
    fun thanhVienChiNoiTrongNhom() {
        assertEquals(setOf("ch_02"), TeamChannels.sharedChannels(tvA, tvA2))
        assertTrue(TeamChannels.sharedChannels(tvA, tvB).isEmpty())
    }

    @Test
    fun kenhCuaTungVaiTro() {
        assertEquals(listOf(TeamChannels.COMMAND_ID), TeamChannels.channelsOf(ban))
        assertEquals(listOf(TeamChannels.COMMAND_ID, "ch_02"), TeamChannels.channelsOf(nhomA))
        assertEquals(listOf("ch_02"), TeamChannels.channelsOf(tvA))
    }

    @Test
    fun maVaiTroLaCoiLaThanhVien() {
        assertEquals(TeamRole.THANH_VIEN, TeamRole.fromWire(null))
        assertEquals(TeamRole.THANH_VIEN, TeamRole.fromWire("xyz"))
        assertEquals(TeamRole.TRUONG_NHOM, TeamRole.fromWire("nhom"))
    }
}

package com.example.damthoai.ui.event

import com.example.damthoai.data.MemberLevel
import com.example.damthoai.data.TeamChannels
import com.example.damthoai.data.TeamRole
import com.example.damthoai.data.seatFor
import com.example.damthoai.network.model.OnlineMember
import kotlin.test.Test
import kotlin.test.assertEquals

class MemberLevelMappingTest {
    @Test
    fun levelFollowsRoleFromQr() {
        assertEquals(MemberLevel.LEVEL_3, OnlineMember(userId = "a", teamRole = "tv", channelId = "ch_03").toUiMember("me").level)
        assertEquals(MemberLevel.LEVEL_2, OnlineMember(userId = "b", teamRole = "nhom", channelId = "ch_03").toUiMember("me").level)
        assertEquals(MemberLevel.LEVEL_1, OnlineMember(userId = "c", teamRole = "ban", channelId = "ch_01").toUiMember("me").level)
    }

    @Test
    fun organizerIsAlwaysLevel1AndMissingRoleIsLevel3() {
        assertEquals(MemberLevel.LEVEL_1, OnlineMember(userId = "o", isOrganizer = true, teamRole = "tv").toUiMember("me").level)
        assertEquals(MemberLevel.LEVEL_3, OnlineMember(userId = "x").toUiMember("me").level)
    }

    @Test
    fun eachLevelLandsInTheRightChannels() {
        val ky = "ch_03"
        assertEquals(listOf(TeamChannels.COMMAND_ID), TeamChannels.channelsOf(seatFor(TeamRole.fromLevel(1), ky, "Kỹ thuật")))
        assertEquals(listOf(TeamChannels.COMMAND_ID, ky), TeamChannels.channelsOf(seatFor(TeamRole.fromLevel(2), ky, "Kỹ thuật")))
        assertEquals(listOf(ky), TeamChannels.channelsOf(seatFor(TeamRole.fromLevel(3), ky, "Kỹ thuật")))
    }
}

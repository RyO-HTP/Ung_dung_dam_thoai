package com.example.damthoai

import com.example.damthoai.data.remote.ChannelDto
import com.example.damthoai.data.remote.EventInfo
import com.example.damthoai.data.remote.UserSessionDto
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class EventMappingTest {

    @Test
    fun testChannelDtoMapping() {
        val dto = ChannelDto(
            id = "ch_01",
            channelId = 1,
            name = "Toàn sự kiện",
            departmentCode = "ALL",
            defaultPriority = 3,
            onlineCount = 10,
            statusText = "Đang phát"
        )
        val domain = dto.toDomain()
        assertEquals("ch_01", domain.id)
        assertEquals("Toàn sự kiện", domain.name)
        assertEquals(true, domain.isDefault)
        assertEquals(true, domain.isPriority)
        assertEquals(10, domain.activeMembers)
    }

    @Test
    fun testUserIdStringParsing() {
        val sessionWithStringId = UserSessionDto(
            userId = JsonPrimitive("user_999"),
            userCode = "user_999",
            eventId = 1L,
            displayName = "Minh Hậu",
            roleTitle = "Trưởng ban",
            userLevel = 2,
            rolePriority = 2,
            primaryTxChannel = 2,
            primaryChannelId = "ch_02",
            listeningChannels = null,
            signalingUrl = null,
            signalingToken = null
        )
        assertEquals("user_999", sessionWithStringId.resolvedUserId)

        val sessionWithNumberId = UserSessionDto(
            userId = JsonPrimitive(101),
            userCode = "user_101",
            eventId = 1L,
            displayName = "Minh Hậu",
            roleTitle = "Trưởng ban",
            userLevel = 2,
            rolePriority = 2,
            primaryTxChannel = 2,
            primaryChannelId = "ch_02",
            listeningChannels = null,
            signalingUrl = null,
            signalingToken = null
        )
        assertEquals("101", sessionWithNumberId.resolvedUserId)
    }
}

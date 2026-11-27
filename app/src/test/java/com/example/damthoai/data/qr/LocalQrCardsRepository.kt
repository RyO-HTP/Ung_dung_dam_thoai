package com.example.damthoai.data.qr

import android.util.Base64
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalQrCardsRepository : QrCardsRepository {
    override suspend fun getRoleCards(eventId: Long): List<RoleQrCard> = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis() / 1000
        val exp = now + 86400L
        val pin = "123456"

        val profiles = listOf(
            Triple("DIR", "Toàn sự kiện", 1),
            Triple("STG", "Sân khấu", 2),
            Triple("TEC", "Kỹ thuật", 3),
            Triple("LOG", "Hậu cần – An ninh", 2),
            Triple("REC", "Đón tiếp", 3)
        )

        profiles.mapIndexed { index, (deptCode, roleTitle, level) ->
            val chId = 101L + index
            val chCode = "ch_0${index + 1}"
            val payload = QrTokenPayload(
                eventId = eventId,
                eventName = "Sự kiện Đàm Thoại",
                pinCode = pin,
                deptCode = deptCode,
                deptTitle = roleTitle,
                roleTitle = roleTitle,
                userLevel = level,
                priority = if (level == 1) 3 else if (level == 2) 2 else 1,
                primaryCh = chId,
                primaryChCode = chCode,
                listenCh = listOf(101L, 102L, 103L),
                uiMode = "PRO_MULTI_CHANNEL",
                exp = exp
            )
            val gson = Gson()
            val json = gson.toJson(payload)
            val base64Payload = Base64.encodeToString(
                json.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            val mockSig = "0".repeat(64)
            val deepLink = "damthoai://join?token=$base64Payload.$mockSig"

            RoleQrCard(
                deptCode = deptCode,
                deptTitle = getChannelNameFromCode(deptCode),
                roleTitle = roleTitle,
                userLevel = level,
                deepLinkUri = deepLink,
                expiresAtEpochSeconds = exp
            )
        }
    }
}

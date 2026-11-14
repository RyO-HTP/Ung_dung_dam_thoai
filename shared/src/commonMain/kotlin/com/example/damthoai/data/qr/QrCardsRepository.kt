/**
 * Path: app/src/main/java/com/example/damthoai/data/qr/QrCardsRepository.kt
 *
 * Khối KDoc tiếng Việt:
 * Quản lý kho thẻ QR phân vai trò bộ phận (Local & Remote QrCardsRepository).
 */
package com.example.damthoai.data.qr

import com.example.damthoai.network.SignalingJson
import com.example.damthoai.network.createHttpClient
import com.example.damthoai.platform.currentTimeMillis
import com.example.damthoai.util.Log
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoleQrCard(
    @SerialName("deptCode") val deptCode: String,
    @SerialName("deptTitle") val deptTitle: String,
    @SerialName("roleTitle") val roleTitle: String,
    @SerialName("userLevel") val userLevel: Int,
    @SerialName("qrDeepLinkUri") val deepLinkUri: String,
    @SerialName("expiresAt") val expiresAtEpochSeconds: Long
)

interface QrCardsRepository {
    suspend fun getRoleCards(eventId: Long): List<RoleQrCard>
}

fun getChannelNameFromCode(code: String): String = when (code.uppercase()) {
    "DIR" -> "Toàn sự kiện"
    "STG" -> "Sân khấu"
    "TEC" -> "Kỹ thuật"
    "LOG" -> "Hậu cần – An ninh"
    "REC" -> "Đón tiếp"
    else -> code
}

class LocalQrCardsRepository : QrCardsRepository {
    override suspend fun getRoleCards(eventId: Long): List<RoleQrCard> = withContext(Dispatchers.Default) {
        val now = currentTimeMillis() / 1000
        val exp = now + 86400L // 24 hours
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
            val json = SignalingJson.encodeToString(QrTokenPayload.serializer(), payload)
            val base64Payload = QrBase64Encoder.encode(json.encodeToByteArray())
            val mockSig = "0".repeat(64) // LOCAL_UNSIGNED: demo giữa 2 thiết bị local
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

class RemoteQrCardsRepository(private val baseUrl: String) : QrCardsRepository {
    companion object {
        private const val TAG = "RemoteQrCardsRepo"
    }

    private val client by lazy {
        createHttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 5_000
                requestTimeoutMillis = 10_000
            }
            expectSuccess = false
        }
    }

    @Serializable
    data class QrRolesResponse(val cards: List<RoleQrCard> = emptyList())

    override suspend fun getRoleCards(eventId: Long): List<RoleQrCard> = withContext(Dispatchers.Default) {
        try {
            val url = "${baseUrl.removeSuffix("/")}/api/v1/events/qr-roles?eventId=$eventId"
            val response = client.get(url)
            if (response.status.isSuccess()) {
                val resp = SignalingJson.decodeFromString(QrRolesResponse.serializer(), response.bodyAsText())
                return@withContext resp.cards
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi tải QR cards: ${e.message}", e)
        }
        emptyList()
    }
}

object QrCardRepositories {
    fun repository(): QrCardsRepository {
        BackendConfig.init()
        val url = BackendConfig.baseUrl
        return if (!url.isNullOrBlank()) {
            RemoteQrCardsRepository(url)
        } else {
            LocalQrCardsRepository()
        }
    }
}

/** Mã hoá Base64URL KHÔNG đệm, giống token do backend sinh. */
@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
private val QrBase64Encoder = kotlin.io.encoding.Base64.UrlSafe.withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT)

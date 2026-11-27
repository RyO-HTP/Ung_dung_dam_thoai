/**
 * Path: app/src/main/java/com/example/damthoai/data/qr/QrCardsRepository.kt
 *
 * Khối KDoc tiếng Việt:
 * Quản lý kho thẻ QR phân vai trò bộ phận (Remote QrCardsRepository).
 */
package com.example.damthoai.data.qr

import android.content.Context
import com.example.damthoai.data.remote.ApiClient
import com.example.damthoai.data.remote.EventSession
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RoleQrCard(
    @SerializedName("deptCode") val deptCode: String,
    @SerializedName("deptTitle") val deptTitle: String,
    @SerializedName("roleTitle") val roleTitle: String,
    @SerializedName("userLevel") val userLevel: Int,
    @SerializedName("qrDeepLinkUri") val deepLinkUri: String,
    @SerializedName("expiresAt") val expiresAtEpochSeconds: Long
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

class RemoteQrCardsRepository(private val baseUrl: String? = null) : QrCardsRepository {
    data class QrRolesResponse(
        val eventId: Long?,
        val eventName: String?,
        val pinCode: String?,
        val cards: List<RoleQrCard>? = null,
        val departmentCards: List<RoleQrCard>? = null
    )

    override suspend fun getRoleCards(eventId: Long): List<RoleQrCard> = withContext(Dispatchers.IO) {
        val session = EventSession.getSession()
        val actorUserId = session?.userId.orEmpty()
        val path = "/api/v1/events/qr-roles?eventId=$eventId&actorUserId=$actorUserId"
        val result = ApiClient.get<QrRolesResponse>(path)
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: Exception("Lỗi tải thẻ QR phân vai")
        }
        val resp = result.getOrNull()
        resp?.departmentCards ?: resp?.cards ?: emptyList()
    }
}

object QrCardRepositories {
    fun repository(context: Context): QrCardsRepository {
        BackendConfig.init(context)
        EventSession.init(context)
        return RemoteQrCardsRepository()
    }
}

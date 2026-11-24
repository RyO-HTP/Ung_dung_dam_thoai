/**
 * Path: app/src/main/java/com/example/damthoai/data/qr/QrCardsRepository.kt
 *
 * Khối KDoc tiếng Việt:
 * Quản lý kho thẻ QR phân vai trò bộ phận (Local & Remote QrCardsRepository).
 */
package com.example.damthoai.data.qr

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

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

class LocalQrCardsRepository : QrCardsRepository {
    override suspend fun getRoleCards(eventId: Long): List<RoleQrCard> = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis() / 1000
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
            val gson = Gson()
            val json = gson.toJson(payload)
            val base64Payload = Base64.encodeToString(
                json.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    data class QrRolesResponse(val cards: List<RoleQrCard>)

    override suspend fun getRoleCards(eventId: Long): List<RoleQrCard> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.removeSuffix("/")}/api/v1/events/qr-roles?eventId=$eventId"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val resp = gson.fromJson(body, QrRolesResponse::class.java)
                    return@withContext resp?.cards ?: emptyList()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Lỗi mạng khi tải QR cards: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi tải QR cards: ${e.message}", e)
        }
        emptyList()
    }
}

object QrCardRepositories {
    fun repository(context: Context): QrCardsRepository {
        BackendConfig.init(context)
        val url = BackendConfig.baseUrl
        return if (!url.isNullOrBlank()) {
            RemoteQrCardsRepository(url)
        } else {
            LocalQrCardsRepository()
        }
    }
}

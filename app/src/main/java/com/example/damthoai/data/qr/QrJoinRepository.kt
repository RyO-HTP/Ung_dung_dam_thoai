/**
 * Path: app/src/main/java/com/example/damthoai/data/qr/QrJoinRepository.kt
 *
 * Khối KDoc tiếng Việt:
 * Quản lý logic tham gia sự kiện qua mã QR (Local & Remote Repository).
 */
package com.example.damthoai.data.qr

import android.content.Context
import android.util.Log
import com.example.damthoai.data.fake.FakeEventRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class QrJoinFailure {
    EXPIRED,
    MALFORMED,
    NOT_DAM_THOAI,
    SERVER_REJECTED,
    NETWORK
}

sealed interface QrJoinOutcome {
    data class Joined(
        val assignedPriority: Int,
        val primaryChannelCode: String,
        val primaryTxChannelId: Long,
        val autoListenChannels: List<Long>,
        val payload: QrTokenPayload
    ) : QrJoinOutcome

    data class Rejected(val reason: QrJoinFailure, val message: String? = null) : QrJoinOutcome
}

interface QrJoinRepository {
    suspend fun joinByQr(rawToken: String, displayName: String): QrJoinOutcome
}

class LocalQrJoinRepository : QrJoinRepository {
    override suspend fun joinByQr(rawToken: String, displayName: String): QrJoinOutcome = withContext(Dispatchers.Default) {
        when (val parseResult = QrTokenParser.parse(rawToken)) {
            is QrParseResult.Valid -> {
                val payload = parseResult.payload
                FakeEventRepository.applyQrJoin(payload, displayName)
                QrJoinOutcome.Joined(
                    assignedPriority = payload.priority,
                    primaryChannelCode = payload.primaryChCode,
                    primaryTxChannelId = payload.primaryCh,
                    autoListenChannels = payload.listenCh,
                    payload = payload
                )
            }
            is QrParseResult.Expired -> QrJoinOutcome.Rejected(QrJoinFailure.EXPIRED)
            QrParseResult.Malformed -> QrJoinOutcome.Rejected(QrJoinFailure.MALFORMED)
            QrParseResult.NotDamThoai -> QrJoinOutcome.Rejected(QrJoinFailure.NOT_DAM_THOAI)
        }
    }
}

class RemoteQrJoinRepository(private val baseUrl: String) : QrJoinRepository {
    companion object {
        private const val TAG = "RemoteQrJoinRepo"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    data class JoinRequest(val qrToken: String, val displayName: String)
    data class JoinResponse(
        val assignedPriority: Int,
        val primaryChannelCode: String,
        val primaryTxChannelId: Long,
        val autoListenChannels: List<Long>
    )

    override suspend fun joinByQr(rawToken: String, displayName: String): QrJoinOutcome = withContext(Dispatchers.IO) {
        // Trước tiên parse local để kiểm tra định dạng/hạn cơ bản
        val parseResult = QrTokenParser.parse(rawToken)
        val payload = when (parseResult) {
            is QrParseResult.Valid -> parseResult.payload
            is QrParseResult.Expired -> return@withContext QrJoinOutcome.Rejected(QrJoinFailure.EXPIRED)
            QrParseResult.Malformed -> return@withContext QrJoinOutcome.Rejected(QrJoinFailure.MALFORMED)
            QrParseResult.NotDamThoai -> return@withContext QrJoinOutcome.Rejected(QrJoinFailure.NOT_DAM_THOAI)
        }

        try {
            val url = "${baseUrl.removeSuffix("/")}/api/v1/auth/join-by-qr"
            val reqBodyJson = gson.toJson(JoinRequest(rawToken, displayName))
            val body = reqBodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).post(body).build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respBody = response.body?.string()
                    val joinResp = gson.fromJson(respBody, JoinResponse::class.java)
                    if (joinResp != null) {
                        FakeEventRepository.applyQrJoin(payload, displayName)
                        return@withContext QrJoinOutcome.Joined(
                            assignedPriority = joinResp.assignedPriority,
                            primaryChannelCode = joinResp.primaryChannelCode,
                            primaryTxChannelId = joinResp.primaryTxChannelId,
                            autoListenChannels = joinResp.autoListenChannels,
                            payload = payload
                        )
                    } else {
                        return@withContext QrJoinOutcome.Rejected(QrJoinFailure.SERVER_REJECTED, "Phản hồi máy chủ trống")
                    }
                } else {
                    val errorMsg = response.body?.string() ?: response.message
                    Log.w(TAG, "Máy chủ từ chối mã QR: HTTP ${response.code} - $errorMsg")
                    return@withContext QrJoinOutcome.Rejected(QrJoinFailure.SERVER_REJECTED, errorMsg)
                }
            }
        } catch (ioException: IOException) {
            Log.e(TAG, "Lỗi mạng khi join by qr: ${ioException.message}", ioException)
            QrJoinOutcome.Rejected(QrJoinFailure.NETWORK)
        } catch (exception: Exception) {
            Log.e(TAG, "Lỗi không xác định khi join by qr: ${exception.message}", exception)
            QrJoinOutcome.Rejected(QrJoinFailure.SERVER_REJECTED, exception.message)
        }
    }
}

object QrRepositories {
    fun joinRepository(context: Context): QrJoinRepository {
        BackendConfig.init(context)
        val url = BackendConfig.baseUrl
        return if (!url.isNullOrBlank()) {
            RemoteQrJoinRepository(url)
        } else {
            LocalQrJoinRepository()
        }
    }
}

/**
 * Path: app/src/main/java/com/example/damthoai/data/qr/QrJoinRepository.kt
 *
 * Khối KDoc tiếng Việt:
 * Quản lý logic tham gia sự kiện qua mã QR (Remote Repository).
 */
package com.example.damthoai.data.qr

import android.content.Context
import android.util.Log
import com.example.damthoai.data.remote.RemoteEventRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

class RemoteQrJoinRepository(private val context: Context) : QrJoinRepository {
    companion object {
        private const val TAG = "RemoteQrJoinRepo"
    }

    override suspend fun joinByQr(rawToken: String, displayName: String): QrJoinOutcome = withContext(Dispatchers.IO) {
        val parseResult = QrTokenParser.parse(rawToken)
        val payload = when (parseResult) {
            is QrParseResult.Valid -> parseResult.payload
            is QrParseResult.Expired -> return@withContext QrJoinOutcome.Rejected(QrJoinFailure.EXPIRED)
            is QrParseResult.Malformed -> return@withContext QrJoinOutcome.Rejected(QrJoinFailure.MALFORMED)
            is QrParseResult.NotDamThoai -> return@withContext QrJoinOutcome.Rejected(QrJoinFailure.NOT_DAM_THOAI)
        }

        try {
            val result = RemoteEventRepository.joinByQr(context, rawToken, displayName)
            if (result.isSuccess) {
                QrJoinOutcome.Joined(
                    assignedPriority = payload.priority,
                    primaryChannelCode = payload.primaryChCode,
                    primaryTxChannelId = payload.primaryCh,
                    autoListenChannels = payload.listenCh,
                    payload = payload
                )
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Máy chủ từ chối mã QR"
                Log.w(TAG, "Máy chủ từ chối mã QR: $errorMsg")
                QrJoinOutcome.Rejected(QrJoinFailure.SERVER_REJECTED, errorMsg)
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Lỗi khi join by qr: ${exception.message}", exception)
            QrJoinOutcome.Rejected(QrJoinFailure.NETWORK, exception.message)
        }
    }
}

object QrRepositories {
    fun joinRepository(context: Context): QrJoinRepository {
        BackendConfig.init(context)
        return RemoteQrJoinRepository(context)
    }
}

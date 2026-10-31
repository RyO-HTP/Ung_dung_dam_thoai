package com.example.damthoai.livekit

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Cấu trúc JSON trả về từ máy chủ cấp Token (api.js - Tuần 2)
 */
data class LiveKitTokenResponse(
    @SerializedName("serverUrl")
    val serverUrl: String,
    @SerializedName("token")
    val token: String
)

/**
 * Service gọi REST API (Node.js Express - api.js) để xin JWT Token tham gia phòng thoại LiveKit SFU.
 * Phụ trách: Thành viên 2 (Tuần 2).
 */
class LiveKitTokenService(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "LiveKitTokenService"
        // IP mặc định kết nối tới máy chủ cấp Token (api.js chạy port 3000)
        // 10.0.2.2 trỏ về localhost máy tính khi chạy trên Android Emulator
        const val DEFAULT_EMULATOR_HOST = "http://10.0.2.2:3000"
        const val DEFAULT_LOCAL_HOST = "http://localhost:3000"
    }

    /**
     * Lấy Token truy cập phòng thoại LiveKit từ Server
     * @param baseUrl Địa chỉ máy chủ (vd: http://192.168.1.100:3000 hoặc http://10.0.2.2:3000)
     * @param roomName Tên phòng/kênh đàm thoại (vd: "Kenh_Hau_Can")
     * @param identity Định danh người dùng (vd: "Tong_Dao_Dien")
     */
    suspend fun fetchToken(
        baseUrl: String = DEFAULT_EMULATOR_HOST,
        roomName: String = "Kenh_Hau_Can",
        identity: String = "Tong_Dao_Dien"
    ): Result<LiveKitTokenResponse> = withContext(Dispatchers.IO) {
        try {
            val cleanBaseUrl = baseUrl.trimEnd('/')
            val url = "$cleanBaseUrl/get-token?room=$roomName&identity=$identity"
            Log.d(TAG, "Đang gửi yêu cầu xin Token tới: $url")

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorMsg = "Yêu cầu Token thất bại (HTTP ${response.code}): ${response.message}"
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val bodyString = response.body?.string()
            if (bodyString.isNullOrBlank()) {
                val errorMsg = "Phản hồi từ Server rỗng!"
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(Exception(errorMsg))
            }

            Log.d(TAG, "Đã nhận JSON Token từ server: $bodyString")
            val tokenResponse = gson.fromJson(bodyString, LiveKitTokenResponse::class.java)

            if (tokenResponse.serverUrl.isBlank() || tokenResponse.token.isBlank()) {
                return@withContext Result.failure(Exception("Dữ liệu serverUrl hoặc token không hợp lệ!"))
            }

            Result.success(tokenResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi kết nối khi xin Token LiveKit: ${e.message}", e)
            Result.failure(e)
        }
    }
}

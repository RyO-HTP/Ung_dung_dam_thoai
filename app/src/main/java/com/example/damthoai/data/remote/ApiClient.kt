package com.example.damthoai.data.remote

import com.example.damthoai.data.qr.BackendConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiException(
    val httpCode: Int,
    val errorCode: String?,
    override val message: String
) : Exception(message)

object ApiClient {
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val currentBaseUrl: String
        get() {
            val url = BackendConfig.baseUrl
            if (url.isNullOrBlank()) {
                throw ApiException(0, "CONFIG_MISSING", "Chưa cấu hình máy chủ")
            }
            return if (url.endsWith("/")) url.dropLast(1) else url
        }

    val gson = Gson()

    fun mapError(httpCode: Int, respBody: String): ApiException {
        var errorCode: String? = null
        var errorMsg: String? = null
        try {
            val jsonObj = gson.fromJson(respBody, JsonObject::class.java)
            if (jsonObj != null) {
                if (jsonObj.has("error")) errorCode = jsonObj.get("error").asString
                if (jsonObj.has("message")) errorMsg = jsonObj.get("message").asString
            }
        } catch (_: Exception) {}

        val defaultMsg = when (httpCode) {
            400 -> "Yêu cầu không hợp lệ"
            403 -> errorCode?.let { if (it == "USER_NOT_IN_EVENT") "Bạn đã bị xóa khỏi sự kiện" else "Không có quyền truy cập" } ?: "Không có quyền truy cập"
            404 -> "Không tìm thấy dữ liệu hoặc sự kiện không tồn tại"
            409 -> "Xung đột dữ liệu hoặc mã đã tồn tại"
            410 -> "Mã QR hoặc sự kiện đã hết hạn"
            429 -> "Quá nhiều yêu cầu, vui lòng thử lại sau"
            in 500..599 -> "Máy chủ đang khởi động hoặc gặp sự cố"
            else -> "Lỗi kết nối máy chủ [$httpCode]"
        }

        val finalMsg = errorMsg ?: defaultMsg
        return ApiException(httpCode, errorCode, finalMsg)
    }

    inline fun <reified T> executeRequest(request: Request): Result<T> {
        return try {
            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    if (T::class == Unit::class || T::class == Any::class) {
                        @Suppress("UNCHECKED_CAST")
                        return Result.success(Unit as T)
                    }
                    if (respBody.isBlank()) {
                        @Suppress("UNCHECKED_CAST")
                        return Result.success(Unit as T)
                    }
                    val type = object : TypeToken<T>() {}.type
                    val result = gson.fromJson<T>(respBody, type)
                    if (result != null) {
                        Result.success(result)
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        Result.success(Unit as T)
                    }
                } else {
                    Result.failure(mapError(response.code, respBody))
                }
            }
        } catch (e: ApiException) {
            Result.failure(e)
        } catch (_: java.io.IOException) {
            Result.failure(ApiException(0, "NETWORK_ERROR", "Lỗi kết nối mạng, vui lòng kiểm tra lại"))
        } catch (e: Exception) {
            Result.failure(ApiException(0, "UNKNOWN_ERROR", e.message ?: "Lỗi không xác định"))
        }
    }

    inline fun <reified T> get(path: String): Result<T> {
        val url = "$currentBaseUrl${if (path.startsWith("/")) path else "/$path"}"
        val request = Request.Builder().url(url).get().build()
        return executeRequest<T>(request)
    }

    inline fun <reified T> post(path: String, bodyObj: Any? = null): Result<T> {
        val url = "$currentBaseUrl${if (path.startsWith("/")) path else "/$path"}"
        val jsonBody = bodyObj?.let { gson.toJson(it) } ?: "{}"
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()
        return executeRequest<T>(request)
    }

    inline fun <reified T> patch(path: String, bodyObj: Any? = null): Result<T> {
        val url = "$currentBaseUrl${if (path.startsWith("/")) path else "/$path"}"
        val jsonBody = bodyObj?.let { gson.toJson(it) } ?: "{}"
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).patch(requestBody).build()
        return executeRequest<T>(request)
    }

    inline fun <reified T> delete(path: String, bodyObj: Any? = null): Result<T> {
        val url = "$currentBaseUrl${if (path.startsWith("/")) path else "/$path"}"
        val requestBody = bodyObj?.let {
            gson.toJson(it).toRequestBody("application/json; charset=utf-8".toMediaType())
        }
        val request = Request.Builder().url(url).delete(requestBody).build()
        return executeRequest<T>(request)
    }
}

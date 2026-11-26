package com.example.damthoai.data.remote

import com.example.damthoai.data.qr.BackendConfig
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object ApiClient {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val currentBaseUrl: String
        get() {
            val url = BackendConfig.baseUrl
            return if (!url.isNullOrBlank()) {
                if (url.endsWith("/")) url.dropLast(1) else url
            } else {
                "http://192.168.1.5:8080"
            }
        }

    inline fun <reified T> get(path: String): Result<T> {
        return try {
            val url = "$currentBaseUrl${if (path.startsWith("/")) path else "/$path"}"
            val request = Request.Builder().url(url).get().build()
            val gson = Gson()

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val result = gson.fromJson(respBody, T::class.java)
                    if (result != null) {
                        Result.success(result)
                    } else {
                        Result.failure(Exception("Phản hồi rỗng từ máy chủ"))
                    }
                } else {
                    Result.failure(Exception("Lỗi máy chủ [${response.code}]: $respBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    inline fun <reified T> post(path: String, bodyObj: Any): Result<T> {
        return try {
            val url = "$currentBaseUrl${if (path.startsWith("/")) path else "/$path"}"
            val gson = Gson()
            val jsonBody = gson.toJson(bodyObj)
            val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val result = gson.fromJson(respBody, T::class.java)
                    if (result != null) {
                        Result.success(result)
                    } else {
                        Result.failure(Exception("Phản hồi rỗng từ máy chủ"))
                    }
                } else {
                    Result.failure(Exception("Lỗi máy chủ [${response.code}]: $respBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.example.damthoai.data

sealed class NetworkStatus {
    data class Good(val ms: Int) : NetworkStatus()
    data class Weak(val ms: Int) : NetworkStatus()
    object Offline : NetworkStatus()

    companion object {
        val CONNECTED = Good(45) // Giá trị kết nối mặc định an toàn
    }
}
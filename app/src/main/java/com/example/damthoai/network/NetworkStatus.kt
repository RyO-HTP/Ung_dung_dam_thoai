package com.example.damthoai.network

sealed class NetworkStatus {
    data class Good(val ms: Int) : NetworkStatus()
    data class Weak(val ms: Int) : NetworkStatus()
    object Offline : NetworkStatus()
}

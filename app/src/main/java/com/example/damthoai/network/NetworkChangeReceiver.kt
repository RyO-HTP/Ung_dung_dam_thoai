package com.example.damthoai.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.damthoai.platform.NetworkMonitor

/** Android: theo dõi đổi mạng Wi-Fi <-> 4G bằng ConnectivityManager (cài đặt [NetworkMonitor]). */
class NetworkChangeReceiver(context: Context) : NetworkMonitor {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var onNetworkChanged: () -> Unit = {}

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        private var lastNetworkType: Int? = null

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)

            val isWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCellular = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val currentNetworkType = when {
                isWifi -> 1
                isCellular -> 2
                else -> 0
            }

            // Kích hoạt Seamless Handover khi phát hiện nhảy mạng
            if (lastNetworkType != null && lastNetworkType != currentNetworkType && currentNetworkType != 0) {
                Log.d("NetworkChange", "Đổi mạng Wi-Fi/4G. Kích hoạt Seamless Handover...")
                onNetworkChanged()
            }
            lastNetworkType = currentNetworkType
        }
    }

    override fun start(onNetworkChanged: () -> Unit) {
        this.onNetworkChanged = onNetworkChanged
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    override fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e("NetworkChange", "Lỗi hủy đăng ký: ${e.message}")
        }
    }
}

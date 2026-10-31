package com.example.damthoai.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Loại mạng kết nối hiện hành của thiết bị
 */
enum class NetworkType {
    WIFI,
    CELLULAR, // 4G / 5G / LTE
    ETHERNET,
    NONE
}

/**
 * =================================================================================
 * GIÁM SÁT CHUYỂN ĐỔI MẠNG (NetworkChangeMonitor) — SEAMLESS HANDOVER TUẦN 3
 * VAI TRÒ: Thành viên 2 (Client Network & Audio Background)
 * =================================================================================
 *
 * Lắng nghe sự kiện chuyển đổi qua lại giữa mạng Wi-Fi và 4G/5G theo thời gian thực.
 * Khi phát hiện chuyển mạng, thông báo cho Signaling Server (server.js - cổng 8080)
 * để thực hiện tái thương lượng ICE (ICE Restart) và chuyển luồng đàm thoại tức thì mà không rớt cuộc gọi.
 */
class NetworkChangeMonitor(context: Context) {

    companion object {
        private const val TAG = "NetworkChangeMonitor"
    }

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _currentNetworkType = MutableStateFlow(getCurrentNetworkTypeInternal())
    val currentNetworkType: StateFlow<NetworkType> = _currentNetworkType.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var onNetworkChangedListener: ((NetworkType) -> Unit)? = null

    /**
     * Bắt đầu giám sát thay đổi mạng hệ thống
     */
    fun startMonitoring(onNetworkChanged: (NetworkType) -> Unit) {
        this.onNetworkChangedListener = onNetworkChanged

        if (networkCallback != null) return

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val newType = getNetworkTypeForNetwork(network)
                Log.d(TAG, "Mạng khả dụng mới: $newType")
                handleNetworkChange(newType)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val newType = when {
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                    else -> NetworkType.NONE
                }
                handleNetworkChange(newType)
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Mất kết nối mạng hiện tại!")
                val fallbackType = getCurrentNetworkTypeInternal()
                handleNetworkChange(fallbackType)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
            Log.d(TAG, "Đã kích hoạt giám sát mạng Seamless Handover.")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đăng ký NetworkCallback: ${e.message}", e)
        }
    }

    private fun handleNetworkChange(newType: NetworkType) {
        if (_currentNetworkType.value != newType) {
            val oldType = _currentNetworkType.value
            _currentNetworkType.value = newType
            Log.d(TAG, "[SEAMLESS HANDOVER] Chuyển mạng: $oldType -> $newType")
            onNetworkChangedListener?.invoke(newType)
        }
    }

    private fun getNetworkTypeForNetwork(network: Network): NetworkType {
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.NONE
        }
    }

    private fun getCurrentNetworkTypeInternal(): NetworkType {
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkType.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.NONE
        }
    }

    /**
     * Dừng giám sát mạng
     */
    fun stopMonitoring() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
                Log.d(TAG, "Đã dừng giám sát mạng.")
            } catch (ignored: Exception) {}
        }
        networkCallback = null
        onNetworkChangedListener = null
    }
}

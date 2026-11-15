package com.example.damthoai.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * TV1 - Tuần 4: Phát hiện chuyển mạng để kích hoạt Seamless Handover.
 *
 * Theo dõi MẠNG MẶC ĐỊNH của hệ thống (registerDefaultNetworkCallback) thay vì mọi mạng:
 * khi đi từ vùng Wi-Fi ra 4G, Android đổi default network từ Wi-Fi sang Cellular -> ta nhận
 * onAvailable() với một Network mới. Đổi giữa 2 điểm phát Wi-Fi khác nhau cũng được tính.
 *
 * @param onNetworkChanged gọi trên main thread với loại mạng mới ("wifi" / "cellular" / "other"),
 *        đã được chống dội (debounce) để tránh ICE restart liên tục khi sóng chập chờn.
 * @param onNetworkLost gọi khi mất hoàn toàn mạng mặc định (để UI/LAN fallback xử lý).
 */
class NetworkChangeReceiver(
    context: Context,
    private val onNetworkLost: () -> Unit = {},
    private val onNetworkChanged: (networkType: String) -> Unit
) {
    companion object {
        private const val TAG = "NetworkChange"
        private const val DEBOUNCE_MS = 800L
    }

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentNetwork: Network? = null
    private var currentType: String = "none"
    private var registered = false
    private var pendingNotify: Runnable? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mainHandler.post { handleDefaultNetwork(network) }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            mainHandler.post { handleDefaultNetwork(network, networkCapabilities) }
        }

        override fun onLost(network: Network) {
            mainHandler.post {
                if (network == currentNetwork) {
                    Log.d(TAG, "Mất mạng mặc định ($currentType), chờ mạng thay thế...")
                    currentNetwork = null
                    currentType = "none"
                    onNetworkLost()
                }
            }
        }
    }

    private fun typeOf(caps: NetworkCapabilities?): String = when {
        caps == null -> "other"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
        else -> "other"
    }

    private fun handleDefaultNetwork(network: Network, caps: NetworkCapabilities? = null) {
        val type = typeOf(caps ?: connectivityManager.getNetworkCapabilities(network))
        val previous = currentNetwork
        if (previous == network && type == currentType) return

        currentNetwork = network
        currentType = type

        // Lần đầu đăng ký chỉ ghi nhận mạng hiện tại, không handover
        if (previous == null && !hadNetworkBefore) {
            hadNetworkBefore = true
            Log.d(TAG, "Mạng ban đầu: $type")
            return
        }
        hadNetworkBefore = true
        if (previous == network) return // chỉ đổi capabilities trên cùng mạng

        Log.d(TAG, "Đổi mạng mặc định -> $type. Kích hoạt Seamless Handover...")
        pendingNotify?.let { mainHandler.removeCallbacks(it) }
        val notify = Runnable { onNetworkChanged(currentType) }
        pendingNotify = notify
        mainHandler.postDelayed(notify, DEBOUNCE_MS)
    }

    private var hadNetworkBefore = false

    /** Loại mạng hiện tại: "wifi" / "cellular" / "ethernet" / "other" / "none" */
    val networkType: String get() = currentType

    fun start() {
        if (registered) return
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            registered = true
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đăng ký theo dõi mạng: ${e.message}")
        }
    }

    fun stop() {
        pendingNotify?.let { mainHandler.removeCallbacks(it) }
        pendingNotify = null
        if (!registered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi hủy đăng ký: ${e.message}")
        }
        registered = false
        currentNetwork = null
        hadNetworkBefore = false
    }
}

package com.example.damthoai.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

/**
 * Thông tin máy chủ Signaling được tìm thấy qua mDNS trong mạng LAN
 */
data class DiscoveredSignalingServer(
    val serviceName: String,
    val hostAddress: String,
    val port: Int,
    val wsUrl: String = "ws://$hostAddress:$port"
)

/**
 * Trình tự động quét tìm Signaling Server trên mạng nội bộ (mDNS / Zeroconf)
 * Tương thích trực tiếp với mdns_broadcaster.py (dịch vụ _bodam._tcp)
 */
class MdnsDiscoveryManager(context: Context) {

    companion object {
        private const val TAG = "MdnsDiscoveryManager"
        private const val SERVICE_TYPE = "_bodam._tcp."
    }

    private val nsdManager: NsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscovering: Boolean = false

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _serverFound = MutableSharedFlow<DiscoveredSignalingServer>(extraBufferCapacity = 1)
    val serverFound: SharedFlow<DiscoveredSignalingServer> = _serverFound.asSharedFlow()

    /**
     * Bắt đầu quét mạng tìm máy chủ bộ đàm
     */
    fun startDiscovery() {
        if (isDiscovering) return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Bắt đầu quét thất bại: mã lỗi $errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Dừng quét thất bại: mã lỗi $errorCode")
                _isSearching.value = false
                isDiscovering = false
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(TAG, "Đang quét mDNS tìm dịch vụ $SERVICE_TYPE trong mạng LAN...")
                _isSearching.value = true
                isDiscovering = true
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(TAG, "Đã dừng quét mDNS.")
                _isSearching.value = false
                isDiscovering = false
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "Tìm thấy dịch vụ: ${serviceInfo?.serviceName}")
                if (serviceInfo == null) return

                // Tiến hành phân giải địa chỉ IP và Port
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "Mất tín hiệu dịch vụ: ${serviceInfo?.serviceName}")
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi gọi discoverServices: ${e.message}", e)
            _isSearching.value = false
            isDiscovering = false
        }
    }

    /**
     * Phân giải thông tin chi tiết IP và cổng của dịch vụ tìm được
     */
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "Phân giải IP dịch vụ thất bại: mã lỗi $errorCode")
            }

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                if (resolvedInfo == null) return

                val host: InetAddress? = resolvedInfo.host
                val port: Int = resolvedInfo.port
                val hostAddress = host?.hostAddress ?: return

                Log.d(TAG, "Đã bắt được Server qua mDNS: $hostAddress:$port")

                val discovered = DiscoveredSignalingServer(
                    serviceName = resolvedInfo.serviceName ?: "BoDamServer",
                    hostAddress = hostAddress,
                    port = port
                )
                _serverFound.tryEmit(discovered)
            }
        }

        try {
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi phân giải dịch vụ: ${e.message}", e)
        }
    }

    /**
     * Dừng quét dịch vụ mDNS
     */
    fun stopDiscovery() {
        if (!isDiscovering) return
        try {
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi dừng NsdManager: ${e.message}", e)
        } finally {
            discoveryListener = null
            isDiscovering = false
            _isSearching.value = false
        }
    }
}

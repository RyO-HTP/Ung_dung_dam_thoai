package com.example.damthoai.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.example.damthoai.platform.DiscoveredServer
import com.example.damthoai.platform.ServerDiscovery
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

/**
 * Trình tự động quét tìm Signaling Server trên mạng nội bộ (mDNS / Zeroconf)
 * Tương thích trực tiếp với mdns_broadcaster.py (dịch vụ _bodam._tcp)
 */
class MdnsDiscoveryManager(context: Context) : ServerDiscovery {

    companion object {
        private const val TAG = "MdnsDiscoveryManager"
        private const val SERVICE_TYPE = "_bodam._tcp."
    }

    private val nsdManager: NsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = 
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    // Cần thiết để duy trì luồng UDP mDNS khi ứng dụng chạy nền
    private var multicastLock: WifiManager.MulticastLock? = null
    
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var isDiscovering: Boolean = false

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _serverFound = MutableSharedFlow<DiscoveredServer>(extraBufferCapacity = 1)
    override val serverFound: SharedFlow<DiscoveredServer> = _serverFound.asSharedFlow()

    /**
     * Bắt đầu quét mạng tìm máy chủ bộ đàm (Client Mode)
     */
    override fun startDiscovery() {
        if (isDiscovering || discoveryListener != null) return

        // Khởi tạo và cấp quyền giữ Multicast Lock để không bị HĐH cắt sóng mDNS
        if (multicastLock == null) {
            multicastLock = wifiManager.createMulticastLock("DamThoai_mDNS_Lock")
            // Không đếm tham chiếu: mỗi lần quét acquire một lần, stopDiscovery release một lần là đủ
            multicastLock?.setReferenceCounted(false)
        }
        multicastLock?.acquire()

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
            discoveryListener = null
            multicastLock?.release()
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

                val discovered = DiscoveredServer(
                    name = resolvedInfo.serviceName ?: "BoDamServer",
                    host = hostAddress,
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
     * Kích hoạt điện thoại này trở thành Host mDNS phát sóng trong mạng LAN (Host Mode)
     * Dùng cho cơ chế thiết bị Android tự làm Signaling Server
     * @param port Cổng dịch vụ (vd: 8080)
     */
    fun registerLocalService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "BoDam_Local_Host"
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(registeredService: NsdServiceInfo) {
                Log.d(TAG, "Đã phát sóng mDNS thành công. Tên dịch vụ: ${registeredService.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Lỗi phát sóng mDNS: mã lỗi $errorCode")
            }
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d(TAG, "Đã dừng phát sóng mDNS.")
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Lỗi dừng phát sóng mDNS: mã lỗi $errorCode")
            }
        }
        
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi gọi registerService: ${e.message}", e)
        }
    }

    /**
     * Dừng mọi dịch vụ dò tìm và phát sóng mDNS
     */
    override fun stopDiscovery() {
        // 1. Dừng chế độ phát Host (nếu có)
        try {
            registrationListener?.let { 
                nsdManager.unregisterService(it) 
                registrationListener = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi hủy đăng ký mDNS Host: ${e.message}")
        }
        
        // 2. Dừng chế độ quét Client (kể cả khi onDiscoveryStarted chưa kịp gọi về)
        if (isDiscovering || discoveryListener != null) {
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
        
        // 3. Hủy bỏ Multicast Lock
        if (multicastLock?.isHeld == true) {
            multicastLock?.release()
        }
    }
}
package com.example.damthoai.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.damthoai.data.fake.NetworkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

enum class NetworkTransport { WIFI, CELLULAR, OTHER, NONE }

interface NetworkStatusSource {
    val status: StateFlow<NetworkStatus>
    val transport: StateFlow<NetworkTransport> get() = MutableStateFlow(NetworkTransport.NONE).asStateFlow()
}

object NetworkStatusProvider {
    var source: NetworkStatusSource = NetworkQualityMonitor
}

/**
 * ĐIỂM NỐI TV2/TV1: sau này có thể thay nguồn ping bằng RTT của WebRTC getStats (candidate-pair currentRoundTripTime)
 * và chuyển mạng Seamless Handover; chỉ cần cấp lại StateFlow cùng kiểu.
 */
object NetworkQualityMonitor : NetworkStatusSource {
    private const val TAG = "NetworkQualityMonitor"

    private const val GOOD_PING_THRESHOLD_MS = 150
    private const val PING_INTERVAL_MS = 5000L
    private const val PING_TIMEOUT_MS = 1500
    private const val MEDIAN_SAMPLE_SIZE = 3
    private const val FAILED_PINGS_FOR_OFFLINE = 3

    private val _status = MutableStateFlow<NetworkStatus>(NetworkStatus.Good(42))
    override val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private val _transport = MutableStateFlow(NetworkTransport.NONE)
    override val transport: StateFlow<NetworkTransport> = _transport.asStateFlow()

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pingJob: Job? = null

    private var isNetworkValidated = false
    private val pingHistory = mutableListOf<Int>()
    private var consecutiveFailedPings = 0
    private var pendingStatusCandidate: NetworkStatus? = null

    @Synchronized
    fun start(context: Context) {
        if (networkCallback != null) return

        val appCtx = context.applicationContext
        val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        connectivityManager = cm

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                isNetworkValidated = validated

                val newTransport = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.OTHER
                    else -> NetworkTransport.NONE
                }
                _transport.value = if (validated) newTransport else NetworkTransport.NONE

                if (!validated) {
                    evaluateAndSetStatus(NetworkStatus.Offline)
                }
            }

            override fun onLost(network: Network) {
                isNetworkValidated = false
                _transport.value = NetworkTransport.NONE
                evaluateAndSetStatus(NetworkStatus.Offline)
            }
        }

        networkCallback = callback
        try {
            cm?.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đăng ký network callback: ${e.message}", e)
        }

        startPingLoop()
    }

    @Synchronized
    fun stop() {
        pingJob?.cancel()
        pingJob = null

        networkCallback?.let { cb ->
            try {
                connectivityManager?.unregisterNetworkCallback(cb)
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi gỡ network callback: ${e.message}", e)
            }
        }
        networkCallback = null
        connectivityManager = null
        isNetworkValidated = false
        _transport.value = NetworkTransport.NONE
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (true) {
                if (isNetworkValidated) {
                    val pingMs = measurePingMs()
                    processPingResult(pingMs)
                } else {
                    evaluateAndSetStatus(NetworkStatus.Offline)
                }
                delay(PING_INTERVAL_MS)
            }
        }
    }

    private suspend fun measurePingMs(): Int? = withContext(Dispatchers.IO) {
        val serverUrl = try {
            // Read from SignalingClient serverUrl getter if available
            // Note: fallback host & port if serverUrl is null/blank
            com.example.damthoai.data.qr.BackendConfig.baseUrl
        } catch (e: Exception) {
            null
        }

        val (host, port) = parseHostAndPort(serverUrl) ?: ("8.8.8.8" to 53)

        val startTime = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), PING_TIMEOUT_MS)
            }
            val duration = (System.currentTimeMillis() - startTime).toInt()
            duration
        } catch (e: Exception) {
            null
        }
    }

    private fun parseHostAndPort(urlStr: String?): Pair<String, Int>? {
        if (urlStr.isNullOrBlank()) return null
        return try {
            val uri = URI(if (!urlStr.contains("://")) "http://$urlStr" else urlStr)
            val host = uri.host ?: return null
            val port = if (uri.port != -1) uri.port else if (uri.scheme == "https" || uri.scheme == "wss") 443 else 80
            host to port
        } catch (e: Exception) {
            null
        }
    }

    private fun processPingResult(pingMs: Int?) {
        if (pingMs != null) {
            consecutiveFailedPings = 0
            pingHistory.add(pingMs)
            if (pingHistory.size > MEDIAN_SAMPLE_SIZE) {
                pingHistory.removeAt(0)
            }
            val medianPing = pingHistory.sorted()[pingHistory.size / 2]
            val candidate = if (medianPing < GOOD_PING_THRESHOLD_MS) {
                NetworkStatus.Good(medianPing)
            } else {
                NetworkStatus.Weak(medianPing)
            }
            evaluateAndSetStatus(candidate)
        } else {
            consecutiveFailedPings++
            if (consecutiveFailedPings >= FAILED_PINGS_FOR_OFFLINE) {
                evaluateAndSetStatus(NetworkStatus.Offline)
            } else {
                val current = _status.value
                val fallbackPing = if (current is NetworkStatus.Good) current.ms else if (current is NetworkStatus.Weak) current.ms else 200
                evaluateAndSetStatus(NetworkStatus.Weak(fallbackPing))
            }
        }
    }

    private fun evaluateAndSetStatus(candidate: NetworkStatus) {
        if (candidate == _status.value) {
            pendingStatusCandidate = null
            return
        }
        // Hysteresis: require 2 consecutive candidate evaluations before transitioning
        if (pendingStatusCandidate == candidate || candidate == NetworkStatus.Offline) {
            _status.value = candidate
            pendingStatusCandidate = null
        } else {
            pendingStatusCandidate = candidate
        }
    }
}

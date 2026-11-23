package com.example.damthoai.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.damthoai.network.model.IceCandidatePayload
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * TV1 - Tuần 5: Local LAN Fallback (mDNS / DNS-SD).
 *
 * Khi hội trường mất Internet nhưng cục phát Wi-Fi vẫn chạy, các điện thoại:
 *  1. Mở một cổng TCP nhỏ và tự quảng bá dịch vụ "_bodampeer._tcp" kèm user_id qua mDNS.
 *  2. Quét mDNS để tìm các máy bộ đàm khác trong cùng mạng Wi-Fi.
 *  3. Trao đổi SDP/ICE trực tiếp với nhau qua TCP (không cần máy chủ).
 *  4. WebRTC kết nối bằng host candidate (IP nội bộ 192.168.x.x) -> âm thanh đi thẳng qua cục phát Wi-Fi.
 *
 * Giao thức trên dây giống hệt WebSocket server (JSON mỗi dòng) nên dùng chung [SignalingMessageParser].
 */
class LocalLanSignaling(
    context: Context,
    private val gson: Gson = Gson()
) : SignalingChannel {

    companion object {
        private const val TAG = "LocalLanSignaling"
        const val SERVICE_TYPE = "_bodampeer._tcp."
        private const val ATTR_USER_ID = "uid"
        private const val CONNECT_TIMEOUT_MS = 2000
        private const val PENDING_TTL_MS = 30_000L
    }

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var localServiceName: String? = null

    @Volatile
    private var myUserId: String? = null

    /** user_id -> địa chỉ TCP của máy đó trong mạng LAN */
    private val peers = ConcurrentHashMap<String, InetSocketAddress>()
    /** Gói tin gửi tới máy chưa tìm thấy -> chờ đến khi mDNS phân giải xong */
    private val pendingByTarget = ConcurrentHashMap<String, ConcurrentLinkedQueue<Pair<Long, String>>>()

    // NsdManager (API < 34) chỉ cho phân giải 1 dịch vụ mỗi lúc -> xếp hàng tuần tự
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    private val _events = MutableSharedFlow<SignalingEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SignalingEvent> = _events.asSharedFlow()

    private val _peerIds = MutableStateFlow<Set<String>>(emptySet())
    /** Danh sách thành viên tìm thấy trong mạng LAN (cho UI hiển thị) */
    val peerIds: StateFlow<Set<String>> = _peerIds.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    /**
     * Bật chế độ LAN: mở cổng TCP, quảng bá và quét mDNS.
     */
    @Synchronized
    fun start(userId: String) {
        if (_isActive.value && myUserId == userId) return
        stop()
        myUserId = userId
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val server = try {
            ServerSocket(0) // cổng ngẫu nhiên, mDNS sẽ quảng bá cổng này
        } catch (e: Exception) {
            Log.e(TAG, "Không mở được cổng TCP LAN: ${e.message}", e)
            return
        }
        serverSocket = server
        _isActive.value = true
        acceptJob = scope.launch { acceptLoop(server) }

        registerService(userId, server.localPort)
        startDiscovery()
        Log.d(TAG, "Bật LAN Fallback cho [$userId] tại cổng ${server.localPort}")
    }

    @Synchronized
    fun stop() {
        if (!_isActive.value && serverSocket == null) return
        registrationListener?.let { runCatching { nsdManager.unregisterService(it) } }
        registrationListener = null
        discoveryListener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        discoveryListener = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        scope.cancel()
        peers.clear()
        pendingByTarget.clear()
        synchronized(resolveQueue) {
            resolveQueue.clear()
            resolving = false
        }
        _peerIds.value = emptySet()
        _isActive.value = false
        Log.d(TAG, "Tắt LAN Fallback")
    }

    // ---------------------------------------------------------------- mDNS

    private fun registerService(userId: String, port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "bodam_" + userId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute(ATTR_USER_ID, userId)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(registered: NsdServiceInfo) {
                // Android có thể đổi tên nếu trùng -> lưu tên thật để bỏ qua chính mình khi quét
                localServiceName = registered.serviceName
                Log.d(TAG, "Đã quảng bá mDNS: ${registered.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Quảng bá mDNS thất bại: $errorCode")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        runCatching { nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { Log.e(TAG, "registerService lỗi: ${it.message}") }
    }

    private fun startDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Đang quét các máy bộ đàm trong mạng LAN...")
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Quét mDNS thất bại: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceName == localServiceName) return
                enqueueResolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val lostId = peers.entries.firstOrNull { (id, _) ->
                    serviceInfo.serviceName.endsWith(id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40))
                }?.key ?: return
                peers.remove(lostId)
                _peerIds.value = peers.keys.toSet()
                Log.d(TAG, "Máy [$lostId] rời mạng LAN")
            }
        }
        discoveryListener = listener
        runCatching { nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { Log.e(TAG, "discoverServices lỗi: ${it.message}") }
    }

    private fun enqueueResolve(info: NsdServiceInfo) {
        synchronized(resolveQueue) {
            resolveQueue.addLast(info)
            if (resolving) return
            resolving = true
        }
        resolveNext()
    }

    private fun resolveNext() {
        val next = synchronized(resolveQueue) {
            val item = resolveQueue.removeFirstOrNull()
            if (item == null) resolving = false
            item
        } ?: return

        @Suppress("DEPRECATION")
        val ok = runCatching {
            nsdManager.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Phân giải ${serviceInfo.serviceName} lỗi: $errorCode")
                    resolveNext()
                }

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    onPeerResolved(resolved)
                    resolveNext()
                }
            })
        }.isSuccess
        if (!ok) resolveNext()
    }

    private fun onPeerResolved(info: NsdServiceInfo) {
        @Suppress("DEPRECATION")
        val host = info.host ?: return
        val uid = info.attributes[ATTR_USER_ID]?.toString(Charsets.UTF_8)
            ?: info.serviceName.removePrefix("bodam_")
        if (uid == myUserId) return
        val address = InetSocketAddress(host, info.port)
        peers[uid] = address
        _peerIds.value = peers.keys.toSet()
        Log.d(TAG, "Tìm thấy máy [$uid] tại ${host.hostAddress}:${info.port}")
        flushPending(uid)
    }

    // ---------------------------------------------------------------- TCP

    private suspend fun acceptLoop(server: ServerSocket) {
        while (scope.isActive && !server.isClosed) {
            val client = try {
                server.accept()
            } catch (e: Exception) {
                break
            }
            scope.launch { readFromPeer(client) }
        }
    }

    private fun readFromPeer(socket: Socket) {
        socket.use { s ->
            runCatching {
                s.soTimeout = 10_000
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    SignalingMessageParser.parse(gson, line)?.let { _events.tryEmit(it) }
                }
            }
        }
    }

    /** Mạng Wi-Fi hiện tại (để bind socket vào Wi-Fi kể cả khi Android đang ưu tiên 4G) */
    @Suppress("DEPRECATION")
    private fun wifiNetwork(): Network? = connectivityManager.allNetworks.firstOrNull { n ->
        connectivityManager.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private fun sendTo(targetId: String, json: String): Boolean {
        if (!_isActive.value) return false
        val address = peers[targetId]
        if (address == null) {
            pendingByTarget.getOrPut(targetId) { ConcurrentLinkedQueue() }
                .add(System.currentTimeMillis() to json)
            Log.d(TAG, "Chưa thấy [$targetId] trong LAN, xếp hàng chờ mDNS")
            return true
        }
        scope.launch { writeToPeer(targetId, address, listOf(json)) }
        return true
    }

    private fun flushPending(targetId: String) {
        val queue = pendingByTarget.remove(targetId) ?: return
        val address = peers[targetId] ?: return
        val now = System.currentTimeMillis()
        val fresh = queue.filter { now - it.first <= PENDING_TTL_MS }.map { it.second }
        if (fresh.isNotEmpty()) scope.launch { writeToPeer(targetId, address, fresh) }
    }

    private fun writeToPeer(targetId: String, address: InetSocketAddress, lines: List<String>) {
        runCatching {
            Socket().use { socket ->
                wifiNetwork()?.let { runCatching { it.bindSocket(socket) } }
                socket.connect(address, CONNECT_TIMEOUT_MS)
                val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
                lines.forEach { writer.write(it); writer.write("\n") }
                writer.flush()
            }
        }.onFailure {
            Log.w(TAG, "Gửi tới [$targetId] qua LAN thất bại: ${it.message}")
        }
    }

    // ---------------------------------------------------------------- SignalingChannel

    override fun sendOffer(targetId: String, sdp: String): Boolean =
        sendTo(targetId, SignalingMessageParser.offer(gson, myUserId, targetId, sdp))

    override fun sendAnswer(targetId: String, sdp: String): Boolean =
        sendTo(targetId, SignalingMessageParser.answer(gson, myUserId, targetId, sdp))

    override fun sendIceCandidate(targetId: String, sdpMid: String?, sdpMLineIndex: Int, candidate: String): Boolean =
        sendTo(targetId, SignalingMessageParser.iceCandidate(gson, myUserId, targetId, sdpMid, sdpMLineIndex, candidate))

    override fun sendIceCandidatesRemoved(targetId: String, candidates: List<IceCandidatePayload>): Boolean =
        sendTo(targetId, SignalingMessageParser.iceCandidatesRemoved(gson, myUserId, targetId, candidates))

    override fun sendHangup(targetId: String): Boolean =
        sendTo(targetId, SignalingMessageParser.hangup(gson, myUserId, targetId))

    override fun sendVoiceNote(
        targetId: String,
        messageId: String,
        channelId: String,
        recordedAtMs: Long,
        durationMs: Long,
        sampleRateHz: Int,
        audioBase64: String
    ): Boolean = sendTo(
        targetId,
        SignalingMessageParser.voiceNote(
            gson = gson,
            senderId = myUserId,
            targetId = targetId,
            messageId = messageId,
            channelId = channelId,
            recordedAtMs = recordedAtMs,
            durationMs = durationMs,
            sampleRateHz = sampleRateHz,
            audioBase64 = audioBase64
        )
    )
}

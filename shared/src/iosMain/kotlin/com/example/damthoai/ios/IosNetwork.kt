package com.example.damthoai.ios

import com.example.damthoai.platform.DiscoveredServer
import com.example.damthoai.platform.LanProbe
import com.example.damthoai.platform.NetworkMonitor
import com.example.damthoai.platform.ServerDiscovery
import kotlinx.cinterop.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import platform.Network.*
import platform.darwin.dispatch_get_main_queue
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.darwin.inet_ntop
import platform.darwin.inet_pton
import platform.posix.*

/**
 * iOS: quét cổng TCP và đọc IP các card mạng bằng POSIX socket (giống AndroidLanProbe).
 */
@OptIn(ExperimentalForeignApi::class)
object IosLanProbe : LanProbe {

    override fun isPortOpen(host: String, port: Int, timeoutMs: Int): Boolean = memScoped {
        val fd = socket(AF_INET, SOCK_STREAM, 0)
        if (fd < 0) return false
        try {
            // Kết nối không chặn để tự đặt thời gian chờ
            val flags = fcntl(fd, F_GETFL, 0)
            fcntl(fd, F_SETFL, flags or O_NONBLOCK)

            val addr = alloc<sockaddr_in>()
            addr.sin_len = sizeOf<sockaddr_in>().toUByte()
            addr.sin_family = AF_INET.toUByte()
            // Thứ tự byte mạng (big-endian) — iPhone là little-endian
            addr.sin_port = (((port and 0xFF) shl 8) or ((port shr 8) and 0xFF)).toUShort()
            if (inet_pton(AF_INET, host, addr.sin_addr.ptr) != 1) return false

            val rc = connect(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt())
            if (rc == 0) return true
            if (errno != EINPROGRESS) return false

            val pfd = alloc<pollfd>()
            pfd.fd = fd
            pfd.events = POLLOUT.toShort()
            if (poll(pfd.ptr, 1u, timeoutMs) <= 0) return false

            val soError = alloc<IntVar>()
            val len = alloc<UIntVar>()
            len.value = sizeOf<IntVar>().toUInt()
            if (getsockopt(fd, SOL_SOCKET, SO_ERROR, soError.ptr, len.ptr) != 0) return false
            soError.value == 0
        } finally {
            close(fd)
        }
    }

    override fun lanScanTargets(): List<String> = memScoped {
        val ownIps = mutableSetOf<String>()
        val ifap = alloc<CPointerVar<ifaddrs>>()
        if (getifaddrs(ifap.ptr) != 0) return emptyList()
        try {
            var cursor = ifap.value
            while (cursor != null) {
                val ifa = cursor.pointed
                val name = ifa.ifa_name?.toKString().orEmpty()
                val sa = ifa.ifa_addr
                // en* = Wi-Fi / Ethernet, bridge* = điểm phát hotspot của chính máy
                if (sa != null && sa.pointed.sa_family.toInt() == AF_INET &&
                    (name.startsWith("en") || name.startsWith("bridge"))
                ) {
                    val sin = sa.reinterpret<sockaddr_in>().pointed
                    val buffer = allocArray<ByteVar>(INET_ADDRSTRLEN)
                    if (inet_ntop(AF_INET, sin.sin_addr.ptr, buffer, INET_ADDRSTRLEN.toUInt()) != null) {
                        val ip = buffer.toKString()
                        if (isPrivateIpv4(ip)) ownIps += ip
                    }
                }
                cursor = ifa.ifa_next
            }
        } finally {
            freeifaddrs(ifap.value)
        }
        val targets = LinkedHashSet<String>()
        for (ip in ownIps) {
            val base = ip.substringBeforeLast('.')
            for (i in 1..254) targets += "$base.$i"
        }
        targets.removeAll(ownIps)
        targets.toList()
    }

    private fun isPrivateIpv4(ip: String): Boolean {
        val p = ip.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4) return false
        return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
    }
}

/** iOS: báo đổi mạng Wi-Fi <-> 4G bằng NWPathMonitor (Seamless Handover). */
@OptIn(ExperimentalForeignApi::class)
class IosNetworkMonitor : NetworkMonitor {
    private var monitor: nw_path_monitor_t = null

    override fun start(onNetworkChanged: () -> Unit) {
        stop()
        val pathMonitor = nw_path_monitor_create()
        var lastType: Int? = null
        nw_path_monitor_set_update_handler(pathMonitor) { path ->
            val type = when {
                nw_path_uses_interface_type(path, nw_interface_type_wifi) -> 1
                nw_path_uses_interface_type(path, nw_interface_type_cellular) -> 2
                else -> 0
            }
            val satisfied = nw_path_get_status(path) == nw_path_status_satisfied
            if (lastType != null && lastType != type && type != 0 && satisfied) {
                onNetworkChanged()
            }
            lastType = type
        }
        nw_path_monitor_set_queue(pathMonitor, dispatch_get_main_queue())
        nw_path_monitor_start(pathMonitor)
        monitor = pathMonitor
    }

    override fun stop() {
        monitor?.let { nw_path_monitor_cancel(it) }
        monitor = null
    }
}

/** iOS: tìm máy chủ trong LAN bằng Bonjour (NetServiceBrowser chạy bên Swift). */
class IosServerDiscovery(private val bridge: IosNativeBridge) : ServerDiscovery {
    private val _serverFound = MutableSharedFlow<DiscoveredServer>(extraBufferCapacity = 4)
    override val serverFound: SharedFlow<DiscoveredServer> = _serverFound.asSharedFlow()

    override fun startDiscovery() {
        bridge.startBonjour(object : ServerFoundListener {
            override fun onServerFound(name: String, host: String, port: Int) {
                _serverFound.tryEmit(DiscoveredServer(name = name, host = host, port = port))
            }
        })
    }

    override fun stopDiscovery() = bridge.stopBonjour()
}

package com.example.damthoai.network

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Tìm máy chủ Signaling đang hoạt động, không phụ thuộc một địa chỉ IP cố định.
 *
 * Thứ tự tìm:
 *  1. Lần lượt các địa chỉ ứng viên do [com.example.damthoai.service.DamThoaiCallManager] đưa vào
 *     (địa chỉ tự nhập → máy chủ đám mây → địa chỉ tìm được lần trước).
 *  2. Nếu không địa chỉ nào sống: tìm trong mạng LAN bằng mDNS và quét cổng 8765 trên toàn mạng Wi-Fi
 *     (chạy song song). Quét cổng vẫn tìm thấy khi router chặn mDNS, và khi điện thoại phát hotspot.
 */
class ServerLocator(private val mdns: MdnsDiscoveryManager) {

    companion object {
        private const val TAG = "ServerLocator"

        /** Cổng mặc định của signaling_server.py. */
        const val DEFAULT_PORT = 8765

        private const val PROBE_TIMEOUT_MS = 500        // Mỗi lần thử mở cổng một máy trong LAN
        private const val LAN_SERVER_TIMEOUT_MS = 1500  // Thử địa chỉ LAN đã biết
        private const val SCAN_PARALLELISM = 64         // Số máy được thử cùng lúc khi quét mạng
        private const val LAN_SEARCH_TIMEOUT_MS = 8000L // Tổng thời gian chờ mDNS + quét mạng

        // Render free ngủ khi không ai dùng; lần gọi đầu đánh thức máy chủ mất khoảng 30-60 giây
        private const val CLOUD_WAKE_TIMEOUT_MS = 75_000L

        // Card mạng di động / ảo: quét các dải này vô ích
        private val NON_LAN_INTERFACE_PREFIXES = listOf("rmnet", "ccmni", "pdp", "v4-", "clat", "dummy", "tun", "lo")

        // Client HTTP để gọi /healthz (dùng lại kết nối giữa các lần gọi)
        private val healthClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(CLOUD_WAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()
        }

        // --------------------------------------------------------------
        // Đọc thông tin từ URL
        // --------------------------------------------------------------

        fun hostOf(url: String?): String? =
            runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }

        /**
         * Cổng thật của URL:
         *  - Có ghi cổng (ws://192.168.1.8:9000) → cổng đó.
         *  - wss:// / https:// không ghi cổng (máy chủ Internet) → 443.
         *  - Còn lại (VD người dùng chỉ nhập IP, app tự thêm "ws://") → 8765 của signaling_server.py.
         */
        fun portOf(url: String?): Int {
            val uri = runCatching { URI(url) }.getOrNull() ?: return DEFAULT_PORT
            if (uri.port > 0) return uri.port
            return when (uri.scheme?.lowercase()) {
                "wss", "https" -> 443
                else -> DEFAULT_PORT
            }
        }

        /** Địa chỉ trong mạng nội bộ (10.x, 172.16-31.x, 192.168.x, 127.x). */
        fun isLanUrl(url: String?): Boolean {
            val parts = hostOf(url)?.split('.')?.mapNotNull { it.toIntOrNull() } ?: return false
            if (parts.size != 4) return false
            return parts[0] == 10 || parts[0] == 127 ||
                (parts[0] == 192 && parts[1] == 168) ||
                (parts[0] == 172 && parts[1] in 16..31)
        }

        /** Trang kiểm tra sức khỏe của máy chủ: wss://host → https://host/healthz. */
        fun healthUrlOf(url: String): String? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            val host = uri.host ?: return null
            val secure = uri.scheme.equals("wss", true) || uri.scheme.equals("https", true)
            val portPart = if (uri.port > 0) ":${uri.port}" else ""
            return "${if (secure) "https" else "http"}://$host$portPart/healthz"
        }

        // --------------------------------------------------------------
        // Kiểm tra máy chủ (gọi trên luồng IO: các hàm này chặn luồng)
        // --------------------------------------------------------------

        /** Thử mở kết nối TCP tới một cổng. */
        fun isPortOpen(host: String, port: Int, timeoutMs: Int = PROBE_TIMEOUT_MS): Boolean = try {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs); true }
        } catch (e: Exception) {
            false
        }

        /**
         * Đây có thật là Signaling Server đang chạy không?
         *  - Địa chỉ LAN: mở được cổng là đủ (nhanh).
         *  - Địa chỉ Internet: phải gọi /healthz và nhận "OK". Chỉ mở cổng 443 là không đủ, vì tên miền
         *    *.onrender.com luôn mở cổng kể cả khi chưa deploy. Lệnh gọi này cũng đánh thức máy chủ
         *    Render đang ngủ, nên được chờ lâu (tới [CLOUD_WAKE_TIMEOUT_MS]).
         */
        fun isServerAlive(url: String): Boolean {
            val host = hostOf(url) ?: return false
            if (isLanUrl(url)) return isPortOpen(host, portOf(url), LAN_SERVER_TIMEOUT_MS)
            val health = healthUrlOf(url) ?: return false
            return try {
                healthClient.newCall(Request.Builder().url(health).build()).execute().use { response ->
                    response.code == 200 && response.body?.string().orEmpty().startsWith("OK")
                }
            } catch (e: Exception) {
                false
            }
        }

        /** Các IPv4 cùng dải /24 với mọi mạng LAN (Wi-Fi, hotspot) mà máy đang tham gia, trừ chính máy. */
        fun lanScanTargets(): List<String> {
            val targets = LinkedHashSet<String>()
            val ownIps = mutableSetOf<String>()
            val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull().orEmpty()
            for (nif in interfaces) {
                if (runCatching { !nif.isUp || nif.isLoopback }.getOrDefault(true)) continue
                if (NON_LAN_INTERFACE_PREFIXES.any { nif.name.lowercase().startsWith(it) }) continue
                for (ia in nif.interfaceAddresses) {
                    val addr = ia.address as? Inet4Address ?: continue
                    if (!addr.isSiteLocalAddress) continue
                    val ip = addr.hostAddress ?: continue
                    ownIps += ip
                    val base = ip.substringBeforeLast('.')
                    for (i in 1..254) targets += "$base.$i"
                }
            }
            targets.removeAll(ownIps)
            return targets.toList()
        }
    }

    /**
     * Thử lần lượt [candidates] theo thứ tự ưu tiên, rồi mới tìm trong mạng LAN.
     * Thử LẦN LƯỢT (không song song) là có chủ ý: mọi máy phải ưu tiên cùng một máy chủ đám mây,
     * nếu máy nào tìm thấy server LAN trước sẽ vào nhầm server khác và không thấy nhau.
     *
     * @return URL của máy chủ đang hoạt động, hoặc null nếu không thấy.
     */
    suspend fun locate(candidates: List<String>): String? {
        for (url in candidates) {
            if (withContext(Dispatchers.IO) { isServerAlive(url) }) {
                Log.d(TAG, "Máy chủ đang hoạt động: $url")
                return url
            }
            Log.d(TAG, "Máy chủ không phản hồi: $url")
        }
        // Quét LAN dùng cổng của địa chỉ LAN đã biết (nếu có), mặc định 8765
        val port = candidates.firstOrNull { isLanUrl(it) }?.let { portOf(it) } ?: DEFAULT_PORT
        return locateOnLan(port)
    }

    /** mDNS và quét cổng chạy song song; lấy kết quả đến trước. */
    private suspend fun locateOnLan(port: Int): String? {
        Log.d(TAG, "Tìm máy chủ trong mạng LAN (cổng $port)...")
        val found = withTimeoutOrNull(LAN_SEARCH_TIMEOUT_MS) {
            coroutineScope {
                val result = CompletableDeferred<String>()
                val mdnsJob = launch {
                    mdns.serverFound.collect { server -> result.complete(server.wsUrl) }
                }
                mdns.startDiscovery()
                val scanJob = launch {
                    // Quét xong mà không thấy thì không làm gì: vẫn chờ mDNS tới hết thời gian
                    scanLan(port)?.let { host -> result.complete("ws://$host:$port") }
                }
                try {
                    result.await()
                } finally {
                    mdnsJob.cancel()
                    scanJob.cancel()
                    mdns.stopDiscovery()
                }
            }
        }
        if (found != null) Log.d(TAG, "Tìm thấy máy chủ LAN: $found")
        else Log.w(TAG, "Không tìm thấy máy chủ cổng $port trong mạng LAN")
        return found
    }

    /**
     * Thử mở cổng trên mọi địa chỉ trong mạng LAN. [SCAN_PARALLELISM] "thợ" cùng lấy địa chỉ kế tiếp
     * từ danh sách; một thợ tìm thấy là tất cả dừng. (~254 địa chỉ × 0,5s / 64 thợ ≈ 2 giây.)
     */
    private suspend fun scanLan(port: Int): String? = withContext(Dispatchers.IO) {
        val targets = lanScanTargets()
        if (targets.isEmpty()) return@withContext null
        Log.d(TAG, "Quét ${targets.size} địa chỉ LAN tìm cổng $port")
        val nextIndex = AtomicInteger(0)
        val found = AtomicReference<String?>(null)
        coroutineScope {
            repeat(minOf(SCAN_PARALLELISM, targets.size)) {
                launch {
                    while (found.get() == null) {
                        val host = targets.getOrNull(nextIndex.getAndIncrement()) ?: break
                        if (isPortOpen(host, port)) found.compareAndSet(null, host)
                    }
                }
            }
        }
        found.get()
    }
}

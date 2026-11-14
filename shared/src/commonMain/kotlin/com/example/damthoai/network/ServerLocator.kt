package com.example.damthoai.network

import com.example.damthoai.platform.LanProbe
import com.example.damthoai.platform.ServerDiscovery
import com.example.damthoai.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

/**
 * Tìm máy chủ Signaling đang hoạt động, không phụ thuộc một địa chỉ IP cố định.
 *
 * Thứ tự tìm:
 *  1. Lần lượt các địa chỉ ứng viên do [com.example.damthoai.service.DamThoaiCallManager] đưa vào
 *     (địa chỉ tự nhập → máy chủ đám mây → địa chỉ tìm được lần trước).
 *  2. Nếu không địa chỉ nào sống: tìm trong mạng LAN bằng mDNS và quét cổng 8765 trên toàn mạng Wi-Fi
 *     (chạy song song). Quét cổng vẫn tìm thấy khi router chặn mDNS, và khi điện thoại phát hotspot.
 */
class ServerLocator(
    private val mdns: ServerDiscovery,
    private val lanProbe: LanProbe
) {

    companion object {
        private const val TAG = "ServerLocator"

        /** Cổng mặc định của signaling_server.py. */
        const val DEFAULT_PORT = 8765

        const val PROBE_TIMEOUT_MS = 500                // Mỗi lần thử mở cổng một máy trong LAN
        private const val LAN_SERVER_TIMEOUT_MS = 1500  // Thử địa chỉ LAN đã biết
        private const val SCAN_PARALLELISM = 64         // Số máy được thử cùng lúc khi quét mạng
        private const val LAN_SEARCH_TIMEOUT_MS = 8000L // Tổng thời gian chờ mDNS + quét mạng

        // Render free ngủ khi không ai dùng; lần gọi đầu đánh thức máy chủ mất khoảng 30-60 giây
        private const val CLOUD_WAKE_TIMEOUT_MS = 75_000L

        // Client HTTP để gọi /healthz (dùng lại kết nối giữa các lần gọi)
        private val healthClient: HttpClient by lazy {
            createHttpClient {
                install(HttpTimeout) {
                    connectTimeoutMillis = 5_000
                    requestTimeoutMillis = CLOUD_WAKE_TIMEOUT_MS
                    socketTimeoutMillis = CLOUD_WAKE_TIMEOUT_MS
                }
                expectSuccess = false
            }
        }

        // --------------------------------------------------------------
        // Đọc thông tin từ URL (bộ phân tích tối giản, chạy được trên mọi nền tảng)
        // --------------------------------------------------------------

        private class ParsedUrl(val scheme: String, val host: String, val port: Int)

        private fun parse(url: String?): ParsedUrl? {
            if (url.isNullOrBlank()) return null
            val schemeEnd = url.indexOf("://")
            if (schemeEnd <= 0) return null
            val scheme = url.substring(0, schemeEnd).lowercase()
            val authority = url.substring(schemeEnd + 3).takeWhile { it != '/' && it != '?' && it != '#' }
            if (authority.isEmpty() || authority.any { it.isWhitespace() }) return null
            val hostPort = authority.substringAfterLast('@')
            val colon = hostPort.lastIndexOf(':')
            val host: String
            val port: Int
            if (colon >= 0 && !hostPort.endsWith("]")) {
                host = hostPort.substring(0, colon)
                port = hostPort.substring(colon + 1).toIntOrNull() ?: return null
            } else {
                host = hostPort
                port = -1
            }
            if (host.isBlank()) return null
            return ParsedUrl(scheme, host, port)
        }

        fun hostOf(url: String?): String? = parse(url)?.host

        /**
         * Cổng thật của URL:
         *  - Có ghi cổng (ws://192.168.1.8:9000) → cổng đó.
         *  - wss:// / https:// không ghi cổng (máy chủ Internet) → 443.
         *  - Còn lại (VD người dùng chỉ nhập IP, app tự thêm "ws://") → 8765 của signaling_server.py.
         */
        fun portOf(url: String?): Int {
            val parsed = parse(url) ?: return DEFAULT_PORT
            if (parsed.port > 0) return parsed.port
            return when (parsed.scheme) {
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
            val parsed = parse(url) ?: return null
            val secure = parsed.scheme == "wss" || parsed.scheme == "https"
            val portPart = if (parsed.port > 0) ":${parsed.port}" else ""
            return "${if (secure) "https" else "http"}://${parsed.host}$portPart/healthz"
        }

        /**
         * Đây có thật là Signaling Server đang chạy không?
         *  - Địa chỉ LAN: mở được cổng là đủ (nhanh).
         *  - Địa chỉ Internet: phải gọi /healthz và nhận "OK". Chỉ mở cổng 443 là không đủ, vì tên miền
         *    *.onrender.com luôn mở cổng kể cả khi chưa deploy. Lệnh gọi này cũng đánh thức máy chủ
         *    Render đang ngủ, nên được chờ lâu (tới [CLOUD_WAKE_TIMEOUT_MS]).
         */
        suspend fun isServerAlive(url: String, lanProbe: LanProbe? = null): Boolean {
            val host = hostOf(url) ?: return false
            if (isLanUrl(url) && lanProbe != null) {
                return withContext(Dispatchers.IO) { lanProbe.isPortOpen(host, portOf(url), LAN_SERVER_TIMEOUT_MS) }
            }
            val health = healthUrlOf(url) ?: return false
            return try {
                val response = healthClient.get(health)
                response.status.value == 200 && response.bodyAsText().startsWith("OK")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
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
            if (isServerAlive(url, lanProbe)) {
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
    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun scanLan(port: Int): String? = withContext(Dispatchers.IO) {
        val targets = lanProbe.lanScanTargets()
        if (targets.isEmpty()) return@withContext null
        Log.d(TAG, "Quét ${targets.size} địa chỉ LAN tìm cổng $port")
        val nextIndex = AtomicInt(0)
        val found = AtomicReference<String?>(null)
        coroutineScope {
            repeat(minOf(SCAN_PARALLELISM, targets.size)) {
                launch {
                    while (found.load() == null) {
                        val host = targets.getOrNull(nextIndex.fetchAndIncrement()) ?: break
                        if (lanProbe.isPortOpen(host, port, PROBE_TIMEOUT_MS)) found.compareAndSet(null, host)
                    }
                }
            }
        }
        found.load()
    }
}

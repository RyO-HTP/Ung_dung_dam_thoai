package com.example.damthoai.network

import com.example.damthoai.platform.LanProbe
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/** Android/JVM: mở socket TCP và đọc IP các card mạng để quét máy chủ trong LAN. */
object AndroidLanProbe : LanProbe {
    // Card mạng di động / ảo: quét các dải này vô ích
    private val NON_LAN_INTERFACE_PREFIXES = listOf("rmnet", "ccmni", "pdp", "v4-", "clat", "dummy", "tun", "lo")

    override fun isPortOpen(host: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs); true }
    } catch (e: Exception) {
        false
    }

    override fun lanScanTargets(): List<String> {
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

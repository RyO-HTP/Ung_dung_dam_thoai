package com.example.damthoai

import com.example.damthoai.network.ServerLocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class ServerLocatorTest {

    @Test
    fun portOf_readsPortOrFallsBackToDefault() {
        assertEquals(9000, ServerLocator.portOf("ws://192.168.1.8:9000"))
        assertEquals(8765, ServerLocator.portOf("ws://192.168.1.8"))
        assertEquals(8765, ServerLocator.portOf(""))
        assertEquals(8765, ServerLocator.portOf(null))
        // Địa chỉ Internet không ghi cổng: theo giao thức
        assertEquals(443, ServerLocator.portOf("wss://abc.trycloudflare.com"))
        assertEquals(8765, ServerLocator.portOf("ws://example.com"))
    }

    @Test
    fun hostOf_handlesBlankAndInvalid() {
        assertEquals("192.168.1.8", ServerLocator.hostOf("ws://192.168.1.8:8765"))
        assertNull(ServerLocator.hostOf(""))
        assertNull(ServerLocator.hostOf(null))
        assertNull(ServerLocator.hostOf("ws://bad host:8765"))
    }

    @Test
    fun isLanUrl_andHealthUrl() {
        assertTrue(ServerLocator.isLanUrl("ws://192.168.1.8:8765"))
        assertTrue(ServerLocator.isLanUrl("ws://10.0.0.5:8765"))
        assertTrue(ServerLocator.isLanUrl("ws://172.20.1.1:8765"))
        assertFalse(ServerLocator.isLanUrl("wss://damthoai-signaling.onrender.com"))
        assertFalse(ServerLocator.isLanUrl("ws://8.8.8.8:8765"))
        assertEquals("https://abc.onrender.com/healthz", ServerLocator.healthUrlOf("wss://abc.onrender.com"))
        assertEquals("http://192.168.1.8:8765/healthz", ServerLocator.healthUrlOf("ws://192.168.1.8:8765"))
    }

    /** Máy chủ HTTP tối giản: trả một phản hồi cố định cho mọi yêu cầu. */
    private fun withHttpServer(status: String, body: String, block: (port: Int) -> Unit) {
        ServerSocket(0).use { server ->
            val worker = Thread {
                runCatching {
                    while (!server.isClosed) {
                        server.accept().use { sock ->
                            val reader = sock.getInputStream().bufferedReader()
                            while (reader.readLine()?.isNotEmpty() == true) { /* bỏ qua header */ }
                            val bytes = body.toByteArray()
                            sock.getOutputStream().write(
                                ("HTTP/1.1 $status\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray() + bytes
                            )
                        }
                    }
                }
            }.apply { isDaemon = true; start() }
            block(server.localPort)
            worker.interrupt()
        }
    }

    @Test
    fun isServerAlive_requiresHealthyResponseNotJustOpenPort() {
        // Cổng mở nhưng không phải Signaling Server (VD tên miền *.onrender.com chưa deploy) → không sống.
        // Dùng "localhost" (không phải IP LAN) để đi đường kiểm tra /healthz.
        withHttpServer("404 Not Found", "Not Found") { port ->
            assertFalse(ServerLocator.isServerAlive("ws://localhost:$port"))
        }
        withHttpServer("200 OK", "OK 0 online") { port ->
            assertTrue(ServerLocator.isServerAlive("ws://localhost:$port"))
        }
    }

    @Test
    fun isPortOpen_detectsListeningAndClosedPorts() {
        ServerSocket(0).use { server ->
            assertTrue(ServerLocator.isPortOpen("127.0.0.1", server.localPort))
        }
        val closedPort = ServerSocket(0).use { it.localPort }
        assertFalse(ServerLocator.isPortOpen("127.0.0.1", closedPort, 300))
    }

    @Test
    fun lanScanTargets_neverContainsLoopbackAndStaysWithinSlash24() {
        val targets = ServerLocator.lanScanTargets()
        assertTrue(targets.none { it.startsWith("127.") })
        assertTrue(targets.all { it.split('.').size == 4 })
    }
}

package com.example.damthoai

import android.util.Base64
import com.example.damthoai.data.qr.BackendConfig
import com.example.damthoai.data.qr.QrParseResult
import com.example.damthoai.data.qr.QrTokenParser
import com.example.damthoai.data.qr.QrTokenPayload
import com.example.damthoai.data.qr.RemoteQrCardsRepository
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class RemoteQrCardsRepositoryTest {

    @Test
    fun backendConfig_hasDefaultBaseUrlFromBuildConfig() {
        assertFalse("DEFAULT_BASE_URL must not be blank", BackendConfig.DEFAULT_BASE_URL.isBlank())
        assertNotNull("BackendConfig.baseUrl must be set by default at startup", BackendConfig.baseUrl)
        assertTrue(
            "DEFAULT_BASE_URL must start with http:// or https://",
            BackendConfig.DEFAULT_BASE_URL.startsWith("http://") ||
                BackendConfig.DEFAULT_BASE_URL.startsWith("https://")
        )
    }

    @Test
    fun getRoleCards_fetchesFromQrRolesEndpointAndParsesValidTokens() = runBlocking {
        val gson = Gson()
        val exp = (System.currentTimeMillis() / 1000) + 86400L
        val requestedPath = AtomicReference("")

        val departments = listOf(
            Triple("ALL_EVENT", "Toàn sự kiện", 1),
            Triple("STAGE", "Sân khấu", 2),
            Triple("LOGISTICS_SEC", "Hậu cần – An ninh", 2),
            Triple("TECHNICAL", "Kỹ thuật", 2),
            Triple("RECEPTION", "Đón tiếp", 3)
        )

        val cardsJson = departments.mapIndexed { idx, (deptCode, deptTitle, level) ->
            val payload = QrTokenPayload(
                eventId = 1L,
                eventName = "Đêm nhạc Chào Tân sinh viên",
                pinCode = "240826",
                deptCode = deptCode,
                deptTitle = deptTitle,
                roleTitle = deptTitle,
                userLevel = level,
                priority = 4 - level,
                primaryCh = (idx + 1).toLong(),
                primaryChCode = "ch_0${idx + 1}",
                listenCh = listOf(1L, (idx + 1).toLong()),
                uiMode = "PRO_MULTI_CHANNEL",
                exp = exp
            )
            val encoded = Base64.encodeToString(
                gson.toJson(payload).toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            val deepLink = "damthoai://join?token=$encoded.0123456789abcdef0123456789abcdef"
            mapOf(
                "deptCode" to deptCode,
                "deptTitle" to deptTitle,
                "departmentCode" to deptCode,
                "departmentTitle" to deptTitle,
                "roleTitle" to deptTitle,
                "userLevel" to level,
                "qrDeepLinkUri" to deepLink,
                "expiresAt" to exp
            )
        }

        val responseBytes = gson.toJson(
            mapOf(
                "eventId" to 1,
                "cards" to cardsJson,
                "departmentCards" to cardsJson
            )
        ).toByteArray(Charsets.UTF_8)

        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { serverSocket ->
            val serverThread = thread(start = true, isDaemon = true) {
                serverSocket.accept().use { client ->
                    val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                    val requestLine = reader.readLine().orEmpty()
                    requestedPath.set(requestLine.split(" ").getOrElse(1) { "" })
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val header = (
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json; charset=utf-8\r\n" +
                            "Content-Length: ${responseBytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray(Charsets.UTF_8)
                    val out = client.getOutputStream()
                    out.write(header)
                    out.write(responseBytes)
                    out.flush()
                }
            }

            val baseUrl = "http://127.0.0.1:${serverSocket.localPort}"
            val repo = RemoteQrCardsRepository(baseUrl)
            val cards = repo.getRoleCards(1L)
            serverThread.join(3000)

            assertEquals("/api/v1/events/qr-roles?eventId=1", requestedPath.get())
            assertEquals(5, cards.size)

            cards.forEach { card ->
                val parsed = QrTokenParser.parse(card.deepLinkUri)
                assertTrue("Expected valid QR token for ${card.deptCode}", parsed is QrParseResult.Valid)
                val valid = parsed as QrParseResult.Valid
                assertEquals(card.deptCode, valid.payload.deptCode)
                assertEquals(card.userLevel, valid.payload.userLevel)
            }
        }
    }
}

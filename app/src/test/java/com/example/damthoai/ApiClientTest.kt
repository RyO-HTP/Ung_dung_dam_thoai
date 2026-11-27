package com.example.damthoai

import com.example.damthoai.data.qr.BackendConfig
import com.example.damthoai.data.remote.ApiClient
import com.example.damthoai.data.remote.ApiException
import com.example.damthoai.data.remote.UserSessionDto
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        BackendConfig.setBaseUrlForTest(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
        BackendConfig.setBaseUrlForTest(null)
    }

    @Test
    fun testGetRequest() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"status\":\"ok\"}"))
        val result = ApiClient.get<Map<String, String>>("/test-get")
        assertTrue(result.isSuccess)
        assertEquals("ok", result.getOrNull()?.get("status"))
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
    }

    @Test
    fun testPostRequestWithUserIdString() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"success\":true}"))
        val body = mapOf("userId" to "Minh_Hau_101_a1b2", "primaryTxChannel" to 2)
        val result = ApiClient.post<Map<String, Boolean>>("/api/v1/users/switch-channel", body)
        assertTrue(result.isSuccess)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.body.readUtf8().contains("Minh_Hau_101_a1b2"))
    }

    @Test
    fun testPatchAndDeleteRequests() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"patched\":true}"))
        val patchRes = ApiClient.patch<Map<String, Boolean>>("/patch-test", mapOf("name" to "New Name"))
        assertTrue(patchRes.isSuccess)
        assertEquals("PATCH", server.takeRequest().method)

        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val deleteRes = ApiClient.delete<Unit>("/delete-test")
        assertTrue(deleteRes.isSuccess)
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun testListParsingWithTypeToken() = runBlocking {
        val jsonArray = "[{\"userId\":\"user_1\",\"eventId\":1,\"userLevel\":1,\"rolePriority\":3,\"primaryTxChannel\":1,\"online\":true}]"
        server.enqueue(MockResponse().setResponseCode(200).setBody(jsonArray))
        val result = ApiClient.get<List<UserSessionDto>>("/members")
        assertTrue(result.isSuccess)
        val list = result.getOrNull()
        assertNotNull(list)
        assertEquals(1, list?.size)
        assertEquals("user_1", list?.first()?.resolvedUserId)
    }

    @Test
    fun testErrorMapping403_404_409_410_429() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("{\"error\":\"USER_NOT_IN_EVENT\",\"message\":\"User not in event\"}"))
        val r403 = ApiClient.get<Unit>("/err")
        assertTrue(r403.isFailure)
        val ex403 = r403.exceptionOrNull() as? ApiException
        assertNotNull(ex403)
        assertEquals(403, ex403?.httpCode)
        assertEquals("USER_NOT_IN_EVENT", ex403?.errorCode)
        assertEquals("User not in event", ex403?.message)

        server.enqueue(MockResponse().setResponseCode(404).setBody("{\"error\":\"EVENT_NOT_FOUND\",\"message\":\"Not found\"}"))
        val r404 = ApiClient.get<Unit>("/err")
        assertEquals(404, (r404.exceptionOrNull() as? ApiException)?.httpCode)

        server.enqueue(MockResponse().setResponseCode(409).setBody("{\"error\":\"ADMIN_CODE_TAKEN\",\"message\":\"Taken\"}"))
        val r409 = ApiClient.get<Unit>("/err")
        assertEquals(409, (r409.exceptionOrNull() as? ApiException)?.httpCode)

        server.enqueue(MockResponse().setResponseCode(410).setBody("{\"error\":\"QR_REVOKED\",\"message\":\"Revoked\"}"))
        val r410 = ApiClient.get<Unit>("/err")
        assertEquals(410, (r410.exceptionOrNull() as? ApiException)?.httpCode)

        server.enqueue(MockResponse().setResponseCode(429).setBody("{\"error\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}"))
        val r429 = ApiClient.get<Unit>("/err")
        assertEquals(429, (r429.exceptionOrNull() as? ApiException)?.httpCode)
    }

    @Test
    fun testMissingBaseUrlThrowsConfigMissing() = runBlocking {
        BackendConfig.setBaseUrlForTest(null)
        val result = ApiClient.get<Unit>("/test")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as? ApiException
        assertNotNull(ex)
        assertEquals("CONFIG_MISSING", ex?.errorCode)
    }
}

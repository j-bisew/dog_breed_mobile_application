package com.woofdetect.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.woofdetect.auth.TokenManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockTokenManager: TokenManager
    private lateinit var interceptor: AuthInterceptor
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val context = ApplicationProvider.getApplicationContext<Context>()
        interceptor = AuthInterceptor(context)

        mockTokenManager = mockk()
        val field = AuthInterceptor::class.java.getDeclaredField("tokenManager")
        field.isAccessible = true
        field.set(interceptor, mockTokenManager)

        client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `request without token passes through unchanged`() {
        coEvery { mockTokenManager.getToken() } returns flowOf(null)
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()

        val response = client.newCall(request).execute()
        val recordedRequest = mockWebServer.takeRequest()

        assertNull(recordedRequest.getHeader("Authorization"))
        assertEquals(200, response.code)
    }

    @Test
    fun `request with empty token passes through unchanged`() {
        coEvery { mockTokenManager.getToken() } returns flowOf("")
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()

        val response = client.newCall(request).execute()
        val recordedRequest = mockWebServer.takeRequest()

        assertNull(recordedRequest.getHeader("Authorization"))
        assertEquals(200, response.code)
    }

    @Test
    fun `request with valid token adds Bearer header`() {
        coEvery { mockTokenManager.getToken() } returns flowOf("valid_token_123")
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()

        val response = client.newCall(request).execute()
        val recordedRequest = mockWebServer.takeRequest()

        assertEquals("Bearer valid_token_123", recordedRequest.getHeader("Authorization"))
        assertEquals(200, response.code)
    }

    @Test
    fun `request with existing Authorization header passes through unchanged`() {
        coEvery { mockTokenManager.getToken() } returns flowOf("valid_token_123")
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .header("Authorization", "Bearer existing_token")
            .build()

        val response = client.newCall(request).execute()
        val recordedRequest = mockWebServer.takeRequest()

        assertEquals("Bearer existing_token", recordedRequest.getHeader("Authorization"))
        assertEquals(200, response.code)
    }
}
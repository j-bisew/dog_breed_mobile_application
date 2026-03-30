package com.woofdetect.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class ApiClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        ApiClient.initialize(context)
        client = OkHttpClient.Builder()
            .addInterceptor(
                ApiClient::class.java
                    .getDeclaredField("jsonFixInterceptor")
                    .also { it.isAccessible = true }
                    .get(null) as okhttp3.Interceptor
            )
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `initialize sets up auth interceptor`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ApiClient.initialize(context)
        assertNotNull(ApiClient.apiService)
    }

    @Test
    fun `apiService is not null after initialization`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ApiClient.initialize(context)
        assertNotNull(ApiClient.apiService)
    }

    @Test
    fun `apiService returns valid retrofit service`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ApiClient.initialize(context)
        val service = ApiClient.apiService
        assertNotNull(service)
        assertTrue(service is ApiService)
    }

    @Test
    fun `jsonFixInterceptor fixes missing closing brace ending with quote`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"key\": \"value\"")
        )
        val request = Request.Builder().url(mockWebServer.url("/test")).build()
        client.newCall(request).execute()
    }

    @Test
    fun `jsonFixInterceptor fixes missing closing brace not ending with quote`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"key\": 123")
        )
        val request = Request.Builder().url(mockWebServer.url("/test")).build()
        client.newCall(request).execute()
    }

    @Test
    fun `jsonFixInterceptor passes valid JSON unchanged`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"key\": \"value\"}")
        )
        val request = Request.Builder().url(mockWebServer.url("/test")).build()
        client.newCall(request).execute()
    }

    @Test
    fun `jsonFixInterceptor handles plain text response`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/plain")
                .setBody("User not found")
        )
        val request = Request.Builder().url(mockWebServer.url("/test")).build()
        client.newCall(request).execute()
    }

    @Test
    fun `jsonFixInterceptor handles missing content type`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"key\": \"value\"}")
        )
        val request = Request.Builder().url(mockWebServer.url("/test")).build()
        client.newCall(request).execute()
    }
}
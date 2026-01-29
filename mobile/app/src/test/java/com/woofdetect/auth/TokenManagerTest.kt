package com.woofdetect.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TokenManagerTest {

    private lateinit var context: Context
    private lateinit var tokenManager: TokenManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        tokenManager = TokenManager(context)
        
        // Wyczyść DataStore przed każdym testem
        runBlocking {
            tokenManager.clearToken()
        }
    }

    @After
    fun tearDown() {
        // Wyczyść po testach
        runBlocking {
            tokenManager.clearToken()
        }
    }

    @Test
    fun `saveToken should save token correctly`() = runBlocking {
        // Given
        val testToken = "test_token_123"
        val testUsername = "testuser"

        // When
        tokenManager.saveToken(testToken, testUsername)

        // Then
        val savedToken = tokenManager.getToken().first()
        val savedUsername = tokenManager.getUsername().first()
        
        assertEquals(testToken, savedToken)
        assertEquals(testUsername, savedUsername)
    }

    @Test
    fun `saveToken without username should save only token`() = runBlocking {
        // Given
        val testToken = "test_token_456"

        // When
        tokenManager.saveToken(testToken)

        // Then
        val savedToken = tokenManager.getToken().first()
        assertEquals(testToken, savedToken)
    }

    @Test
    fun `getToken should return null when no token saved`() = runBlocking {
        // When
        val token = tokenManager.getToken().first()

        // Then
        assertNull(token)
    }

    @Test
    fun `getUsername should return null when no username saved`() = runBlocking {
        // When
        val username = tokenManager.getUsername().first()

        // Then
        assertNull(username)
    }

    @Test
    fun `clearToken should remove token and username`() = runBlocking {
        // Given
        tokenManager.saveToken("token123", "user123")

        // When
        tokenManager.clearToken()

        // Then
        val token = tokenManager.getToken().first()
        val username = tokenManager.getUsername().first()
        
        assertNull(token)
        assertNull(username)
    }

    @Test
    fun `isLoggedIn should return true when token exists`() = runBlocking {
        // Given
        tokenManager.saveToken("valid_token")

        // When
        val isLoggedIn = tokenManager.isLoggedIn()

        // Then
        assertTrue(isLoggedIn)
    }

    @Test
    fun `isLoggedIn should return false when token is null`() = runBlocking {
        // When
        val isLoggedIn = tokenManager.isLoggedIn()

        // Then
        assertFalse(isLoggedIn)
    }

    @Test
    fun `isLoggedIn should return false when token is empty`() = runBlocking {
        // Given
        tokenManager.saveToken("")

        // When
        val isLoggedIn = tokenManager.isLoggedIn()

        // Then
        assertFalse(isLoggedIn)
    }

    @Test
    fun `saveToken should overwrite existing token`() = runBlocking {
        // Given
        tokenManager.saveToken("old_token", "old_user")

        // When
        tokenManager.saveToken("new_token", "new_user")

        // Then
        val token = tokenManager.getToken().first()
        val username = tokenManager.getUsername().first()
        
        assertEquals("new_token", token)
        assertEquals("new_user", username)
    }

    @Test
    fun `saveToken with username should preserve previous token if only username changes`() = runBlocking {
        // Given
        tokenManager.saveToken("token123", "user1")

        // When - save same token with different username
        tokenManager.saveToken("token123", "user2")

        // Then
        val token = tokenManager.getToken().first()
        val username = tokenManager.getUsername().first()
        
        assertEquals("token123", token)
        assertEquals("user2", username)
    }
}

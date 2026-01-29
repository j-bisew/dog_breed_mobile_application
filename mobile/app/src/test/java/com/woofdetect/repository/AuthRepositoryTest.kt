package com.woofdetect.repository

import com.woofdetect.network.ApiClient
import com.woofdetect.network.ApiService
import com.woofdetect.network.dto.LoginRequest
import com.woofdetect.network.dto.LoginResponse
import com.woofdetect.network.dto.RegisterRequest
import com.woofdetect.network.dto.RegisterResponse
import io.mockk.*
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * Testy jednostkowe dla AuthRepository
 * Testują logikę logowania i rejestracji użytkowników
 */
class AuthRepositoryTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var mockApiService: ApiService

    @Before
    fun setup() {
        // Mock ApiService
        mockApiService = mockk()
        
        // Mock ApiClient.apiService
        mockkObject(ApiClient)
        every { ApiClient.apiService } returns mockApiService

        authRepository = AuthRepository()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ========== LOGIN TESTS ==========

    @Test
    fun `login should return success when credentials are valid`() = runBlocking {
        // Given
        val username = "testuser"
        val password = "password123"
        val expectedResponse = LoginResponse(
            message = "Login successful",
            token = "valid_token_123"
        )

        coEvery { 
            mockApiService.login(any()) 
        } returns Response.success(expectedResponse)

        // When
        val result = authRepository.login(username, password)

        // Then
        assertTrue(result.isSuccess)
        assertEquals("Login successful", result.getOrNull()?.message)
        assertEquals("valid_token_123", result.getOrNull()?.token)
        
        // Verify API was called with correct parameters
        coVerify { 
            mockApiService.login(
                match { 
                    it.loginData.username == username && 
                    it.loginData.password == password 
                }
            )
        }
    }

    @Test
    fun `login should return failure when credentials are invalid`() = runBlocking {
        // Given
        val username = "wronguser"
        val password = "wrongpass"

        coEvery { 
            mockApiService.login(any()) 
        } returns Response.error(401, "Unauthorized".toResponseBody())

        // When
        val result = authRepository.login(username, password)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") ?: false)
    }

    @Test
    fun `login should return failure when network error occurs`() = runBlocking {
        // Given
        val username = "testuser"
        val password = "password123"

        coEvery { 
            mockApiService.login(any()) 
        } throws Exception("Network error")

        // When
        val result = authRepository.login(username, password)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `login should return failure when response body is null`() = runBlocking {
        // Given
        val username = "testuser"
        val password = "password123"

        coEvery { 
            mockApiService.login(any()) 
        } returns Response.success(null)

        // When
        val result = authRepository.login(username, password)

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `login should handle server error 500`() = runBlocking {
        // Given
        val username = "testuser"
        val password = "password123"

        coEvery { 
            mockApiService.login(any()) 
        } returns Response.error(500, "Internal Server Error".toResponseBody())

        // When
        val result = authRepository.login(username, password)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") ?: false)
    }

    // ========== REGISTER TESTS ==========

    @Test
    fun `register should return success when data is valid`() = runBlocking {
        // Given
        val name = "Test User"
        val username = "testuser"
        val email = "test@example.com"
        val password = "password123"
        val expectedResponse = RegisterResponse(
            message = "Registration successful",
            token = "new_user_token"
        )

        coEvery { 
            mockApiService.register(any()) 
        } returns Response.success(expectedResponse)

        // When
        val result = authRepository.register(name, username, email, password)

        // Then
        assertTrue(result.isSuccess)
        assertEquals("Registration successful", result.getOrNull()?.message)
        assertEquals("new_user_token", result.getOrNull()?.token)
        
        // Verify API was called with correct parameters
        coVerify { 
            mockApiService.register(
                match { 
                    it.registrationData.name == name &&
                    it.registrationData.username == username &&
                    it.registrationData.email == email &&
                    it.registrationData.password == password
                }
            )
        }
    }

    @Test
    fun `register should return failure when username already exists`() = runBlocking {
        // Given
        val name = "Test User"
        val username = "existinguser"
        val email = "test@example.com"
        val password = "password123"

        coEvery { 
            mockApiService.register(any()) 
        } returns Response.error(409, "Username already exists".toResponseBody())

        // When
        val result = authRepository.register(name, username, email, password)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("409") ?: false)
    }

    @Test
    fun `register should return failure when email is invalid`() = runBlocking {
        // Given
        val name = "Test User"
        val username = "testuser"
        val email = "invalid-email"
        val password = "password123"

        coEvery { 
            mockApiService.register(any()) 
        } returns Response.error(400, "Invalid email".toResponseBody())

        // When
        val result = authRepository.register(name, username, email, password)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("400") ?: false)
    }

    @Test
    fun `register should return failure when network error occurs`() = runBlocking {
        // Given
        val name = "Test User"
        val username = "testuser"
        val email = "test@example.com"
        val password = "password123"

        coEvery { 
            mockApiService.register(any()) 
        } throws Exception("Connection timeout")

        // When
        val result = authRepository.register(name, username, email, password)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Connection timeout", result.exceptionOrNull()?.message)
    }

    @Test
    fun `register should return failure when response body is null`() = runBlocking {
        // Given
        val name = "Test User"
        val username = "testuser"
        val email = "test@example.com"
        val password = "password123"

        coEvery { 
            mockApiService.register(any()) 
        } returns Response.success(null)

        // When
        val result = authRepository.register(name, username, email, password)

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `register should handle empty password`() = runBlocking {
        // Given
        val name = "Test User"
        val username = "testuser"
        val email = "test@example.com"
        val password = ""

        coEvery { 
            mockApiService.register(any()) 
        } returns Response.error(400, "Password required".toResponseBody())

        // When
        val result = authRepository.register(name, username, email, password)

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `register should handle special characters in username`() = runBlocking {
        // Given
        val name = "Test User"
        val username = "test@user#123"
        val email = "test@example.com"
        val password = "password123"
        val expectedResponse = RegisterResponse(
            message = "Registration successful",
            token = "token_with_special_chars"
        )

        coEvery { 
            mockApiService.register(any()) 
        } returns Response.success(expectedResponse)

        // When
        val result = authRepository.register(name, username, email, password)

        // Then
        assertTrue(result.isSuccess)
        
        coVerify { 
            mockApiService.register(
                match { it.registrationData.username == username }
            )
        }
    }
}

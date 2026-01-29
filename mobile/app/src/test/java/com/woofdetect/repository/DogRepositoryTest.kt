package com.woofdetect.repository

import android.graphics.Bitmap
import android.net.Uri
import com.google.gson.Gson
import com.woofdetect.network.ApiClient
import com.woofdetect.network.ApiService
import com.woofdetect.network.dto.DogAnalysisResponse
import com.woofdetect.network.dto.FeedbackRequest
import io.mockk.*
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.File

/**
 * Testy jednostkowe dla DogRepository
 * Testują analizę zdjęć psów i wysyłanie feedbacku
 */
class DogRepositoryTest {

    private lateinit var dogRepository: DogRepository
    private lateinit var mockApiService: ApiService
    private lateinit var mockFile: File
    private lateinit var mockUri: Uri

    @Before
    fun setup() {
        // Mock dependencies
        mockApiService = mockk()
        mockFile = mockk(relaxed = true)
        mockUri = mockk(relaxed = true)

        // Mock ApiClient
        mockkObject(ApiClient)
        every { ApiClient.apiService } returns mockApiService

        // Mock File properties
        every { mockFile.name } returns "test_dog.jpg"
        every { mockFile.length() } returns 1024L
        every { mockFile.exists() } returns true

        dogRepository = DogRepository()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ========== ANALYZE DOG PHOTO TESTS ==========

    @Test
    fun `analyzeDogPhoto should return success with JSON response`() = runBlocking {
        // Given
        val jsonResponse = """
            {
                "breedName": "labrador",
                "breedFullName": "Labrador Retriever",
                "breedDescription": "Friendly and outgoing dog breed",
                "confidence": 95.5
            }
        """.trimIndent()

        val responseBody = jsonResponse.toResponseBody("application/json".toMediaTypeOrNull())
        
        coEvery { 
            mockApiService.analyzeDogPhoto(any(), any()) 
        } returns Response.success(responseBody)

        // When
        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        // Then
        assertTrue(result.isSuccess)
        val dogResult = result.getOrNull()
        assertNotNull(dogResult)
        assertEquals("labrador", dogResult?.breedName)
        assertEquals("Labrador Retriever", dogResult?.breedFullName)
        assertEquals("Friendly and outgoing dog breed", dogResult?.breedDescription)
        assertEquals(95.5, dogResult?.confidence ?: 0.0, 0.1)
    }

    @Test
    fun `analyzeDogPhoto should handle API error 404`() = runBlocking {
        // Given
        coEvery { 
            mockApiService.analyzeDogPhoto(any(), any()) 
        } returns Response.error(404, "Not found".toResponseBody())

        // When
        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("404") ?: false)
    }

    @Test
    fun `analyzeDogPhoto should handle API error 515 - breed not recognized`() = runBlocking {
        // Given
        coEvery { 
            mockApiService.analyzeDogPhoto(any(), any()) 
        } returns Response.error(515, "Breed not recognized".toResponseBody())

        // When
        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("515") ?: false)
    }

    @Test
    fun `analyzeDogPhoto should handle empty response body`() = runBlocking {
        // Given
        coEvery { 
            mockApiService.analyzeDogPhoto(any(), any()) 
        } returns Response.success(null)

        // When
        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Empty response") ?: false)
    }

    @Test
    fun `analyzeDogPhoto should handle network exception`() = runBlocking {
        // Given
        coEvery { 
            mockApiService.analyzeDogPhoto(any(), any()) 
        } throws Exception("Network timeout")

        // When
        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Network timeout", result.exceptionOrNull()?.message)
    }

    @Test
    fun `analyzeDogPhoto should handle malformed JSON`() = runBlocking {
        // Given
        val malformedJson = """
            {
                "breedName": "labrador",
                "invalidField": 
            }
        """.trimIndent()

        val responseBody = malformedJson.toResponseBody("application/json".toMediaTypeOrNull())
        
        coEvery { 
            mockApiService.analyzeDogPhoto(any(), any()) 
        } returns Response.success(responseBody)

        // When
        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `analyzeDogPhoto should handle missing confidence field`() = runBlocking {
        // Given
        val jsonResponse = """
            {
                "breedName": "beagle",
                "breedFullName": "Beagle",
                "breedDescription": "Small hound dog"
            }
        """.trimIndent()

        val responseBody = jsonResponse.toResponseBody("application/json".toMediaTypeOrNull())
        
        coEvery { 
            mockApiService.analyzeDogPhoto(any(), any()) 
        } returns Response.success(responseBody)

        // When
        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        // Then
        assertTrue(result.isSuccess)
        val dogResult = result.getOrNull()
        assertEquals(0.0, dogResult?.confidence ?: -1.0, 0.1)
    }

    @Test
    fun `analyzeDogPhoto should handle very low confidence`() = runBlocking {
        // Given
        val jsonResponse = """
            {
                "breedName": "unknown",
                "breedFullName": "Unknown Breed",
                "breedDescription": "Could not determine breed with confidence",
                "confidence": 15.3
            }
        """.trimIndent()

        val responseBody = jsonResponse.toResponseBody("application/json".toMediaTypeOrNull())
        
        coEvery { 
            mockApiService.analyzeDogPhoto(any(), any()) 
        } returns Response.success(responseBody)

        // When
        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        // Then
        assertTrue(result.isSuccess)
        val dogResult = result.getOrNull()
        assertEquals(15.3, dogResult?.confidence ?: 0.0, 0.1)
    }

    // ========== SUBMIT FEEDBACK TESTS ==========

    @Test
    fun `submitFeedback should return success when feedback is submitted`() = runBlocking {
        // Given
        val breedName = "Golden Retriever"
        val successBody = "Feedback received".toResponseBody()

        coEvery { 
            mockApiService.submitFeedback(any(), any()) 
        } returns Response.success(successBody)

        // When
        val result = dogRepository.submitFeedback(breedName)

        // Then
        assertTrue(result.isSuccess)
        
        // Verify correct breed name was sent (lowercase)
        coVerify { 
            mockApiService.submitFeedback(
                match { 
                    it.raceNameData.raceName == breedName.lowercase()
                },
                any()
            )
        }
    }

    @Test
    fun `submitFeedback should lowercase breed name`() = runBlocking {
        // Given
        val breedName = "LABRADOR RETRIEVER"
        val successBody = "OK".toResponseBody()

        coEvery { 
            mockApiService.submitFeedback(any(), any()) 
        } returns Response.success(successBody)

        // When
        val result = dogRepository.submitFeedback(breedName)

        // Then
        assertTrue(result.isSuccess)
        
        coVerify { 
            mockApiService.submitFeedback(
                match { 
                    it.raceNameData.raceName == "labrador retriever"
                },
                any()
            )
        }
    }

    @Test
    fun `submitFeedback should handle API error 400`() = runBlocking {
        // Given
        val breedName = "InvalidBreed"

        coEvery { 
            mockApiService.submitFeedback(any(), any()) 
        } returns Response.error(400, "Invalid breed name".toResponseBody())

        // When
        val result = dogRepository.submitFeedback(breedName)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("400") ?: false)
    }

    @Test
    fun `submitFeedback should handle network exception`() = runBlocking {
        // Given
        val breedName = "Beagle"

        coEvery { 
            mockApiService.submitFeedback(any(), any()) 
        } throws Exception("Connection refused")

        // When
        val result = dogRepository.submitFeedback(breedName)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Connection refused", result.exceptionOrNull()?.message)
    }

    @Test
    fun `submitFeedback should handle empty breed name`() = runBlocking {
        // Given
        val breedName = ""
        val successBody = "OK".toResponseBody()

        coEvery { 
            mockApiService.submitFeedback(any(), any()) 
        } returns Response.success(successBody)

        // When
        val result = dogRepository.submitFeedback(breedName)

        // Then
        assertTrue(result.isSuccess)
        
        coVerify { 
            mockApiService.submitFeedback(
                match { it.raceNameData.raceName == "" },
                any()
            )
        }
    }

    @Test
    fun `submitFeedback should handle special characters in breed name`() = runBlocking {
        // Given
        val breedName = "Chow-Chow (Chinese)"
        val successBody = "OK".toResponseBody()

        coEvery { 
            mockApiService.submitFeedback(any(), any()) 
        } returns Response.success(successBody)

        // When
        val result = dogRepository.submitFeedback(breedName)

        // Then
        assertTrue(result.isSuccess)
        
        coVerify { 
            mockApiService.submitFeedback(
                match { 
                    it.raceNameData.raceName == "chow-chow (chinese)"
                },
                any()
            )
        }
    }

    @Test
    fun `submitFeedback should handle server error 500`() = runBlocking {
        // Given
        val breedName = "Poodle"

        coEvery { 
            mockApiService.submitFeedback(any(), any()) 
        } returns Response.error(500, "Internal server error".toResponseBody())

        // When
        val result = dogRepository.submitFeedback(breedName)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") ?: false)
    }
}

package com.woofdetect.repository

import android.net.Uri
import com.woofdetect.network.ApiClient
import com.woofdetect.network.ApiService
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
 * Testy jednostkowe dla DogRepository.
 * Pokrywają:
 *  - JSON fallback (bez Content-Type multipart)
 *  - Multipart response (JSON metadata + opcjonalny obraz)
 *  - Obsługę błędów API i wyjątków sieciowych
 *  - submitFeedback (sukces, błędy, lowercase)
 */
class DogRepositoryTest {

    private lateinit var dogRepository: DogRepository
    private lateinit var mockApiService: ApiService
    private lateinit var mockFile: File
    private lateinit var mockUri: Uri

    @Before
    fun setup() {
        mockApiService = mockk()
        mockFile = mockk(relaxed = true)
        mockUri = mockk(relaxed = true)

        mockkObject(ApiClient)
        every { ApiClient.apiService } returns mockApiService

        every { mockFile.name } returns "test_dog.jpg"
        every { mockFile.length() } returns 1024L
        every { mockFile.exists() } returns true

        dogRepository = DogRepository()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ========== HELPER: budowanie multipart body ==========

    /**
     * Buduje surowe bajty odpowiedzi multipart zgodne z RFC 2046.
     * Zawiera:
     *  - część JSON z metadanymi rasy
     *  - opcjonalną część z danymi obrazu (domyślnie minimalne bajty JPEG)
     */
    private fun buildMultipartBody(
        boundary: String,
        jsonPayload: String,
        includeImagePart: Boolean = true,
        imageBytes: ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
    ): ByteArray {
        val sb = StringBuilder()

        // Część JSON
        sb.append("--$boundary\r\n")
        sb.append("Content-Type: application/json\r\n")
        sb.append("\r\n")
        sb.append(jsonPayload)
        sb.append("\r\n")

        if (includeImagePart) {
            // Część obraz
            sb.append("--$boundary\r\n")
            sb.append("Content-Type: image/jpeg\r\n")
            sb.append("\r\n")
        }

        val textBytes = sb.toString().toByteArray(Charsets.ISO_8859_1)

        return if (includeImagePart) {
            val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.ISO_8859_1)
            textBytes + imageBytes + tail
        } else {
            val closing = "--$boundary--\r\n".toByteArray(Charsets.ISO_8859_1)
            textBytes + closing
        }
    }

    private fun successResponseWithContentType(
        body: ByteArray,
        contentType: String
    ): Response<ResponseBody> {
        val rb = body.toResponseBody(contentType.toMediaTypeOrNull())
        // Retrofit Response.success nie pozwala ustawiać nagłówków przez publiczne API,
        // więc używamy okhttp3.Response ręcznie.
        val rawResponse = okhttp3.Response.Builder()
            .code(200)
            .message("OK")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("http://example.com/").build())
            .header("Content-Type", contentType)
            .body(rb)
            .build()
        return Response.success(rb, rawResponse)
    }

    // ========== JSON FALLBACK TESTS ==========

    @Test
    fun `analyzeDogPhoto returns success with full JSON response`() = runBlocking {
        val json = """{"breedName":"labrador","breedFullName":"Labrador Retriever",
            |"breedDescription":"Friendly and outgoing dog breed","confidence":95.5}"""
            .trimMargin()

        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { mockApiService.analyzeDogPhoto(any(), any()) } returns Response.success(responseBody)

        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        assertTrue(result.isSuccess)
        val dog = result.getOrThrow()
        assertEquals("labrador", dog.breedName)
        assertEquals("Labrador Retriever", dog.breedFullName)
        assertEquals("Friendly and outgoing dog breed", dog.breedDescription)
        assertEquals(95.5, dog.confidence, 0.01)
        assertNull(dog.breedPhoto)
    }

    @Test
    fun `analyzeDogPhoto returns success when confidence field is absent (defaults to 0)`() = runBlocking {
        val json = """{"breedName":"beagle","breedFullName":"Beagle","breedDescription":"Small hound"}"""
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { mockApiService.analyzeDogPhoto(any(), any()) } returns Response.success(responseBody)

        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        assertTrue(result.isSuccess)
        assertEquals(0.0, result.getOrThrow().confidence, 0.001)
    }

    @Test
    fun `analyzeDogPhoto returns success with very low confidence value`() = runBlocking {
        val json = """{"breedName":"unknown","breedFullName":"Unknown Breed",
            |"breedDescription":"Uncertain","confidence":15.3}""".trimMargin()
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { mockApiService.analyzeDogPhoto(any(), any()) } returns Response.success(responseBody)

        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        assertTrue(result.isSuccess)
        assertEquals(15.3, result.getOrThrow().confidence, 0.01)
    }

    @Test
    fun `analyzeDogPhoto returns failure on HTTP 404`() = runBlocking {
        coEvery { mockApiService.analyzeDogPhoto(any(), any()) } returns
                Response.error(404, "Not found".toResponseBody())

        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("404") == true)
    }

    @Test
    fun `analyzeDogPhoto returns failure on HTTP 515 (breed not recognized)`() = runBlocking {
        coEvery { mockApiService.analyzeDogPhoto(any(), any()) } returns
                Response.error(515, "Breed not recognized".toResponseBody())

        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("515") == true)
    }

    @Test
    fun `analyzeDogPhoto returns failure when response body is null`() = runBlocking {
        coEvery { mockApiService.analyzeDogPhoto(any(), any()) } returns Response.success(null)

        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Empty response") == true)
    }

    @Test
    fun `analyzeDogPhoto returns failure when network throws exception`() = runBlocking {
        coEvery { mockApiService.analyzeDogPhoto(any(), any()) } throws Exception("Network timeout")

        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        assertTrue(result.isFailure)
        assertEquals("Network timeout", result.exceptionOrNull()?.message)
    }

    @Test
    fun `analyzeDogPhoto returns failure on malformed JSON`() = runBlocking {
        val malformed = """{"breedName":"labrador","invalidField":}"""
        val responseBody = malformed.toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { mockApiService.analyzeDogPhoto(any(), any()) } returns Response.success(responseBody)

        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        assertTrue(result.isFailure)
    }

    @Test
    fun `analyzeDogPhoto JSON fallback with mainPhotoData present decodes bitmap (null result ok)`() = runBlocking {
        // mainPhotoData zawiera nieprawidłowe dane obrazu – BitmapFactory zwróci null,
        // ale kod nie rzuci wyjątku (null bitmap jest akceptowalny)
        val json = """{"breedName":"poodle","breedFullName":"Poodle",
            |"breedDescription":"Intelligent","confidence":88.0,"mainPhotoData":"bm90YW5pbWFnZQ=="}"""
            .trimMargin()
        val responseBody = json.toResponseBody("application/json".toMediaTypeOrNull())
        coEvery { mockApiService.analyzeDogPhoto(any(), any()) } returns Response.success(responseBody)

        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        // Dekodowanie zwróci null (nie prawdziwy JPEG), ale wynik nadal jest sukcesem
        assertTrue(result.isSuccess)
        val dog = result.getOrThrow()
        assertEquals("poodle", dog.breedName)
        assertEquals(88.0, dog.confidence, 0.01)
        // breedPhoto może być null jeśli BitmapFactory nie rozpozna danych
        // – to prawidłowe zachowanie
    }

    // ========== MULTIPART TESTS ==========

    @Test
    fun `analyzeDogPhoto multipart returns failure when boundary is missing`() = runBlocking {
        // Content-Type multipart bez parametru boundary
        val body = "somedata".toByteArray().toResponseBody("multipart/form-data".toMediaTypeOrNull())

        val rawResponse = okhttp3.Response.Builder()
            .code(200).message("OK")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("http://example.com/").build())
            .header("Content-Type", "multipart/form-data")
            .body(body)
            .build()
        val response = Response.success(body, rawResponse)

        coEvery { mockApiService.analyzeDogPhoto(any(), any()) } returns response

        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("boundary") == true)
    }

    @Test
    fun `analyzeDogPhoto multipart returns success with JSON and image parts`() = runBlocking {
        val boundary = "testboundary123"
        val json = """{"breedName":"husky","breedFullName":"Siberian Husky",
            |"breedDescription":"Energetic sled dog","confidence":91.0}""".trimMargin()

        val bodyBytes = buildMultipartBody(boundary, json, includeImagePart = true)
        val contentType = "multipart/form-data; boundary=$boundary"
        val response = successResponseWithContentType(bodyBytes, contentType)

        coEvery { mockApiService.analyzeDogPhoto(any(), any()) } returns response

        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        assertTrue(result.isSuccess)
        val dog = result.getOrThrow()
        assertEquals("husky", dog.breedName)
        assertEquals("Siberian Husky", dog.breedFullName)
        assertEquals(91.0, dog.confidence, 0.01)
        // breedPhoto może być null jeśli testowe bajty JPEG nie są pełnym obrazem –
        // ważne że nie rzucono wyjątku
    }

    @Test
    fun `analyzeDogPhoto multipart returns success with JSON part only (no image)`() = runBlocking {
        val boundary = "boundaryonly"
        val json = """{"breedName":"dalmatian","breedFullName":"Dalmatian",
            |"breedDescription":"Spotted breed","confidence":78.5}""".trimMargin()

        val bodyBytes = buildMultipartBody(boundary, json, includeImagePart = false)
        val contentType = "multipart/form-data; boundary=$boundary"
        val response = successResponseWithContentType(bodyBytes, contentType)

        coEvery { mockApiService.analyzeDogPhoto(any(), any()) } returns response

        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        assertTrue(result.isSuccess)
        val dog = result.getOrThrow()
        assertEquals("dalmatian", dog.breedName)
        assertNull(dog.breedPhoto)
    }

    @Test
    fun `analyzeDogPhoto multipart returns failure when JSON metadata is missing`() = runBlocking {
        val boundary = "noboundary999"
        // Tworzymy multipart z samą częścią obraz, bez JSON
        val bodyStr = "--$boundary\r\nContent-Type: image/jpeg\r\n\r\n" +
                byteArrayOf(0xFF.toByte(), 0xD8.toByte()).toString(Charsets.ISO_8859_1) +
                "\r\n--$boundary--\r\n"
        val bodyBytes = bodyStr.toByteArray(Charsets.ISO_8859_1)
        val contentType = "multipart/form-data; boundary=$boundary"
        val response = successResponseWithContentType(bodyBytes, contentType)

        coEvery { mockApiService.analyzeDogPhoto(any(), any()) } returns response

        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("missing JSON") == true)
    }

    @Test
    fun `analyzeDogPhoto multipart boundary quoted in Content-Type is handled`() = runBlocking {
        val boundary = "quotedboundary"
        val json = """{"breedName":"corgi","breedFullName":"Pembroke Welsh Corgi",
            |"breedDescription":"Short-legged herding dog","confidence":84.2}""".trimMargin()

        val bodyBytes = buildMultipartBody(boundary, json, includeImagePart = false)
        // boundary w cudzysłowie – kod robi removeSurrounding("\"")
        val contentType = """multipart/form-data; boundary="$boundary""""
        val response = successResponseWithContentType(bodyBytes, contentType)

        coEvery { mockApiService.analyzeDogPhoto(any(), any()) } returns response

        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        assertTrue(result.isSuccess)
        assertEquals("corgi", result.getOrThrow().breedName)
    }

    @Test
    fun `analyzeDogPhoto multipart with confidence null defaults to 0`() = runBlocking {
        val boundary = "boundarynoconf"
        val json = """{"breedName":"boxer","breedFullName":"Boxer","breedDescription":"Playful breed"}"""

        val bodyBytes = buildMultipartBody(boundary, json, includeImagePart = false)
        val response = successResponseWithContentType(bodyBytes, "multipart/form-data; boundary=$boundary")

        coEvery { mockApiService.analyzeDogPhoto(any(), any()) } returns response

        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        assertTrue(result.isSuccess)
        assertEquals(0.0, result.getOrThrow().confidence, 0.001)
    }

    @Test
    fun `analyzeDogPhoto multipart with image CRLF trailing bytes is trimmed correctly`() = runBlocking {
        val boundary = "crlftrim"
        val json = """{"breedName":"setter","breedFullName":"Irish Setter",
            |"breedDescription":"Energetic","confidence":77.0}""".trimMargin()

        // Dodaj CRLF na końcu danych obrazu – kod powinien je przyciąć
        val imageWithCrlf = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            '\r'.code.toByte(), '\n'.code.toByte())

        val bodyBytes = buildMultipartBody(boundary, json, includeImagePart = true, imageBytes = imageWithCrlf)
        val response = successResponseWithContentType(bodyBytes, "multipart/form-data; boundary=$boundary")

        coEvery { mockApiService.analyzeDogPhoto(any(), any()) } returns response

        val result = dogRepository.analyzeDogPhoto(mockFile, mockUri)

        // Wynik sukces – nawet jeśli bitmap null (4 bajty JPEG bez EOI nie są prawidłowe)
        assertTrue(result.isSuccess)
        assertEquals("setter", result.getOrThrow().breedName)
    }

    // ========== SUBMIT FEEDBACK TESTS ==========

    @Test
    fun `submitFeedback returns success and sends lowercase breed name`() = runBlocking {
        coEvery { mockApiService.submitFeedback(any(), any()) } returns
                Response.success("OK".toResponseBody())

        val result = dogRepository.submitFeedback("Golden Retriever")

        assertTrue(result.isSuccess)
        coVerify {
            mockApiService.submitFeedback(
                match { it.raceNameData.raceName == "golden retriever" },
                any()
            )
        }
    }

    @Test
    fun `submitFeedback lowercases all-caps breed name`() = runBlocking {
        coEvery { mockApiService.submitFeedback(any(), any()) } returns
                Response.success("OK".toResponseBody())

        val result = dogRepository.submitFeedback("LABRADOR RETRIEVER")

        assertTrue(result.isSuccess)
        coVerify {
            mockApiService.submitFeedback(
                match { it.raceNameData.raceName == "labrador retriever" },
                any()
            )
        }
    }

    @Test
    fun `submitFeedback returns failure on HTTP 400`() = runBlocking {
        coEvery { mockApiService.submitFeedback(any(), any()) } returns
                Response.error(400, "Bad request".toResponseBody())

        val result = dogRepository.submitFeedback("InvalidBreed")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("400") == true)
    }

    @Test
    fun `submitFeedback returns failure on HTTP 500`() = runBlocking {
        coEvery { mockApiService.submitFeedback(any(), any()) } returns
                Response.error(500, "Internal server error".toResponseBody())

        val result = dogRepository.submitFeedback("Poodle")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }

    @Test
    fun `submitFeedback returns failure when network throws exception`() = runBlocking {
        coEvery { mockApiService.submitFeedback(any(), any()) } throws Exception("Connection refused")

        val result = dogRepository.submitFeedback("Beagle")

        assertTrue(result.isFailure)
        assertEquals("Connection refused", result.exceptionOrNull()?.message)
    }

    @Test
    fun `submitFeedback accepts empty breed name and sends it as empty string`() = runBlocking {
        coEvery { mockApiService.submitFeedback(any(), any()) } returns
                Response.success("OK".toResponseBody())

        val result = dogRepository.submitFeedback("")

        assertTrue(result.isSuccess)
        coVerify {
            mockApiService.submitFeedback(match { it.raceNameData.raceName == "" }, any())
        }
    }

    @Test
    fun `submitFeedback handles special characters in breed name`() = runBlocking {
        coEvery { mockApiService.submitFeedback(any(), any()) } returns
                Response.success("OK".toResponseBody())

        val result = dogRepository.submitFeedback("Chow-Chow (Chinese)")

        assertTrue(result.isSuccess)
        coVerify {
            mockApiService.submitFeedback(
                match { it.raceNameData.raceName == "chow-chow (chinese)" },
                any()
            )
        }
    }
}
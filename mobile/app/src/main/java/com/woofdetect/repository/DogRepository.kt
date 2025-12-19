package com.woofdetect.repository

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.woofdetect.model.DogResult
import com.woofdetect.network.ApiClient
import okhttp3.ResponseBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

// Helpers for working with raw multipart bytes
private fun ByteArray.indexOf(sub: ByteArray, fromIndex: Int = 0): Int {
    if (sub.isEmpty()) return -1
    outer@ for (i in fromIndex..(this.size - sub.size)) {
        for (j in sub.indices) {
            if (this[i + j] != sub[j]) continue@outer
        }
        return i
    }
    return -1
}

private fun ByteArray.split(separator: ByteArray): List<ByteArray> {
    val list = mutableListOf<ByteArray>()
    var start = 0
    var idx = this.indexOf(separator, start)
    while (idx >= 0) {
        list.add(this.copyOfRange(start, idx))
        start = idx + separator.size
        idx = this.indexOf(separator, start)
    }
    if (start <= this.size) {
        list.add(this.copyOfRange(start, this.size))
    }
    return list
}

class DogRepository {

    companion object {
        private const val TAG = "DogRepository"
    }

    suspend fun analyzeDogPhoto(
        photoFile: File,
        photoUri: Uri,
    ): Result<DogResult> = withContext(Dispatchers.IO) {
        try {
            val requestBody = photoFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val photoPart = MultipartBody.Part.createFormData("photo", photoFile.name, requestBody)

            Log.d(TAG, "Sending photo to API: ${photoFile.name}, size: ${photoFile.length()} bytes")


            val response = ApiClient.apiService.analyzeDogPhoto(photo = photoPart)

            Log.d(TAG, "API response code: ${response.code()}")

            if (!response.isSuccessful) {
                val errorMsg = "API Error: ${response.code()} - ${response.message()}"
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val contentTypeHeader = response.headers().get("Content-Type") ?: ""
            Log.d(TAG, "Response Content-Type: $contentTypeHeader")
            val body: ResponseBody? = response.body()
            if (body == null) {
                val errorMsg = "Empty response body"
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(Exception(errorMsg))
            }

            try {
                // If server returned multipart (binary image + JSON metadata)
                if (contentTypeHeader.contains("multipart/", ignoreCase = true)) {
                    val boundary = contentTypeHeader.split(";")
                        .find { it.trim().startsWith("boundary=", ignoreCase = true) }
                        ?.substringAfter("boundary=")
                        ?.trim()
                        ?.removeSurrounding("\"")

                    if (boundary.isNullOrEmpty()) {
                        val errorMsg = "Multipart response missing boundary in Content-Type: $contentTypeHeader"
                        Log.e(TAG, errorMsg)
                        return@withContext Result.failure(Exception(errorMsg))
                    }

                    Log.d(TAG, "Using boundary: $boundary")
                    val bytes = body.bytes()
                    Log.d(TAG, "Total bytes received: ${bytes.size}")
                    val boundaryBytes = ("--$boundary").toByteArray(Charsets.ISO_8859_1)

                    // Split the body into parts using the boundary
                    val parts = bytes.split(boundaryBytes)
                    Log.d(TAG, "Found ${parts.size} parts in multipart response")

                    var metadataJson: String? = null
                    var imageBytes: ByteArray? = null

                    for (part in parts) {
                        if (part.isEmpty()) continue
                        
                        val partStr = String(part, Charsets.ISO_8859_1)
                        
                        if (partStr.contains("Content-Type: application/json", ignoreCase = true)) {
                            val idx = partStr.indexOf("\r\n\r\n")
                            if (idx != -1) {
                                metadataJson = partStr.substring(idx + 4).trim()
                                Log.d(TAG, "Found JSON metadata in multipart: $metadataJson")
                            }
                        } else if (partStr.contains("Content-Type: image/", ignoreCase = true)) {
                            val idx = partStr.indexOf("\r\n\r\n")
                            if (idx != -1) {
                                var imgStart = idx + 4
                                var imgEnd = part.size
                                
                                // Remove trailing CRLF if present
                                if (imgEnd - 2 >= imgStart && part[imgEnd - 2] == '\r'.code.toByte() && part[imgEnd - 1] == '\n'.code.toByte()) {
                                    imgEnd -= 2
                                }
                                
                                // Skip any leading garbage (like stray CRLFs) until JPEG SOI marker (FF D8)
                                while (imgStart < imgEnd - 1 && (part[imgStart] != 0xFF.toByte() || part[imgStart + 1] != 0xD8.toByte())) {
                                    imgStart++
                                }

                                imageBytes = part.copyOfRange(imgStart, imgEnd)
                                Log.d(TAG, "Extracted image data, size: ${imageBytes.size} bytes")
                            }
                        }
                    }

                    if (metadataJson == null) {
                        val errorMsg = "Multipart response missing JSON metadata"
                        Log.e(TAG, errorMsg)
                        return@withContext Result.failure(Exception(errorMsg))
                    }

                    val gson = Gson()
                    val dto = gson.fromJson(metadataJson, com.woofdetect.network.dto.DogAnalysisResponse::class.java)

                    val breedBitmap = imageBytes?.let { bytesArr ->
                        try {
                            BitmapFactory.decodeByteArray(bytesArr, 0, bytesArr.size)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error decoding breed photo from multipart", e)
                            null
                        }
                    }

                    val dogResult = DogResult(
                        breedName = dto.breedName,
                        breedFullName = dto.breedFullName,
                        breedDescription = dto.breedDescription,
                        confidence = dto.confidence ?: 0.0,
                        breedPhoto = breedBitmap
                    )

                    Result.success(dogResult)

                } else {
                    // Fallback: JSON body
                    val jsonString = body.string()
                    Log.d(TAG, "Fallback to JSON parsing. Body starts with: ${jsonString.take(100)}")
                    val gson = Gson()
                    val dto = gson.fromJson(jsonString, com.woofdetect.network.dto.DogAnalysisResponse::class.java)

                    val breedBitmap = dto.mainPhotoData?.let { photoData ->
                        try {
                            val bytes = photoData.toByteArray(Charsets.ISO_8859_1)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error decoding breed photo", e)
                            null
                        }
                    }

                    val dogResult = DogResult(
                        breedName = dto.breedName,
                        breedFullName = dto.breedFullName,
                        breedDescription = dto.breedDescription,
                        confidence = dto.confidence ?: 0.0,
                        breedPhoto = breedBitmap
                    )

                    Result.success(dogResult)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing analyzeDogPhoto response", e)
                Result.failure(e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing dog photo", e)
            Result.failure(e)
        }
    }

    suspend fun submitFeedback(breedName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val requestBody = mapOf(
                "raceNameData" to mapOf(
                    "raceName" to breedName.lowercase()
                )
            )

            Log.d(TAG, "Submitting feedback for breed: $breedName")

            val response = ApiClient.apiService.submitFeedback(body = requestBody)

            if (response.isSuccessful) {
                Log.d(TAG, "Feedback submitted successfully")
                Result.success(Unit)
            } else {
                val errorMsg = "Feedback Error: ${response.code()} - ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception during feedback submission", e)
            Result.failure(e)
        }
    }
}
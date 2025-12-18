package com.woofdetect.repository

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.woofdetect.model.DogResult
import com.woofdetect.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

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

            val dto = response.body()
            if (response.isSuccessful && dto != null) {
                Log.d(TAG, "API response: ${dto.breedName}, confidence: ${dto.confidence}")

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
            } else {
                val errorMsg = "API Error: ${response.code()} - ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
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
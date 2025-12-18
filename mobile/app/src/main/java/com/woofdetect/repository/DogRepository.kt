package com.woofdetect.repository

import android.net.Uri
import android.util.Log
import com.woofdetect.model.DogResult
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


            val response =
        }
    }
}
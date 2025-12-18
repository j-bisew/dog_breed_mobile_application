package com.woofdetect.network

import com.woofdetect.network.dto.DogAnalysisResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/*
    API endpoints
    Retrofit implements
 */

interface ApiService {

    // POST /getDogBreedInfo
    @Multipart
    @POST("getDogBreedInfo")
    suspend fun analyzeDogPhoto(
        @Part photo: MultipartBody.Part,
        @Header("Authorization") authorization: String? = null
    ): Response<DogAnalysisResponse>

    // POST /submitDogBreedFeedback
    @POST
    @Headers("Content-Type: application/json")
    suspend fun submitFeedback(
        @Body body: Map<String, Map<String, String>>,
        @Header("Authorization") authorization: String? = null
    ): Response<String>

}
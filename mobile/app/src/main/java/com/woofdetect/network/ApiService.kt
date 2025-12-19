package com.woofdetect.network

import com.woofdetect.network.dto.DogAnalysisResponse
import okhttp3.ResponseBody
import com.woofdetect.network.dto.LoginRequest
import com.woofdetect.network.dto.LoginResponse
import com.woofdetect.network.dto.RegisterRequest
import com.woofdetect.network.dto.RegisterResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/*
    API endpoints
    Retrofit implements
 */

interface ApiService {

    // ========== AUTH ENDPOINTS ==========
    @POST("loginUser")
    @Headers("Content-Type: application/json")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("registerUser")
    @Headers("Content-Type: application/json")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<RegisterResponse>


    // ========== DOG ANALYSIS ENDPOINTS ==========
    @Multipart
    @POST("getDogBreedInfo")
    suspend fun analyzeDogPhoto(
        @Part photo: MultipartBody.Part,
        @Header("Authorization") authorization: String? = null
    ): Response<ResponseBody>

    @POST
    @Headers("Content-Type: application/json")
    suspend fun submitFeedback(
        @Body body: Map<String, Map<String, String>>,
        @Header("Authorization") authorization: String? = null
    ): Response<String>

}
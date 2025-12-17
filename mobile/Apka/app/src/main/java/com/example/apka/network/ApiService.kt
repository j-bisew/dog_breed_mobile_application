package com.example.apka.network

import com.google.gson.JsonObject
import okhttp3.ResponseBody
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @POST("/loginUser")
    suspend fun login(@Body body: JsonObject): Response<ResponseBody>

    @Multipart
    @POST("/getDogBreedInfo")
    suspend fun getDogBreedInfo(@Header("Authorization") auth: String, @Part photo: MultipartBody.Part): Response<okhttp3.ResponseBody>

    @POST("/submitDogBreedFeedback")
    suspend fun submitFeedback(@Header("Authorization") auth: String, @Body body: JsonObject): Response<JsonObject>
}

package com.woofdetect.network

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "http://192.168.0.17:8000/"

    private val gson: Gson = GsonBuilder()
        .setLenient()
        .create()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val jsonFixInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val body = response.body
        
        // Check if we should inspect this body
        // 1. It is explicitly JSON
        // 2. OR Content-Type is missing (common with the custom python backend errors)
        val contentType = body?.contentType()
        val isJson = contentType?.subtype?.contains("json") == true
        val isMissingType = contentType == null

        if (body != null && (isJson || isMissingType)) {
            try {
                // Buffer the entire body to inspect it. 
                // NOTE: This assumes API responses are reasonably small text.
                val content = body.string()
                val trimmed = content.trim()

                // Heuristic: If it looks like a JSON object (starts with {)
                if (trimmed.startsWith("{")) {
                     val newContent = if (!trimmed.endsWith("}")) {
                        android.util.Log.e("ApiClient", "JSON FIX APPLIED: Missing closing brace. Original: '$content'")
                        if (content.endsWith("\"")) {
                            content + "}"
                        } else {
                            // If it cut off mid-string or number, appending } might make invalid json like {"key": val}
                            // But usually it cuts off at the very end.
                            content + "}"
                        }
                    } else {
                        content
                    }
                    
                    val mediaType = contentType ?: "application/json".toMediaTypeOrNull()
                    val newBody = ResponseBody.create(mediaType, newContent)
                    return@Interceptor response.newBuilder().body(newBody).build()
                } else {
                    // Not a JSON object (e.g. plain error text "User not found"), restore body
                    val newBody = ResponseBody.create(contentType, content)
                    return@Interceptor response.newBuilder().body(newBody).build()
                }
            } catch (e: Exception) {
                android.util.Log.e("ApiClient", "Error in jsonFixInterceptor", e)
                // If we consumed the body and crashed, the downstream request will fail. Used mostly for debugging.
            }
        }
        response
    }

    private var authInterceptor: AuthInterceptor? = null

    fun initialize(context: Context) {
        authInterceptor = AuthInterceptor(context.applicationContext)
    }

    private val okHttpClient: OkHttpClient
        get() = OkHttpClient.Builder()
            .addInterceptor(jsonFixInterceptor)
            .addInterceptor(loggingInterceptor)
            .apply {
                authInterceptor?.let { addInterceptor(it) }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    private val retrofit: Retrofit
        get() = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    val apiService: ApiService
        get() = retrofit.create(ApiService::class.java)

}
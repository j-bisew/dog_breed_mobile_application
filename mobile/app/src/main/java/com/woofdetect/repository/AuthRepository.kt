package com.woofdetect.repository

import android.util.Log
import com.woofdetect.network.ApiClient
import com.woofdetect.network.dto.LoginRequest
import com.woofdetect.network.dto.LoginResponse
import com.woofdetect.network.dto.RegisterRequest
import com.woofdetect.network.dto.RegisterResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository {

    companion object {
        private const val TAG = "AuthRepository"
    }

    /*
        Login
     */
    suspend fun login(username: String, password: String): Result<LoginResponse> =
        withContext(Dispatchers.IO) {
            try {
                val request = LoginRequest(
                    loginData = LoginRequest.LoginData(
                        username = username,
                        password = password
                    )
                )

                Log.d(TAG, "Attempting login for user: $username")

                val response = ApiClient.apiService.login(request)

                if (response.isSuccessful && response.body() != null) {
                    val loginResponse = response.body()!!
                    Log.d(TAG, "Login successful: ${loginResponse.message}")
                    Result.success(loginResponse)
                } else {
                    val errorMsg = "Login failed: ${response.code()} - ${response.message()}"
                    Log.e(TAG, errorMsg)
                    Result.failure(Exception(errorMsg))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Login exception", e)
                Result.failure(e)
            }
        }

    /*
        Register
     */
    suspend fun register(
        name: String,
        username: String,
        email: String,
        password: String
    ): Result<RegisterResponse> = withContext(Dispatchers.IO) {
        try {
            val request = RegisterRequest(
                registrationData = RegisterRequest.RegistrationData(
                    name = name,
                    username = username,
                    email = email,
                    password = password
                )
            )

            Log.d(TAG, "Attempting registration for user: $username")

            val response = ApiClient.apiService.register(request)

            if (response.isSuccessful && response.body() != null) {
                val registerResponse = response.body()!!
                Log.d(TAG, "Registration successful: ${registerResponse.message}")
                Result.success(registerResponse)
            } else {
                val errorMsg = "Registration failed: ${response.code()} - ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Registration exception", e)
            Result.failure(e)
        }
    }
}
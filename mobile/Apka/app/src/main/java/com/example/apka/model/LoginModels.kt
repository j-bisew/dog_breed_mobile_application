package com.example.apka.model

data class LoginData(
    val username: String,
    val password: String
)

data class LoginRequest(
    val loginData: LoginData
)

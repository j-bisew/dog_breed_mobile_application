package com.woofdetect.network.dto

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("loginData")
    val loginData: LoginData
) {
    data class LoginData(
        @SerializedName("username")
        val username: String,

        @SerializedName("password")
        val password: String
    )
}
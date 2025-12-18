package com.woofdetect.network.dto

import com.google.gson.annotations.SerializedName

data class RegisterResponse(
    @SerializedName("message")
    val message: String,

    @SerializedName("token")
    val token: String
)
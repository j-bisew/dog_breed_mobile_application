package com.woofdetect.network.dto

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("registrationData")
    val registrationData: RegistrationData
) {
    data class RegistrationData(
        @SerializedName("name")
        val name: String,

        @SerializedName("username")
        val username: String,

        @SerializedName("email")
        val email: String,

        @SerializedName("password")
        val password: String
    )
}
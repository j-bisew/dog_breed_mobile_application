package com.woofdetect.network.dto

import com.google.gson.annotations.SerializedName

data class DogAnalysisResponse(
    @SerializedName("breedName")
    val breedName: String,

    @SerializedName("breedFullName")
    val breedFullName: String,

    @SerializedName("breedDescription")
    val breedDescription: String,

    @SerializedName("mainPhotoData")
    val mainPhotoData: String? = null,

    @SerializedName("confidence")
    val confidence: Double? = null,
)
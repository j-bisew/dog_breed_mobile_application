package com.woofdetect.model

import android.graphics.Bitmap

data class DogResult(
    val breedName: String,
    val breedFullName: String,
    val breedDescription: String,
    val confidence: Double,
    val breedPhoto: Bitmap? = null
)
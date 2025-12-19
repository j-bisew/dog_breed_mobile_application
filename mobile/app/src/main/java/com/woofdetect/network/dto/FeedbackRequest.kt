package com.woofdetect.network.dto

import com.google.gson.annotations.SerializedName

data class FeedbackRequest(
    @SerializedName("raceNameData")
    val raceNameData: RaceNameData
)

data class RaceNameData(
    @SerializedName("raceName")
    val raceName: String
)

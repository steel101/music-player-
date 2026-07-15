package com.steel101.musicplayer.network

import com.google.gson.annotations.SerializedName

data class AcoustIdResponse(
    @SerializedName("status") val status: String,
    @SerializedName("results") val results: List<AcoustIdResult>?
)

data class AcoustIdResult(
    @SerializedName("id") val id: String,
    @SerializedName("score") val score: Double,
    @SerializedName("recordings") val recordings: List<AcoustIdRecording>?
)

data class AcoustIdRecording(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String?,
    @SerializedName("artists") val artists: List<AcoustIdArtist>?
)

data class AcoustIdArtist(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String
)

package com.steel101.musicplayer.network

import com.google.gson.annotations.SerializedName

data class ItunesResponse(
    @SerializedName("resultCount") val resultCount: Int,
    @SerializedName("results") val results: List<ItunesResult>?
)

data class ItunesResult(
    @SerializedName("artworkUrl100") val artworkUrl100: String?,
    @SerializedName("artistName") val artistName: String?,
    @SerializedName("collectionName") val collectionName: String?,
    @SerializedName("primaryGenreName") val primaryGenreName: String?,
    @SerializedName("releaseDate") val releaseDate: String?
)

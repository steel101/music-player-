package com.steel101.musicplayer.network

import com.google.gson.annotations.SerializedName

data class AudioDbResponse(
    @SerializedName("artists") val artists: List<AudioDbArtist>?
)

data class AudioDbArtist(
    @SerializedName("idArtist") val id: String?,
    @SerializedName("strArtist") val name: String?,
    @SerializedName("strArtistThumb") val thumbUrl: String?,
    @SerializedName("strArtistLogo") val logoUrl: String?,
    @SerializedName("strArtistFanart") val fanartUrl: String?,
    @SerializedName("strBiographyEN") val biography: String?
)

data class AudioDbAlbumResponse(
    @SerializedName("album") val albums: List<AudioDbAlbum>?
)

data class AudioDbAlbum(
    @SerializedName("idAlbum") val id: String?,
    @SerializedName("strAlbum") val title: String?,
    @SerializedName("strArtist") val artist: String?,
    @SerializedName("intYearReleased") val year: String?,
    @SerializedName("strAlbumThumb") val thumbUrl: String?
)

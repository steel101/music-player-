package com.steel101.musicplayer.network

import com.google.gson.annotations.SerializedName

data class MusicBrainzResponse(
    @SerializedName("recordings") val recordings: List<Recording>?
)

data class MusicBrainzArtistResponse(
    @SerializedName("artists") val artists: List<ArtistDetails>?
)

data class Recording(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist-credit") val artistCredit: List<ArtistCredit>?,
    @SerializedName("releases") val releases: List<Release>?,
    @SerializedName("tags") val tags: List<Tag>?
)

data class Tag(
    @SerializedName("name") val name: String,
    @SerializedName("count") val count: Int
)

data class ArtistCredit(
    @SerializedName("artist") val artist: Artist
)

data class Artist(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String
)

data class Release(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("date") val date: String?
)

data class ArtistDetails(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("relations") val relations: List<Relation>?
)

data class Relation(
    @SerializedName("type") val type: String,
    @SerializedName("url") val url: RelationUrl
)

data class RelationUrl(
    @SerializedName("resource") val resource: String
)

data class MusicBrainzSearchReleaseResponse(
    @SerializedName("releases") val releases: List<Release>?
)

data class MusicBrainzReleaseDetails(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("media") val media: List<Medium>?
)

data class Medium(
    @SerializedName("tracks") val tracks: List<MBTrack>?
)

data class MBTrack(
    @SerializedName("id") val id: String,
    @SerializedName("position") val position: Int,
    @SerializedName("title") val title: String,
    @SerializedName("recording") val recording: Recording?
)

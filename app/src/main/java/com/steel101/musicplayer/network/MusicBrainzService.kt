package com.steel101.musicplayer.network

import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface MusicBrainzService {
    @Headers("User-Agent: MusicPlayerApp/1.0.0 ( steel.music.player@example.com )")
    @GET("recording/")
    suspend fun searchRecording(
        @Query("query") query: String,
        @Query("fmt") format: String = "json"
    ): MusicBrainzResponse

    @Headers("User-Agent: MusicPlayerApp/1.0.0 ( steel.music.player@example.com )")
    @GET("artist/")
    suspend fun searchArtist(
        @Query("query") query: String,
        @Query("fmt") format: String = "json"
    ): MusicBrainzArtistResponse

    @Headers("User-Agent: MusicPlayerApp/1.0.0 ( steel.music.player@example.com )")
    @GET("artist/{mbid}")
    suspend fun getArtist(
        @Path("mbid") mbid: String,
        @Query("inc") includes: String = "url-rels",
        @Query("fmt") format: String = "json"
    ): ArtistDetails

    @Headers("User-Agent: MusicPlayerApp/1.0.0 ( steel.music.player@example.com )")
    @GET("release/")
    suspend fun searchRelease(
        @Query("query") query: String,
        @Query("fmt") format: String = "json"
    ): MusicBrainzSearchReleaseResponse

    @Headers("User-Agent: MusicPlayerApp/1.0.0 ( steel.music.player@example.com )")
    @GET("release/{mbid}")
    suspend fun getRelease(
        @Path("mbid") mbid: String,
        @Query("inc") includes: String = "recordings",
        @Query("fmt") format: String = "json"
    ): MusicBrainzReleaseDetails
}

package com.steel101.musicplayer.network

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface LyricsService {
    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String?,
        @Query("duration") durationInSeconds: Int,
        @Header("User-Agent") userAgent: String = "MusicPlayerApp/1.0.0 (https://github.com/example/musicplayer)"
    ): LyricsResponse

    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String,
        @Header("User-Agent") userAgent: String = "MusicPlayerApp/1.0.0 (https://github.com/example/musicplayer)"
    ): List<LyricsResponse>
}

data class LyricsResponse(
    val id: Long?,
    val trackName: String?,
    val artistName: String?,
    val albumName: String?,
    val duration: Int?,
    val instrumental: Boolean?,
    val plainLyrics: String?,
    val syncedLyrics: String?
)

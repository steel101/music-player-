package com.steel101.musicplayer.network

import retrofit2.http.GET
import retrofit2.http.Query

interface AudioDbService {
    @GET("search.php")
    suspend fun searchArtist(
        @Query("s") artistName: String
    ): AudioDbResponse

    @GET("album.php")
    suspend fun getAlbums(
        @Query("i") artistId: String
    ): AudioDbAlbumResponse
}

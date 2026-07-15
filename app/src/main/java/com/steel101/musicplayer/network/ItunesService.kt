package com.steel101.musicplayer.network

import retrofit2.http.GET
import retrofit2.http.Query

interface ItunesService {
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("entity") entity: String = "album",
        @Query("limit") limit: Int = 1
    ): ItunesResponse
}

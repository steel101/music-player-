package com.steel101.musicplayer.network

import retrofit2.http.GET
import retrofit2.http.Query

interface ItunesService {
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("entity") entity: String = "album",
        @Query("limit") limit: Int = 50,
        @Query("attribute") attribute: String? = null
    ): ItunesResponse

    @GET("lookup")
    suspend fun lookup(
        @Query("id") id: String,
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 200
    ): ItunesResponse
}

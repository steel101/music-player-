package com.steel101.musicplayer.network

import retrofit2.http.GET
import retrofit2.http.Query

interface AcoustIdService {
    @GET("lookup")
    suspend fun lookup(
        @Query("client") client: String,
        @Query("duration") duration: Int,
        @Query("fingerprint") fingerprint: String,
        @Query("meta") meta: String = "recordings releasegroups releases tracks compress",
        @Query("format") format: String = "json"
    ): AcoustIdResponse
}

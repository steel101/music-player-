package com.steel101.musicplayer.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface NeteaseService {
    @GET("api/search/get/web")
    suspend fun search(
        @Query("s") query: String,
        @Query("type") type: Int = 1,
        @Query("limit") limit: Int = 1
    ): NeteaseSearchResponse

    @GET("api/song/lyric")
    suspend fun getLyrics(
        @Query("id") songId: Long,
        @Query("lv") lv: Int = 1,
        @Query("kv") kv: Int = 1,
        @Query("tv") tv: Int = -1
    ): NeteaseLyricResponse
}

data class NeteaseSearchResponse(
    @SerializedName("result") val result: NeteaseSearchResult?,
    @SerializedName("code") val code: Int
)

data class NeteaseSearchResult(
    @SerializedName("songs") val songs: List<NeteaseSong>?
)

data class NeteaseSong(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("artists") val artists: List<NeteaseArtist>?
)

data class NeteaseArtist(
    @SerializedName("name") val name: String
)

data class NeteaseLyricResponse(
    @SerializedName("lrc") val lrc: NeteaseLrc?,
    @SerializedName("code") val code: Int
)

data class NeteaseLrc(
    @SerializedName("lyric") val lyric: String?
)

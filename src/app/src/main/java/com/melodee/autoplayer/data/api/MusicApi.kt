package com.melodee.autoplayer.data.api

import com.melodee.autoplayer.domain.model.*
import retrofit2.http.*

interface MusicApi {
    @POST("user/authenticate")
    suspend fun login(
        @Body credentials: Map<String, String>
    ): AuthResponse

    @GET("user/playlists")
    suspend fun getPlaylists(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 20
    ): PaginatedResponse<Playlist>

    @GET("playlist/{playlistId}/songs")
    suspend fun getPlaylistSongs(
        @Path("playlistId") playlistId: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 20
    ): PaginatedResponse<Song>

    @GET("search")
    suspend fun searchSongs(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 20
    ): PaginatedResponse<Song>

    @POST("song/starred/{songId}/{userStarred}")
    suspend fun favoriteSong(
        @Path("songId") songId: String,
        @Path("userStarred") userStarred: Boolean
    ): retrofit2.Response<Unit>
} 
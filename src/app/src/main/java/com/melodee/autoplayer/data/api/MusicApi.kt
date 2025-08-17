package com.melodee.autoplayer.data.api

import com.melodee.autoplayer.domain.model.*
import retrofit2.http.*

interface MusicApi {
    @POST("users/authenticate")
    suspend fun login(
        @Body credentials: Map<String, String>
    ): AuthResponse

    @GET("users/playlists")
    suspend fun getPlaylists(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 50
    ): PaginatedResponse<Playlist>

    @GET("playlists/{playlistId}/songs")
    suspend fun getPlaylistSongs(
        @Path("playlistId") playlistId: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 50
    ): PaginatedResponse<Song>

    @GET("search/songs")
    suspend fun searchSongs(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 50
    ): PaginatedResponse<Song>

    @GET("artists")
    suspend fun getArtists(
        @Query("q") query: String? = null,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 50
    ): PaginatedResponse<Artist>

    @GET("artists/{artistId}/songs")
    suspend fun getArtistSongs(
        @Path("artistId") artistId: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 50
    ): PaginatedResponse<Song>

    @GET("artists/{artistId}/albums")
    suspend fun getArtistAlbums(
        @Path("artistId") artistId: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 50
    ): PaginatedResponse<Album>

    @GET("albums/{albumId}/songs")
    suspend fun getAlbumSongs(
        @Path("albumId") albumId: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 50
    ): PaginatedResponse<Song>

    @GET("search/songs")
    suspend fun searchSongsWithArtist(
        @Query("q") query: String,
        @Query("filterByArtistApiKey") artistId: String?,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 50
    ): PaginatedResponse<Song>

    @POST("songs/starred/{songId}/{userStarred}")
    suspend fun favoriteSong(
        @Path("songId") songId: String,
        @Path("userStarred") userStarred: Boolean
    ): retrofit2.Response<Unit>
} 
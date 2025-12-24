package com.melodee.autoplayer.data.api

import com.melodee.autoplayer.domain.model.*
import retrofit2.http.*

interface MusicApi {
    @POST("api/v1/auth/authenticate")
    suspend fun login(
        @Body credentials: LoginModel
    ): AuthenticationResponse

    // Fetch current authenticated user profile
    @GET("api/v1/user/me")
    suspend fun getCurrentUser(): User

    @GET("api/v1/user/playlists")
    suspend fun getPlaylists(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 50
    ): PlaylistPagedResponse

    @GET("api/v1/playlists/{id}/songs")
    suspend fun getPlaylistSongs(
        @Path("id") playlistId: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 50
    ): SongPagedResponse

    @GET("api/v1/search/songs")
    suspend fun searchSongs(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 50,
        @Query("filterByArtistApiKey") artistId: String? = null
    ): SongPagedResponse

    @GET("api/v1/artists")
    suspend fun getArtists(
        @Query("q") query: String? = null,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 50,
        @Query("orderBy") orderBy: String? = null,
        @Query("orderDirection") orderDirection: String? = null
    ): ArtistPagedResponse

    @GET("api/v1/artists/{id}/songs")
    suspend fun getArtistSongs(
        @Path("id") artistId: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 50
    ): SongPagedResponse

    @GET("api/v1/artists/{id}/albums")
    suspend fun getArtistAlbums(
        @Path("id") artistId: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 50
    ): AlbumPagedResponse

    @GET("api/v1/albums/{id}/songs")
    suspend fun getAlbumSongs(
        @Path("id") albumId: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 50
    ): SongPagedResponse

    @POST("api/v1/songs/starred/{id}/{isStarred}")
    suspend fun favoriteSong(
        @Path("id") songId: String,
        @Path("isStarred") userStarred: Boolean
    ): retrofit2.Response<Unit>
}

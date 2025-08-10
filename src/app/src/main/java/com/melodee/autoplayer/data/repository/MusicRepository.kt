package com.melodee.autoplayer.data.repository

import android.content.Context
import com.melodee.autoplayer.R
import com.melodee.autoplayer.data.api.MusicApi
import com.melodee.autoplayer.data.api.NetworkModule
import com.melodee.autoplayer.domain.model.AuthResponse
import com.melodee.autoplayer.domain.model.PaginatedResponse
import com.melodee.autoplayer.domain.model.Playlist
import com.melodee.autoplayer.domain.model.Song
import com.melodee.autoplayer.domain.model.Artist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException

class MusicRepository(private val baseUrl: String, private val context: Context) {
    init {
        NetworkModule.setBaseUrl(baseUrl)
    }

    private val api: MusicApi
        get() = NetworkModule.getMusicApi()

    fun login(email: String, password: String): Flow<AuthResponse> = flow {
        try {
            val response = api.login(mapOf("email" to email, "password" to password))
            NetworkModule.setAuthToken(response.token)
            emit(response)
        } catch (e: HttpException) {
            val errorMessage = when (e.code()) {
                400 -> context.getString(R.string.invalid_credentials)
                401 -> context.getString(R.string.unauthorized_access)
                403 -> context.getString(R.string.forbidden_access)
                404 -> context.getString(R.string.account_not_found)
                429 -> context.getString(R.string.too_many_requests)
                in 500..599 -> context.getString(R.string.server_error)
                else -> context.getString(R.string.login_failed, "HTTP ${e.code()}")
            }
            throw IOException(errorMessage)
        } catch (e: java.net.UnknownHostException) {
            throw IOException(context.getString(R.string.server_unreachable))
        } catch (e: java.net.ConnectException) {
            throw IOException(context.getString(R.string.server_unreachable))
        } catch (e: java.net.SocketTimeoutException) {
            throw IOException(context.getString(R.string.server_unreachable))
        } catch (e: IOException) {
            throw IOException(context.getString(R.string.network_error, e.message ?: "Unknown network error"))
        } catch (e: Exception) {
            throw IOException(context.getString(R.string.login_failed, e.message ?: "Unknown error"))
        }
    }

    fun getPlaylists(page: Int): Flow<PaginatedResponse<Playlist>> = flow {
        try {
            val response = api.getPlaylists(page)
            emit(response)
        } catch (e: HttpException) {
            throw IOException(context.getString(R.string.failed_to_get_playlists, e.message()))
        } catch (e: IOException) {
            throw IOException(context.getString(R.string.network_error, e.message))
        }
    }

    fun getPlaylistSongs(playlistId: String, page: Int): Flow<PaginatedResponse<Song>> = flow {
        try {
            val response = api.getPlaylistSongs(playlistId, page)
            emit(response)
        } catch (e: HttpException) {
            throw IOException(context.getString(R.string.failed_to_get_playlist_songs, e.message()))
        } catch (e: IOException) {
            throw IOException(context.getString(R.string.network_error, e.message))
        }
    }

    fun searchSongs(query: String, page: Int = 1): Flow<PaginatedResponse<Song>> = flow {
        try {
            val response = api.searchSongs(query, page)
            emit(response)
        } catch (e: HttpException) {
            throw IOException(context.getString(R.string.failed_to_search_songs, e.message()))
        } catch (e: IOException) {
            throw IOException(context.getString(R.string.network_error, e.message))
        }
    }

    fun searchArtists(query: String, page: Int = 1): Flow<PaginatedResponse<Artist>> = flow {
        try {
            val response = api.getArtists(
                query = if (query.isBlank()) null else query,
                page = page
            )
            emit(response)
        } catch (e: HttpException) {
            throw IOException(context.getString(R.string.network_error, e.message()))
        } catch (e: IOException) {
            throw IOException(context.getString(R.string.network_error, e.message))
        }
    }

    fun getArtistSongs(artistId: String, page: Int = 1): Flow<PaginatedResponse<Song>> = flow {
        try {
            val response = api.getArtistSongs(artistId, page)
            emit(response)
        } catch (e: HttpException) {
            throw IOException(context.getString(R.string.network_error, e.message()))
        } catch (e: IOException) {
            throw IOException(context.getString(R.string.network_error, e.message))
        }
    }

    fun searchSongsWithArtist(query: String, artistId: String?, page: Int = 1): Flow<PaginatedResponse<Song>> = flow {
        try {
            val response = api.searchSongsWithArtist(query, artistId, page)
            emit(response)
        } catch (e: HttpException) {
            throw IOException(context.getString(R.string.failed_to_search_songs, e.message()))
        } catch (e: IOException) {
            throw IOException(context.getString(R.string.network_error, e.message))
        }
    }

    suspend fun favoriteSong(songId: String, userStarred: Boolean): Boolean {
        return try {
            val response = api.favoriteSong(songId, userStarred)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
} 
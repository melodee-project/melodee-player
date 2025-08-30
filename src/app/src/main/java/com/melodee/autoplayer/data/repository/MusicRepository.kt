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
import com.melodee.autoplayer.domain.model.Album
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
    
    private val deduplicator = RequestDeduplicator.getInstance()

    fun login(emailOrUsername: String, password: String): Flow<AuthResponse> = flow {
        val response = ErrorHandler.handleOperation(context, "login", "MusicRepository") {
            // Determine if input is email or username based on presence of @ symbol
            val loginMap = if (emailOrUsername.contains("@")) {
                mapOf("email" to emailOrUsername, "password" to password)
            } else {
                mapOf("userName" to emailOrUsername, "password" to password)
            }
            val response = api.login(loginMap)
            NetworkModule.setAuthToken(response.token)
            response
        }
        emit(response)
    }

    fun getPlaylists(page: Int): Flow<PaginatedResponse<Playlist>> = flow {
        val response = ErrorHandler.handleOperation(context, "getPlaylists", "MusicRepository") {
            api.getPlaylists(page)
        }
        emit(response)
    }

    fun getPlaylistSongs(playlistId: String, page: Int): Flow<PaginatedResponse<Song>> = flow {
        val response = ErrorHandler.handleOperation(context, "getPlaylistSongs", "MusicRepository") {
            api.getPlaylistSongs(playlistId, page)
        }
        emit(response)
    }

    suspend fun searchSongs(query: String, page: Int = 1): Flow<PaginatedResponse<Song>> {
        val key = deduplicator.generateKey("searchSongs", query, page)
        return deduplicator.deduplicate(key) {
            kotlinx.coroutines.flow.flow {
                val response = ErrorHandler.handleOperation(context, "searchSongs", "MusicRepository") {
                    api.searchSongs(query, page)
                }
                emit(response)
            }
        }
    }

    suspend fun searchArtists(query: String, page: Int = 1): Flow<PaginatedResponse<Artist>> {
        val key = deduplicator.generateKey("searchArtists", query, page)
        return deduplicator.deduplicate(key) {
            kotlinx.coroutines.flow.flow {
                val response = ErrorHandler.handleOperation(context, "searchArtists", "MusicRepository") {
                    api.getArtists(
                        query = if (query.isBlank()) null else query,
                        page = page
                    )
                }
                emit(response)
            }
        }
    }

    fun getArtistSongs(artistId: String, page: Int = 1): Flow<PaginatedResponse<Song>> = flow {
        val response = ErrorHandler.handleOperation(context, "getArtistSongs", "MusicRepository") {
            api.getArtistSongs(artistId, page)
        }
        emit(response)
    }

    fun getArtistAlbums(artistId: String, page: Int = 1): Flow<PaginatedResponse<Album>> = flow {
        val response = ErrorHandler.handleOperation(context, "getArtistAlbums", "MusicRepository") {
            api.getArtistAlbums(artistId, page)
        }
        emit(response)
    }

    fun getAlbumSongs(albumId: String, page: Int = 1): Flow<PaginatedResponse<Song>> = flow {
        val response = ErrorHandler.handleOperation(context, "getAlbumSongs", "MusicRepository") {
            api.getAlbumSongs(albumId, page)
        }
        emit(response)
    }

    fun searchSongsWithArtist(query: String, artistId: String?, page: Int = 1): Flow<PaginatedResponse<Song>> = flow {
        val response = api.searchSongsWithArtist(query, artistId, page)
        emit(response)
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
package com.melodee.autoplayer.data.repository

import android.content.Context
import com.melodee.autoplayer.R
import com.melodee.autoplayer.data.api.MusicApi
import com.melodee.autoplayer.data.api.NetworkModule
import com.melodee.autoplayer.domain.model.Album
import com.melodee.autoplayer.domain.model.Artist
import com.melodee.autoplayer.domain.model.AuthResponse
import com.melodee.autoplayer.domain.model.LoginModel
import com.melodee.autoplayer.domain.model.PaginatedResponse
import com.melodee.autoplayer.domain.model.Playlist
import com.melodee.autoplayer.domain.model.ServerInfo
import com.melodee.autoplayer.domain.model.Song
import com.melodee.autoplayer.domain.model.User
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

    suspend fun getSystemInfo(): ServerInfo {
        return ErrorHandler.handleOperation(context, "getSystemInfo", "MusicRepository") {
            api.getSystemInfo()
        }
    }

    suspend fun validateServerVersion(): Boolean {
        return try {
            val serverInfo = getSystemInfo()
            if (!serverInfo.isCompatibleVersion()) {
                throw IllegalStateException(
                    "Server API version ${serverInfo.getVersionString()} is not compatible. " +
                    "Minimum required version is 1.2.0"
                )
            }
            true
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw Exception("Failed to validate server version: ${e.message}", e)
        }
    }

    fun login(emailOrUsername: String, password: String): Flow<AuthResponse> = flow {
        validateServerVersion()
        
        val response = ErrorHandler.handleOperation(context, "login", "MusicRepository") {
            val loginModel = if (emailOrUsername.contains("@")) {
                LoginModel(email = emailOrUsername, password = password)
            } else {
                LoginModel(userName = emailOrUsername, password = password)
            }
            val response = api.login(loginModel)
            NetworkModule.setTokens(response.token, response.refreshToken)
            response
        }
        emit(response)
    }

    fun getPlaylists(page: Int): Flow<PaginatedResponse<Playlist>> = flow {
        val response = ErrorHandler.handleOperation(context, "getPlaylists", "MusicRepository") {
            api.getPlaylists(page)
        }
        emit(PaginatedResponse(meta = response.meta, data = response.data))
    }

    fun getCurrentUser(): Flow<User> = flow {
        val response = ErrorHandler.handleOperation(context, "getCurrentUser", "MusicRepository") {
            api.getCurrentUser()
        }
        emit(response)
    }

    fun getPlaylistSongs(playlistId: String, page: Int): Flow<PaginatedResponse<Song>> = flow {
        val response = ErrorHandler.handleOperation(context, "getPlaylistSongs", "MusicRepository") {
            api.getPlaylistSongs(playlistId, page)
        }
        emit(PaginatedResponse(meta = response.meta, data = response.data))
    }

    suspend fun searchSongs(query: String, page: Int = 1): Flow<PaginatedResponse<Song>> {
        val key = deduplicator.generateKey("searchSongs", query, page)
        return deduplicator.deduplicate(key) {
            kotlinx.coroutines.flow.flow {
                val response = ErrorHandler.handleOperation(context, "searchSongs", "MusicRepository") {
                    api.searchSongs(query, page)
                }
                emit(PaginatedResponse(meta = response.meta, data = response.data))
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
                emit(PaginatedResponse(meta = response.meta, data = response.data))
            }
        }
    }

    fun getArtistSongs(artistId: String, page: Int = 1): Flow<PaginatedResponse<Song>> = flow {
        val response = ErrorHandler.handleOperation(context, "getArtistSongs", "MusicRepository") {
            api.getArtistSongs(artistId, page)
        }
        emit(PaginatedResponse(meta = response.meta, data = response.data))
    }

    fun getArtistAlbums(artistId: String, page: Int = 1): Flow<PaginatedResponse<Album>> = flow {
        val response = ErrorHandler.handleOperation(context, "getArtistAlbums", "MusicRepository") {
            api.getArtistAlbums(artistId, page)
        }
        emit(PaginatedResponse(meta = response.meta, data = response.data))
    }

    fun getAlbumSongs(albumId: String, page: Int = 1): Flow<PaginatedResponse<Song>> = flow {
        val response = ErrorHandler.handleOperation(context, "getAlbumSongs", "MusicRepository") {
            api.getAlbumSongs(albumId, page)
        }
        emit(PaginatedResponse(meta = response.meta, data = response.data))
    }

    fun searchSongsWithArtist(query: String, artistId: String?, page: Int = 1): Flow<PaginatedResponse<Song>> = flow {
        val response = api.searchSongs(query, page, artistId = artistId)
        emit(PaginatedResponse(meta = response.meta, data = response.data))
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

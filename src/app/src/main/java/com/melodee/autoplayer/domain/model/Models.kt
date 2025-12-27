package com.melodee.autoplayer.domain.model

import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat
import android.util.Log
import java.util.UUID

// Phase 1 note: Retrofit uses Gson (NetworkModule) for JSON; models live in domain.model; IDs remain UUID serialized as strings.

data class PaginationMetadata(
    val totalCount: Int,
    val pageSize: Int,
    val currentPage: Int,
    val totalPages: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean
)

typealias PaginationMeta = PaginationMetadata

data class PaginatedResponse<T>(
    val meta: PaginationMetadata,
    val data: List<T>
)

data class SongPagedResponse(
    val meta: PaginationMetadata,
    val data: List<Song>
)

data class PlaylistPagedResponse(
    val meta: PaginationMetadata,
    val data: List<Playlist>
)

data class ArtistPagedResponse(
    val meta: PaginationMetadata,
    val data: List<Artist>
)

data class AlbumPagedResponse(
    val meta: PaginationMetadata,
    val data: List<Album>
)

data class SearchResultData(
    val meta: PaginationMetadata,
    val data: SearchData
)

data class SearchData(
    val totalCount: Int,
    val artists: List<Artist>,
    val totalArtists: Int,
    val albums: List<Album>,
    val totalAlbums: Int,
    val songs: List<Song>,
    val totalSongs: Int,
    val playlists: List<Playlist>,
    val totalPlaylists: Int
)

data class Playlist(
    val id: UUID,
    val name: String,
    val description: String = "",
    val imageUrl: String,
    val thumbnailUrl: String,
    val durationMs: Double,
    val durationFormatted: String,
    val songCount: Int,
    val isPublic: Boolean = false,
    val owner: User? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
)

/**
 * Song model with parcelable support.
 *
 * WARNING: This class has a deep parcelable graph (Song → Album → Artist → List<String>).
 * When passing List<Song> via Intent, monitor parcel size to avoid TransactionTooLargeException.
 * Android's Binder has a 1MB transaction limit.
 *
 * For large collections (>100 songs), consider passing only List<UUID> and fetching from ViewModel.
 */
data class Song(
    val id: UUID,
    val streamUrl: String,
    val title: String,
    val artist: Artist,
    val album: Album,
    val thumbnailUrl: String,
    val imageUrl: String,
    val durationMs: Double,
    val durationFormatted: String,
    val userStarred: Boolean = false,
    val userRating: Int = 0,
    val songNumber: Int = 0,
    val bitrate: Int = 0,
    val playCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val genre: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        id = parcel.readUuidString(),
        streamUrl = parcel.readStringOrEmpty(),
        title = parcel.readStringOrEmpty(),
        artist = ParcelCompat.readParcelable(parcel, Artist::class.java.classLoader, Artist::class.java) ?: emptyArtist(),
        album = ParcelCompat.readParcelable(parcel, Album::class.java.classLoader, Album::class.java) ?: emptyAlbum(),
        thumbnailUrl = parcel.readStringOrEmpty(),
        imageUrl = parcel.readStringOrEmpty(),
        durationMs = parcel.readDouble(),
        durationFormatted = parcel.readStringOrEmpty(),
        userStarred = parcel.readBooleanByte(),
        userRating = parcel.readInt(),
        songNumber = parcel.readInt(),
        bitrate = parcel.readInt(),
        playCount = parcel.readInt(),
        createdAt = parcel.readStringOrEmpty(),
        updatedAt = parcel.readStringOrEmpty(),
        genre = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        val startSize = parcel.dataSize()

        parcel.writeString(id.toString())
        parcel.writeString(streamUrl)
        parcel.writeString(title)
        parcel.writeParcelable(artist, flags)
        parcel.writeParcelable(album, flags)
        parcel.writeString(thumbnailUrl)
        parcel.writeString(imageUrl)
        parcel.writeDouble(durationMs)
        parcel.writeString(durationFormatted)
        parcel.writeByte(if (userStarred) 1 else 0)
        parcel.writeInt(userRating)
        parcel.writeInt(songNumber)
        parcel.writeInt(bitrate)
        parcel.writeInt(playCount)
        parcel.writeString(createdAt)
        parcel.writeString(updatedAt)
        parcel.writeString(genre)

        val endSize = parcel.dataSize()
        val size = endSize - startSize

        if (size > 5000) {
            Log.w("Song", "Large parcel detected: $size bytes for song $id")
        }
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Song> {
        override fun createFromParcel(parcel: Parcel): Song = Song(parcel)
        override fun newArray(size: Int): Array<Song?> = arrayOfNulls(size)
    }
}

data class Artist(
    val id: UUID,
    val name: String,
    val thumbnailUrl: String,
    val imageUrl: String,
    val userStarred: Boolean = false,
    val userRating: Int = 0,
    val albumCount: Int = 0,
    val songCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val biography: String? = null,
    val genres: List<String> = emptyList()
) : Parcelable {
    constructor(parcel: Parcel) : this(
        id = parcel.readUuidString(),
        name = parcel.readStringOrEmpty(),
        thumbnailUrl = parcel.readStringOrEmpty(),
        imageUrl = parcel.readStringOrEmpty(),
        userStarred = parcel.readBooleanByte(),
        userRating = parcel.readInt(),
        albumCount = parcel.readInt(),
        songCount = parcel.readInt(),
        createdAt = parcel.readStringOrEmpty(),
        updatedAt = parcel.readStringOrEmpty(),
        biography = parcel.readString(),
        genres = mutableListOf<String>().apply { parcel.readStringList(this) }.toList()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id.toString())
        parcel.writeString(name)
        parcel.writeString(thumbnailUrl)
        parcel.writeString(imageUrl)
        parcel.writeByte(if (userStarred) 1 else 0)
        parcel.writeInt(userRating)
        parcel.writeInt(albumCount)
        parcel.writeInt(songCount)
        parcel.writeString(createdAt)
        parcel.writeString(updatedAt)
        parcel.writeString(biography)
        parcel.writeStringList(genres)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Artist> {
        override fun createFromParcel(parcel: Parcel): Artist = Artist(parcel)
        override fun newArray(size: Int): Array<Artist?> = arrayOfNulls(size)
    }
}

data class Album(
    val id: UUID,
    val name: String,
    val thumbnailUrl: String,
    val imageUrl: String,
    val releaseYear: Int,
    val userStarred: Boolean = false,
    val userRating: Int = 0,
    val artist: Artist? = null,
    val songCount: Int = 0,
    val durationMs: Double = 0.0,
    val durationFormatted: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val description: String? = null,
    val genre: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        id = parcel.readUuidString(),
        name = parcel.readStringOrEmpty(),
        thumbnailUrl = parcel.readStringOrEmpty(),
        imageUrl = parcel.readStringOrEmpty(),
        releaseYear = parcel.readInt(),
        userStarred = parcel.readBooleanByte(),
        userRating = parcel.readInt(),
        artist = ParcelCompat.readParcelable(parcel, Artist::class.java.classLoader, Artist::class.java),
        songCount = parcel.readInt(),
        durationMs = parcel.readDouble(),
        durationFormatted = parcel.readStringOrEmpty(),
        createdAt = parcel.readStringOrEmpty(),
        updatedAt = parcel.readStringOrEmpty(),
        description = parcel.readString(),
        genre = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id.toString())
        parcel.writeString(name)
        parcel.writeString(thumbnailUrl)
        parcel.writeString(imageUrl)
        parcel.writeInt(releaseYear)
        parcel.writeByte(if (userStarred) 1 else 0)
        parcel.writeInt(userRating)
        parcel.writeParcelable(artist, flags)
        parcel.writeInt(songCount)
        parcel.writeDouble(durationMs)
        parcel.writeString(durationFormatted)
        parcel.writeString(createdAt)
        parcel.writeString(updatedAt)
        parcel.writeString(description)
        parcel.writeString(genre)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Album> {
        override fun createFromParcel(parcel: Parcel): Album = Album(parcel)
        override fun newArray(size: Int): Array<Album?> = arrayOfNulls(size)
    }
}

data class AuthenticationResponse(
    val token: String,
    var serverVersion: String,
    val user: User,
    val expiresAt: String = "",
    val refreshToken: String = "",
    val refreshTokenExpiresAt: String = ""
)

typealias AuthResponse = AuthenticationResponse

data class LoginModel(
    val userName: String? = null,
    val email: String? = null,
    val password: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class ServerInfo(
    val name: String? = null,
    val description: String? = null,
    val majorVersion: Int = 0,
    val minorVersion: Int = 0,
    val patchVersion: Int = 0,
    val version: String? = null
) {
    /**
     * Checks if the server API version is compatible with this client.
     * Minimum required version: 1.2.0
     * Compatible versions: 1.2.0, 1.2.5, 1.7.8, 2.0.0, 8.3.0, etc.
     * Incompatible versions: 1.1.9, 1.0.0, 0.9.5, etc.
     */
    fun isCompatibleVersion(): Boolean {
        // Required minimum version is 1.2.0
        val requiredMajor = 1
        val requiredMinor = 2
        
        return when {
            // Any major version above 1 is compatible (2.x.x, 3.x.x, etc.)
            majorVersion > requiredMajor -> true
            // For major version 1, require minor version 2 or higher (1.2.x, 1.3.x, etc.)
            majorVersion == requiredMajor && minorVersion >= requiredMinor -> true
            // All other versions (1.0.x, 1.1.x, 0.x.x) are incompatible
            else -> false
        }
    }
    
    fun getVersionString(): String {
        return "$majorVersion.$minorVersion.$patchVersion"
    }
}

data class User(
    val id: UUID,
    val email: String,
    val thumbnailUrl: String,
    val imageUrl: String,
    val username: String,
    val isAdmin: Boolean = false,
    val isEditor: Boolean = false,
    val roles: List<String> = emptyList(),
    val songsPlayed: Int = 0,
    val artistsLiked: Int = 0,
    val artistsDisliked: Int = 0,
    val albumsLiked: Int = 0,
    val albumsDisliked: Int = 0,
    val songsLiked: Int = 0,
    val songsDisliked: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = ""
)

private val EMPTY_UUID: UUID = UUID(0, 0)

private fun Parcel.readStringOrEmpty(): String = readString().orEmpty()
private fun Parcel.readUuidString(): UUID = readString()?.let(UUID::fromString) ?: EMPTY_UUID
private fun Parcel.readBooleanByte(): Boolean = readByte() != 0.toByte()

private fun emptyArtist(): Artist = Artist(
    id = EMPTY_UUID,
    name = "",
    thumbnailUrl = "",
    imageUrl = "",
    userStarred = false,
    userRating = 0,
    albumCount = 0,
    songCount = 0,
    createdAt = "",
    updatedAt = "",
    biography = null,
    genres = emptyList()
)

private fun emptyAlbum(): Album = Album(
    id = EMPTY_UUID,
    name = "",
    thumbnailUrl = "",
    imageUrl = "",
    releaseYear = 0,
    userStarred = false,
    userRating = 0,
    artist = null,
    songCount = 0,
    durationMs = 0.0,
    durationFormatted = "",
    createdAt = "",
    updatedAt = "",
    description = null,
    genre = null
)

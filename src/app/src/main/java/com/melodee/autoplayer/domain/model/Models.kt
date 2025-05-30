package com.melodee.autoplayer.domain.model

import java.util.UUID
import android.os.Parcel
import android.os.Parcelable

data class PaginatedResponse<T>(
    val meta: PaginationMeta,
    val data: List<T>
)

data class PaginationMeta(
    val totalCount: Int,
    val pageSize: Int,
    val currentPage: Int,
    val totalPages: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean
)

data class Playlist(
    val id: UUID,
    val name: String,
    val description: String? = null,
    val imageUrl: String,
    val thumbnailUrl: String,
    val durationMs: Double,
    val durationFormatted: String,
    val songsCount: Int
)

data class Song(
    val id: UUID,
    val streamUrl: String,
    val name: String,
    val artist: Artist,
    val album: Album,
    val thumbnailUrl: String,
    val imageUrl: String,
    val durationMs: Double,
    val durationFormatted: String,
    val userStarred: Boolean = false,
    val userRating: Double
) : Parcelable {
    constructor(parcel: Parcel) : this(
        UUID.fromString(parcel.readString()),
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readParcelable(Artist::class.java.classLoader)!!,
        parcel.readParcelable(Album::class.java.classLoader)!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readDouble(),
        parcel.readString()!!,
        parcel.readByte() != 0.toByte(),
        parcel.readDouble()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id.toString())
        parcel.writeString(streamUrl)
        parcel.writeString(name)
        parcel.writeParcelable(artist, flags)
        parcel.writeParcelable(album, flags)
        parcel.writeString(thumbnailUrl)
        parcel.writeString(imageUrl)
        parcel.writeDouble(durationMs)
        parcel.writeString(durationFormatted)
        parcel.writeByte(if (userStarred) 1 else 0)
        parcel.writeDouble(userRating)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Song> {
        override fun createFromParcel(parcel: Parcel): Song {
            return Song(parcel)
        }

        override fun newArray(size: Int): Array<Song?> {
            return arrayOfNulls(size)
        }
    }
}

data class Artist(
    val id: UUID,
    val name: String,
    val thumbnailUrl: String,
    val imageUrl: String,
    val userStarred: Boolean = false,
    val userRating: Double
) : Parcelable {
    constructor(parcel: Parcel) : this(
        UUID.fromString(parcel.readString()),
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readByte() != 0.toByte(),
        parcel.readDouble()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id.toString())
        parcel.writeString(name)
        parcel.writeString(thumbnailUrl)
        parcel.writeString(imageUrl)
        parcel.writeByte(if (userStarred) 1 else 0)
        parcel.writeDouble(userRating)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Artist> {
        override fun createFromParcel(parcel: Parcel): Artist {
            return Artist(parcel)
        }

        override fun newArray(size: Int): Array<Artist?> {
            return arrayOfNulls(size)
        }
    }
}

data class Album(
    val id: UUID,
    val name: String,
    val thumbnailUrl: String,
    val imageUrl: String,
    val releaseYear: Int,
    val userStarred: Boolean = false,
    val userRating: Double
) : Parcelable {
    constructor(parcel: Parcel) : this(
        UUID.fromString(parcel.readString()),
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readInt(),
        parcel.readByte() != 0.toByte(),
        parcel.readDouble()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id.toString())
        parcel.writeString(name)
        parcel.writeString(thumbnailUrl)
        parcel.writeString(imageUrl)
        parcel.writeInt(releaseYear)
        parcel.writeByte(if (userStarred) 1 else 0)
        parcel.writeDouble(userRating)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Album> {
        override fun createFromParcel(parcel: Parcel): Album {
            return Album(parcel)
        }

        override fun newArray(size: Int): Array<Album?> {
            return arrayOfNulls(size)
        }
    }
}

data class AuthResponse(
    val token: String,
    val user: User
)

data class User(
    val id: UUID,
    val email: String,
    val avatarThumbnailUrl: String,
    val avatarUrl: String,
    val userName: String
) 
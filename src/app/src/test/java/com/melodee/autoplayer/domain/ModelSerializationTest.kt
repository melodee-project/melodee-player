package com.melodee.autoplayer.domain

import android.os.Parcel
import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonParser
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.google.gson.stream.JsonToken
import com.melodee.autoplayer.data.api.ScrobbleRequest
import com.melodee.autoplayer.data.api.ScrobbleRequestType
import com.melodee.autoplayer.domain.model.Album
import com.melodee.autoplayer.domain.model.Artist
import com.melodee.autoplayer.domain.model.PaginationMetadata
import com.melodee.autoplayer.domain.model.Song
import com.melodee.autoplayer.domain.model.SongPagedResponse
import com.melodee.autoplayer.domain.model.User
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModelSerializationTest {
    private val gson = GsonBuilder()
        .registerTypeAdapter(UUID::class.java, object : TypeAdapter<UUID>() {
            override fun write(out: JsonWriter, value: UUID?) {
                out.value(value?.toString())
            }

            override fun read(reader: JsonReader): UUID? {
                return try {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                        return UUID(0, 0)
                    }
                    val str = reader.nextString()
                    if (str.isNullOrBlank()) {
                        UUID(0, 0)
                    } else {
                        UUID.fromString(str)
                    }
                } catch (e: IllegalArgumentException) {
                    UUID(0, 0)
                } catch (e: IllegalStateException) {
                    UUID(0, 0)
                }
            }
        })
        .create()

    @Test
    fun user_deserializes_with_uuid_and_new_fields() {
        val json = """
            {
              "id": "123e4567-e89b-12d3-a456-426614174000",
              "email": "user@example.com",
              "thumbnailUrl": "thumb",
              "imageUrl": "image",
              "username": "tester",
              "isAdmin": true,
              "isEditor": false,
              "roles": ["admin"],
              "songsPlayed": 10,
              "artistsLiked": 1,
              "artistsDisliked": 2,
              "albumsLiked": 3,
              "albumsDisliked": 4,
              "songsLiked": 5,
              "songsDisliked": 6,
              "createdAt": "2024-01-01T00:00:00Z",
              "updatedAt": "2024-02-01T00:00:00Z"
            }
        """.trimIndent()

        val user = gson.fromJson(json, User::class.java)

        assertThat(user.id.toString()).isEqualTo("123e4567-e89b-12d3-a456-426614174000")
        assertThat(user.isAdmin).isTrue()
        assertThat(user.roles).containsExactly("admin")
        assertThat(user.createdAt).isEqualTo("2024-01-01T00:00:00Z")
    }

    @Test
    fun song_serializes_and_deserializes_with_new_fields() {
        val artist = Artist(
            id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            name = "Artist",
            thumbnailUrl = "artistThumb",
            imageUrl = "artistImage",
            userStarred = true,
            userRating = 4,
            albumCount = 2,
            songCount = 10,
            createdAt = "2024-01-01",
            updatedAt = "2024-01-02",
            biography = "Bio",
            genres = listOf("rock", "jazz")
        )
        val album = Album(
            id = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
            name = "Album",
            thumbnailUrl = "albumThumb",
            imageUrl = "albumImage",
            releaseYear = 2024,
            userStarred = false,
            userRating = 5,
            artist = artist,
            songCount = 12,
            durationMs = 1234.0,
            durationFormatted = "3:21",
            createdAt = "2024-01-01",
            updatedAt = "2024-01-03",
            description = "Great album",
            genre = "rock"
        )
        val song = Song(
            id = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
            streamUrl = "stream",
            title = "Song",
            artist = artist,
            album = album,
            thumbnailUrl = "songThumb",
            imageUrl = "songImage",
            durationMs = 9999.0,
            durationFormatted = "5:00",
            userStarred = true,
            userRating = 5,
            songNumber = 1,
            bitrate = 320,
            playCount = 42,
            createdAt = "2024-01-01",
            updatedAt = "2024-01-04",
            genre = "rock"
        )

        val json = gson.toJson(song)
        val restored = gson.fromJson(json, Song::class.java)

        assertThat(json).contains("\"userRating\":5")
        assertThat(restored).isEqualTo(song)
        assertThat(restored.genre).isEqualTo("rock")
    }

    @Test
    fun paged_response_serializes_with_metadata() {
        val meta = PaginationMetadata(
            totalCount = 1,
            pageSize = 10,
            currentPage = 1,
            totalPages = 1,
            hasPrevious = false,
            hasNext = false
        )
        val song = Song(
            id = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
            streamUrl = "stream",
            title = "Song",
            artist = Artist(
                id = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                name = "Artist",
                thumbnailUrl = "",
                imageUrl = "",
                userStarred = false,
                userRating = 0
            ),
            album = Album(
                id = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                name = "Album",
                thumbnailUrl = "",
                imageUrl = "",
                releaseYear = 2024,
                userStarred = false,
                userRating = 0
            ),
            thumbnailUrl = "",
            imageUrl = "",
            durationMs = 1000.0,
            durationFormatted = "0:01"
        )
        val response = SongPagedResponse(meta = meta, data = listOf(song))

        val json = gson.toJson(response)
        val restored = gson.fromJson(json, SongPagedResponse::class.java)

        assertThat(restored.meta.totalCount).isEqualTo(1)
        assertThat(restored.data).hasSize(1)
    }

    @Test
    fun scrobble_request_type_serializes_to_int_value() {
        val request = ScrobbleRequest(
            songId = "song",
            playerName = "Player",
            scrobbleType = "played",
            timestamp = 1234.0,
            playedDuration = 50.0,
            scrobbleTypeValue = ScrobbleRequestType.PLAYED
        )

        val json = gson.toJson(request)
        val jsonObject = JsonParser.parseString(json).asJsonObject

        assertThat(jsonObject.get("scrobbleTypeValue").asInt).isEqualTo(2)
    }

    @Test
    fun song_parcelable_round_trip_preserves_new_fields() {
        val artist = Artist(
            id = UUID.fromString("01010101-0101-0101-0101-010101010101"),
            name = "Artist",
            thumbnailUrl = "thumb",
            imageUrl = "image",
            userStarred = true,
            userRating = 3,
            albumCount = 1,
            songCount = 2,
            createdAt = "created",
            updatedAt = "updated",
            biography = "bio",
            genres = listOf("rock")
        )
        val album = Album(
            id = UUID.fromString("02020202-0202-0202-0202-020202020202"),
            name = "Album",
            thumbnailUrl = "thumb",
            imageUrl = "image",
            releaseYear = 2024,
            userStarred = false,
            userRating = 4,
            artist = artist,
            songCount = 10,
            durationMs = 1000.0,
            durationFormatted = "0:01",
            createdAt = "created",
            updatedAt = "updated",
            description = "desc",
            genre = "genre"
        )
        val song = Song(
            id = UUID.fromString("03030303-0303-0303-0303-030303030303"),
            streamUrl = "stream",
            title = "Song",
            artist = artist,
            album = album,
            thumbnailUrl = "thumb",
            imageUrl = "image",
            durationMs = 1000.0,
            durationFormatted = "0:01",
            userStarred = true,
            userRating = 4,
            songNumber = 5,
            bitrate = 320,
            playCount = 7,
            createdAt = "created",
            updatedAt = "updated",
            genre = "genre"
        )

        val parcel = Parcel.obtain()
        song.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val restored = Song.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(restored).isEqualTo(song)
        assertThat(restored.playCount).isEqualTo(7)
    }

    @Test
    fun `user deserializes with null id returns empty uuid`() {
        val json = """
            {
              "id": null,
              "email": "user@example.com",
              "thumbnailUrl": "thumb",
              "imageUrl": "image",
              "username": "tester"
            }
        """.trimIndent()

        val user = gson.fromJson(json, User::class.java)

        assertThat(user.id).isEqualTo(UUID(0, 0))
    }

    @Test
    fun `song deserializes with invalid uuid format returns empty uuid`() {
        val json = """
            {
              "id": "not-a-valid-uuid",
              "streamUrl": "stream",
              "title": "Test Song",
              "artist": {
                "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "name": "Artist",
                "thumbnailUrl": "thumb",
                "imageUrl": "image",
                "userStarred": false,
                "userRating": 0
              },
              "album": {
                "id": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                "name": "Album",
                "thumbnailUrl": "thumb",
                "imageUrl": "image",
                "releaseYear": 2024,
                "userStarred": false,
                "userRating": 0
              },
              "thumbnailUrl": "thumb",
              "imageUrl": "image",
              "durationMs": 1000.0,
              "durationFormatted": "0:01"
            }
        """.trimIndent()

        val song = gson.fromJson(json, Song::class.java)

        assertThat(song.id).isEqualTo(UUID(0, 0))
    }

    @Test
    fun `pagination response with null song id handles gracefully`() {
        val json = """
            {
              "meta": {
                "totalCount": 1,
                "pageSize": 10,
                "currentPage": 1,
                "totalPages": 1,
                "hasPrevious": false,
                "hasNext": false
              },
              "data": [
                {
                  "id": null,
                  "streamUrl": "stream",
                  "title": "Song",
                  "artist": {
                    "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "name": "Artist",
                    "thumbnailUrl": "thumb",
                    "imageUrl": "image",
                    "userStarred": false,
                    "userRating": 0
                  },
                  "album": {
                    "id": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                    "name": "Album",
                    "thumbnailUrl": "thumb",
                    "imageUrl": "image",
                    "releaseYear": 2024,
                    "userStarred": false,
                    "userRating": 0
                  },
                  "thumbnailUrl": "thumb",
                  "imageUrl": "image",
                  "durationMs": 1000.0,
                  "durationFormatted": "0:01"
                }
              ]
            }
        """.trimIndent()

        val response = gson.fromJson(json, SongPagedResponse::class.java)

        assertThat(response.data).hasSize(1)
        assertThat(response.data[0].id).isEqualTo(UUID(0, 0))
    }
}

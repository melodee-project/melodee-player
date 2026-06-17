package com.melodee.autoplayer.api

import com.google.common.truth.Truth.assertThat
import com.melodee.autoplayer.data.api.MusicApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitPathTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: MusicApi

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(MusicApi::class.java)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `playlist songs endpoint uses correct path parameter`() = runBlocking {
        val playlistId = "abc123-def456-ghi789"

        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"meta": {"totalCount": 0, "pageSize": 10, "currentPage": 1, "totalPages": 0, "hasPrevious": false, "hasNext": false}, "data": []}
                """.trimIndent()
            )
        )

        try {
            api.getPlaylistSongs(playlistId, 1, 10)
        } catch (_: Exception) {
        }

        val request = mockWebServer.takeRequest()

        assertThat(request.path).contains("/api/v1/playlists/$playlistId/songs")
        assertThat(request.path).doesNotContain("{apiKey}")
        assertThat(request.path).doesNotContain("{id}")
    }

    @Test
    fun `refresh token endpoint uses v1 body endpoint`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "token": "access",
                  "serverVersion": "1.2.0",
                  "expiresAt": "2024-01-01T00:00:00Z",
                  "refreshToken": "refresh",
                  "refreshTokenExpiresAt": "2024-02-01T00:00:00Z",
                  "user": {
                    "id": "123e4567-e89b-12d3-a456-426614174000",
                    "thumbnailUrl": "",
                    "imageUrl": "",
                    "username": "tester",
                    "email": "tester@example.com",
                    "isAdmin": false,
                    "isEditor": false,
                    "roles": [],
                    "songsPlayed": 0,
                    "artistsLiked": 0,
                    "artistsDisliked": 0,
                    "albumsLiked": 0,
                    "albumsDisliked": 0,
                    "songsLiked": 0,
                    "songsDisliked": 0,
                    "createdAt": "",
                    "updatedAt": ""
                  }
                }
                """.trimIndent()
            )
        )

        try {
            api.refresh(com.melodee.autoplayer.domain.model.RefreshTokenRequest("refresh-token"))
        } catch (_: Exception) {
        }

        val request = mockWebServer.takeRequest()

        assertThat(request.path).isEqualTo("/api/v1/auth/refresh-token")
        assertThat(request.body.readUtf8()).contains("\"refreshToken\":\"refresh-token\"")
    }

    @Test
    fun `user playlists endpoint uses limit query parameter`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"meta": {"totalCount": 0, "pageSize": 25, "currentPage": 1, "totalPages": 0, "hasPrevious": false, "hasNext": false}, "data": []}
                """.trimIndent()
            )
        )

        try {
            api.getPlaylists(1, 25)
        } catch (_: Exception) {
        }

        val request = mockWebServer.takeRequest()

        assertThat(request.path).contains("/api/v1/user/playlists?page=1&limit=25")
        assertThat(request.path).doesNotContain("pageSize")
    }

    @Test
    fun `album songs endpoint does not send unsupported pagination query`() = runBlocking {
        val albumId = "album-uuid-789"

        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"meta": {"totalCount": 0, "pageSize": 50, "currentPage": 1, "totalPages": 0, "hasPrevious": false, "hasNext": false}, "data": []}
                """.trimIndent()
            )
        )

        try {
            api.getAlbumSongs(albumId)
        } catch (_: Exception) {
        }

        val request = mockWebServer.takeRequest()

        assertThat(request.path).isEqualTo("/api/v1/albums/$albumId/songs")
    }

    @Test
    fun `favorite song endpoint uses correct path parameters`() = runBlocking {
        val songId = "song-uuid-123"
        val isStarred = true

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        try {
            api.favoriteSong(songId, isStarred)
        } catch (_: Exception) {
        }

        val request = mockWebServer.takeRequest()

        assertThat(request.path).contains("/api/v1/songs/starred/$songId/true")
        assertThat(request.path).doesNotContain("{apiKey}")
        assertThat(request.path).doesNotContain("{id}")
        assertThat(request.path).doesNotContain("{isStarred}")
    }

    @Test
    fun `artist songs endpoint uses id parameter`() = runBlocking {
        val artistId = "artist-uuid-456"

        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"meta": {"totalCount": 0, "pageSize": 10, "currentPage": 1, "totalPages": 0, "hasPrevious": false, "hasNext": false}, "data": []}
                """.trimIndent()
            )
        )

        try {
            api.getArtistSongs(artistId, 1, 10)
        } catch (_: Exception) {
        }

        val request = mockWebServer.takeRequest()

        assertThat(request.path).contains("/api/v1/artists/$artistId/songs")
    }
}

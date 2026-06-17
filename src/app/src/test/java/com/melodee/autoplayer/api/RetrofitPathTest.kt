package com.melodee.autoplayer.api

import com.google.common.truth.Truth.assertThat
import com.melodee.autoplayer.data.api.MusicApi
import com.melodee.autoplayer.data.api.ScrobbleApi
import com.melodee.autoplayer.data.api.ScrobbleRequest
import com.melodee.autoplayer.data.api.ScrobbleRequestType
import com.melodee.autoplayer.data.api.toScrobbleResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.JsonParser
import com.melodee.autoplayer.data.api.ScrobbleResult
import com.melodee.autoplayer.domain.model.LoginModel
import java.lang.reflect.Method

class RetrofitPathTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var musicApi: MusicApi
    private lateinit var scrobbleApi: ScrobbleApi

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        musicApi = retrofit.create(MusicApi::class.java)
        scrobbleApi = retrofit.create(ScrobbleApi::class.java)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    private fun musicApiMethod(name: String): Method {
        return MusicApi::class.java.declaredMethods.first { it.name == name }
    }

    private fun requireGet(method: Method): GET {
        return method.getAnnotation(GET::class.java)
            ?: throw AssertionError("Missing @GET on ${method.name}")
    }

    private fun requirePost(method: Method): POST {
        return method.getAnnotation(POST::class.java)
            ?: throw AssertionError("Missing @POST on ${method.name}")
    }

    private fun requirePath(method: Method, parameterIndex: Int): Path {
        return method.parameterAnnotations[parameterIndex]
            .filterIsInstance<Path>()
            .firstOrNull()
            ?: throw AssertionError("Missing @Path on ${method.name} parameter $parameterIndex")
    }

    @Test
    fun `authenticate endpoint uses v1 authenticate path and request body shape`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "token": "access",
                  "serverVersion": "1.2.0",
                  "expiresAt": "2024-01-01T00:00:00Z",
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
            val requestBody = LoginModel(userName = "tester", email = "tester@example.com", password = "secret")
            musicApi.login(requestBody)
        } catch (_: Exception) {
        }

        val request = mockWebServer.takeRequest()
        val requestJson = JsonParser.parseString(request.body.readUtf8()).asJsonObject

        assertThat(request.path).isEqualTo("/api/v1/auth/authenticate")
        assertThat(requestJson.keySet()).containsExactly("userName", "email", "password")
        assertThat(requestJson.get("userName").asString).isEqualTo("tester")
        assertThat(requestJson.get("email").asString).isEqualTo("tester@example.com")
    }

    @Test
    fun `refresh-token endpoint uses v1 refresh path and nullable fields are accepted`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "token": "access",
                  "serverVersion": "1.2.0",
                  "expiresAt": "2024-01-01T00:00:00Z",
                  "refreshToken": null,
                  "refreshTokenExpiresAt": null,
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
            musicApi.refresh(com.melodee.autoplayer.domain.model.RefreshTokenRequest("refresh-token"))
        } catch (_: Exception) {
        }

        val request = mockWebServer.takeRequest()

        assertThat(request.path).isEqualTo("/api/v1/auth/refresh-token")
        assertThat(request.body.readUtf8()).contains("\"refreshToken\":\"refresh-token\"")
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
            musicApi.getPlaylistSongs(playlistId, 1, 10)
        } catch (_: Exception) {
        }

        val request = mockWebServer.takeRequest()

        assertThat(request.path).contains("/api/v1/playlists/$playlistId/songs")
        assertThat(request.path).contains("page=1")
        assertThat(request.path).contains("pageSize=10")
        assertThat(request.path).doesNotContain("{apiKey}")
    }

    @Test
    fun `playlist songs endpoint uses apiKey path placeholder`() {
        val method = musicApiMethod("getPlaylistSongs")
        val get = requireGet(method)
        val path = requirePath(method, 0)

        assertThat(get.value).isEqualTo("api/v1/playlists/{apiKey}/songs")
        assertThat(path.value).isEqualTo("apiKey")
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
            musicApi.refresh(com.melodee.autoplayer.domain.model.RefreshTokenRequest("refresh-token"))
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
            musicApi.getPlaylists(1, 25)
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
            musicApi.getAlbumSongs(albumId)
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
            musicApi.favoriteSong(songId, isStarred)
        } catch (_: Exception) {
        }

        val request = mockWebServer.takeRequest()

        assertThat(request.path).contains("/api/v1/songs/starred/$songId/true")
        assertThat(request.path).doesNotContain("{apiKey}")
        assertThat(request.path).doesNotContain("{id}")
        assertThat(request.path).doesNotContain("{isStarred}")
    }

    @Test
    fun `favorite song endpoint uses apiKey placeholder path`() {
        val method = musicApiMethod("favoriteSong")
        val post = requirePost(method)

        val pathParamSong = requirePath(method, 0)
        val pathParamStarred = requirePath(method, 1)

        assertThat(post.value).isEqualTo("api/v1/songs/starred/{apiKey}/{isStarred}")
        assertThat(pathParamSong.value).isEqualTo("apiKey")
        assertThat(pathParamStarred.value).isEqualTo("isStarred")
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
            musicApi.getArtistSongs(artistId, 1, 10)
        } catch (_: Exception) {
        }

        val request = mockWebServer.takeRequest()

        assertThat(request.path).contains("/api/v1/artists/$artistId/songs")
    }

    @Test
    fun `scrobble endpoint uses v1 path and request payload shape`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(204))

        val requestBody = ScrobbleRequest(
            songId = "song-uuid",
            playerName = "MelodeePlayer",
            scrobbleTypeValue = ScrobbleRequestType.PLAYED,
            timestamp = 1703462400.0,
            playedDuration = 42.0
        )

        try {
            scrobbleApi.scrobble(requestBody)
        } catch (_: Exception) {
        }

        val request = mockWebServer.takeRequest()
        val payload = JsonParser.parseString(request.body.readUtf8()).asJsonObject

        assertThat(request.path).isEqualTo("/api/v1/scrobble")
        assertThat(payload.get("songId").asString).isEqualTo("song-uuid")
        assertThat(payload.get("scrobbleType").asString).isEqualTo("played")
        assertThat(payload.get("scrobbleTypeValue").asInt).isEqualTo(2)
        assertThat(payload.get("timestamp").isJsonPrimitive).isTrue()
        assertThat(payload.get("playedDuration").isJsonPrimitive).isTrue()
    }

    @Test
    fun `scrobble error payload follows v1 error contract`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """
                {
                  "code": "invalid_request",
                  "message": "Invalid scrobble payload",
                  "correlationId": null
                }
                """.trimIndent()
            )
        )

        val response = try {
            scrobbleApi.scrobble(
                ScrobbleRequest(
                    songId = "song-uuid",
                    playerName = "MelodeePlayer",
                    scrobbleTypeValue = ScrobbleRequestType.NOW_PLAYING,
                    timestamp = 1703462400.0,
                    playedDuration = 0.0
                )
            )
        } catch (_: Exception) {
            throw AssertionError("Scrobble call should not throw; HTTP error should be surfaced via Response")
        }

        val result = response.toScrobbleResult()

        assertThat((result as? ScrobbleResult.Error)?.errorResponse?.code).isEqualTo("invalid_request")
        assertThat((result as? ScrobbleResult.Error)?.errorResponse?.message).isEqualTo("Invalid scrobble payload")
        assertThat((result as? ScrobbleResult.Error)?.errorResponse?.correlationId).isNull()
        assertThat((result as? ScrobbleResult.Error)?.httpStatus).isEqualTo(400)
    }
}

package com.melodee.autoplayer.data

import com.google.common.truth.Truth.assertThat
import com.melodee.autoplayer.data.api.MusicApi
import com.melodee.autoplayer.data.api.NetworkModule
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertThrows
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException

@RunWith(RobolectricTestRunner::class)
class NetworkModuleAuthRefreshTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var musicApi: MusicApi
    private var authFailureCount = 0

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        NetworkModule.setBaseUrl(mockWebServer.url("/").toString())
        NetworkModule.setTokens("", "")

        musicApi = NetworkModule.getMusicApi()
        authFailureCount = 0
        NetworkModule.setAuthenticationFailureCallback { authFailureCount++ }
    }

    @After
    fun tearDown() {
        NetworkModule.setTokens("", "")
        mockWebServer.shutdown()
        NetworkModule.setAuthenticationFailureCallback {}
    }

    @Test
    fun `transient refresh failure keeps stored auth tokens and does not clear session`() {
        NetworkModule.setTokens("access-token", "refresh-token")

        mockWebServer.enqueue(
            MockResponse().setResponseCode(401)
        )
        mockWebServer.enqueue(
            MockResponse().setResponseCode(502).setBody("temporary outage")
        )

        val failure = assertThrows(HttpException::class.java) { runBlocking { musicApi.getCurrentUser() } }
        assertThat(failure.code()).isEqualTo(401)

        val firstRequest = mockWebServer.takeRequest()
        val secondRequest = mockWebServer.takeRequest()

        assertThat(firstRequest.path).isEqualTo("/api/v1/user/me")
        assertThat(secondRequest.path).isEqualTo("/api/v1/auth/refresh-token")

        assertThat(NetworkModule.isAuthenticated()).isTrue()
        assertThat(authFailureCount).isEqualTo(0)
        assertThat(NetworkModule.getAuthToken()).isEqualTo("access-token")
    }

    @Test
    fun `invalid refresh clears auth and triggers auth failure handling`() {
        NetworkModule.setTokens("access-token", "refresh-token")

        mockWebServer.enqueue(
            MockResponse().setResponseCode(401)
        )
        mockWebServer.enqueue(
            MockResponse().setResponseCode(401).setBody("refresh expired")
        )

        val failure = assertThrows(HttpException::class.java) { runBlocking { musicApi.getCurrentUser() } }
        assertThat(failure.code()).isEqualTo(401)

        val firstRequest = mockWebServer.takeRequest()
        val secondRequest = mockWebServer.takeRequest()

        assertThat(firstRequest.path).isEqualTo("/api/v1/user/me")
        assertThat(secondRequest.path).isEqualTo("/api/v1/auth/refresh-token")

        assertThat(NetworkModule.isAuthenticated()).isFalse()
        assertThat(NetworkModule.getAuthToken()).isNull()
        assertThat(authFailureCount).isEqualTo(1)
    }
}

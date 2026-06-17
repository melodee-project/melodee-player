package com.melodee.autoplayer.service

import com.google.common.truth.Truth.assertThat
import com.melodee.autoplayer.data.AuthenticationManager
import com.melodee.autoplayer.data.SettingsManager
import com.melodee.autoplayer.data.api.NetworkModule
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MusicServiceAuthRestorationTest {
    private lateinit var settingsManager: SettingsManager
    private lateinit var authenticationManager: AuthenticationManager
    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication().applicationContext
        settingsManager = SettingsManager(context)
        settingsManager.serverUrl = ""
        settingsManager.authToken = ""
        settingsManager.refreshToken = ""
        settingsManager.userId = ""
        settingsManager.userEmail = ""
        settingsManager.username = ""
        settingsManager.userThumbnailUrl = ""
        settingsManager.userImageUrl = ""
        settingsManager.refreshTokenExpiresAt = ""

        settingsManager.saveAuthenticationData(
            token = "service-auth-token",
            userId = "user-1",
            userEmail = "user@example.com",
            username = "User",
            serverUrl = "https://server.example/",
            refreshToken = "service-refresh-token",
            refreshTokenExpiresAt = "2030-01-01T00:00:00Z"
        )

        authenticationManager = AuthenticationManager(context)
        NetworkModule.setBaseUrl("https://stale-server/")
        NetworkModule.clearAuthentication()
    }

    @After
    fun tearDown() {
        NetworkModule.clearAuthentication()
    }

    @Test
    fun `ensureAuthentication restores persisted auth from settings without requiring explicit login`() {
        val service = MusicService()
        val settingsField = MusicService::class.java.getDeclaredField("settingsManager")
        settingsField.isAccessible = true
        settingsField.set(service, settingsManager)

        val authManagerField = MusicService::class.java.getDeclaredField("authenticationManager")
        authManagerField.isAccessible = true
        authManagerField.set(service, authenticationManager)

        val ensureAuthMethod = MusicService::class.java.getDeclaredMethod("ensureAuthentication")
        ensureAuthMethod.isAccessible = true

        val restored = ensureAuthMethod.invoke(service) as Boolean

        assertThat(restored).isTrue()
        assertThat(NetworkModule.isAuthenticated()).isTrue()
        assertThat(NetworkModule.getAuthToken()).isEqualTo("service-auth-token")
        assertThat(NetworkModule.getAuthToken()).isNotEmpty()
    }
}

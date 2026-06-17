package com.melodee.autoplayer.data

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsManagerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication().applicationContext
        context.getSharedPreferences("MelodeePrefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("MelodeeSecurePrefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun saveAuthenticationData_preservesExistingRefreshTokenForSameUserWhenRefreshIsOmitted() {
        val settings = SettingsManager(context)

        settings.saveAuthenticationData(
            token = "access-1",
            userId = "user-1",
            userEmail = "user@example.com",
            username = "user",
            serverUrl = "https://server.example/",
            refreshToken = "refresh-1",
            refreshTokenExpiresAt = "2030-01-01T00:00:00Z"
        )

        settings.saveAuthenticationData(
            token = "access-2",
            userId = "user-1",
            userEmail = "user@example.com",
            username = "user",
            serverUrl = "https://server.example/"
        )

        assertThat(settings.authToken).isEqualTo("access-2")
        assertThat(settings.refreshToken).isEqualTo("refresh-1")
        assertThat(settings.refreshTokenExpiresAt).isEqualTo("2030-01-01T00:00:00Z")
    }

    @Test
    fun saveAuthenticationData_doesNotPreserveExistingRefreshTokenForDifferentUser() {
        val settings = SettingsManager(context)

        settings.saveAuthenticationData(
            token = "access-1",
            userId = "user-1",
            userEmail = "user1@example.com",
            username = "user1",
            serverUrl = "https://server.example/",
            refreshToken = "refresh-1",
            refreshTokenExpiresAt = "2030-01-01T00:00:00Z"
        )

        settings.saveAuthenticationData(
            token = "access-2",
            userId = "user-2",
            userEmail = "user2@example.com",
            username = "user2",
            serverUrl = "https://server.example/"
        )

        assertThat(settings.authToken).isEqualTo("access-2")
        assertThat(settings.refreshToken).isEmpty()
        assertThat(settings.refreshTokenExpiresAt).isEmpty()
    }

    @Test
    fun saveAuthenticationData_preservesRefreshExpiryForSameUserWhenRefreshTokenIsOmitted() {
        val settings = SettingsManager(context)

        settings.saveAuthenticationData(
            token = "access-1",
            userId = "user-1",
            userEmail = "user@example.com",
            username = "user",
            serverUrl = "https://server.example/",
            refreshToken = "refresh-1",
            refreshTokenExpiresAt = "2030-01-01T00:00:00Z"
        )

        settings.saveAuthenticationData(
            token = "access-2",
            userId = "user-1",
            userEmail = "user@example.com",
            username = "user",
            serverUrl = "https://server.example/"
        )

        assertThat(settings.authToken).isEqualTo("access-2")
        assertThat(settings.refreshToken).isEqualTo("refresh-1")
        assertThat(settings.refreshTokenExpiresAt).isEqualTo("2030-01-01T00:00:00Z")
    }
}

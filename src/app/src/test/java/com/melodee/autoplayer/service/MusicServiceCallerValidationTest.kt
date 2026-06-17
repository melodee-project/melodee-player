package com.melodee.autoplayer.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MusicServiceCallerValidationTest {
    @Test
    fun `trusted android auto packages are recognized by exact match`() {
        assertThat(AndroidAutoBrowserClientValidator.isTrustedAndroidAutoPackageName("com.google.android.projection.gearhead")).isTrue()
        assertThat(AndroidAutoBrowserClientValidator.isTrustedAndroidAutoPackageName("com.google.android.gms")).isTrue()
    }

    @Test
    fun `untrusted package names are not accepted by substring only matches`() {
        assertThat(AndroidAutoBrowserClientValidator.isTrustedAndroidAutoPackageName("com.example.android.auto")).isFalse()
        assertThat(AndroidAutoBrowserClientValidator.isTrustedAndroidAutoPackageName("com.malicious.gearhead.proxy")).isFalse()
        assertThat(AndroidAutoBrowserClientValidator.isTrustedAndroidAutoPackageName("com.google.android.projection.gearhead.debug")).isFalse()
        assertThat(AndroidAutoBrowserClientValidator.isTrustedAndroidAutoPackageName("com.example.melodee.autoplayer")).isFalse()
    }
}

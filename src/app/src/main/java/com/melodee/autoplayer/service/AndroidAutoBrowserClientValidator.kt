package com.melodee.autoplayer.service

internal object AndroidAutoBrowserClientValidator {
    private val exactTrustedAndroidAutoPackages = setOf(
        "com.google.android.projection.gearhead",
        "com.google.android.gms"
    )

    fun isTrustedAndroidAutoPackageName(clientPackageName: String): Boolean {
        return clientPackageName in exactTrustedAndroidAutoPackages
    }
}

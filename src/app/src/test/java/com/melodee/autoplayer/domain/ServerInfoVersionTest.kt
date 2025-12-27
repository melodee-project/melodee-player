package com.melodee.autoplayer.domain

import com.melodee.autoplayer.domain.model.ServerInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test suite to verify API version compatibility check.
 * Minimum required version: 1.2.0
 */
class ServerInfoVersionTest {

    @Test
    fun `version 1_2_0 should be compatible - minimum required version`() {
        val serverInfo = ServerInfo(
            majorVersion = 1,
            minorVersion = 2,
            patchVersion = 0
        )
        assertTrue("Version 1.2.0 should be compatible (minimum required)", serverInfo.isCompatibleVersion())
    }

    @Test
    fun `version 1_2_5 should be compatible - patch above minimum`() {
        val serverInfo = ServerInfo(
            majorVersion = 1,
            minorVersion = 2,
            patchVersion = 5
        )
        assertTrue("Version 1.2.5 should be compatible", serverInfo.isCompatibleVersion())
    }

    @Test
    fun `version 1_7_8 should be compatible - minor above minimum`() {
        val serverInfo = ServerInfo(
            majorVersion = 1,
            minorVersion = 7,
            patchVersion = 8
        )
        assertTrue("Version 1.7.8 should be compatible", serverInfo.isCompatibleVersion())
    }

    @Test
    fun `version 2_0_0 should be compatible - major above minimum`() {
        val serverInfo = ServerInfo(
            majorVersion = 2,
            minorVersion = 0,
            patchVersion = 0
        )
        assertTrue("Version 2.0.0 should be compatible", serverInfo.isCompatibleVersion())
    }

    @Test
    fun `version 8_3_0 should be compatible - much higher major version`() {
        val serverInfo = ServerInfo(
            majorVersion = 8,
            minorVersion = 3,
            patchVersion = 0
        )
        assertTrue("Version 8.3.0 should be compatible", serverInfo.isCompatibleVersion())
    }

    @Test
    fun `version 1_1_9 should not be compatible - below minimum`() {
        val serverInfo = ServerInfo(
            majorVersion = 1,
            minorVersion = 1,
            patchVersion = 9
        )
        assertFalse("Version 1.1.9 should not be compatible", serverInfo.isCompatibleVersion())
    }

    @Test
    fun `version 1_0_0 should not be compatible - below minimum`() {
        val serverInfo = ServerInfo(
            majorVersion = 1,
            minorVersion = 0,
            patchVersion = 0
        )
        assertFalse("Version 1.0.0 should not be compatible", serverInfo.isCompatibleVersion())
    }

    @Test
    fun `version 0_9_5 should not be compatible - major version too low`() {
        val serverInfo = ServerInfo(
            majorVersion = 0,
            minorVersion = 9,
            patchVersion = 5
        )
        assertFalse("Version 0.9.5 should not be compatible", serverInfo.isCompatibleVersion())
    }

    @Test
    fun `version 1_3_0 should be compatible - minor above minimum`() {
        val serverInfo = ServerInfo(
            majorVersion = 1,
            minorVersion = 3,
            patchVersion = 0
        )
        assertTrue("Version 1.3.0 should be compatible", serverInfo.isCompatibleVersion())
    }

    @Test
    fun `version 10_0_0 should be compatible - double digit major version`() {
        val serverInfo = ServerInfo(
            majorVersion = 10,
            minorVersion = 0,
            patchVersion = 0
        )
        assertTrue("Version 10.0.0 should be compatible", serverInfo.isCompatibleVersion())
    }
}

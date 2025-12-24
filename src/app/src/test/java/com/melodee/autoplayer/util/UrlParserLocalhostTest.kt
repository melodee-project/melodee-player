package com.melodee.autoplayer.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UrlParserLocalhostTest {

    @Test
    fun `normalizes emulator host with port`() {
        val normalized = UrlParser.normalizeServerUrl("http://10.0.2.2:5157")
        assertThat(normalized).isEqualTo("http://10.0.2.2:5157/")
        assertThat(UrlParser.isValidServerUrl("http://10.0.2.2:5157")).isTrue()
    }

    @Test
    fun `normalizes localhost with https default`() {
        val normalized = UrlParser.normalizeServerUrl("localhost")
        assertThat(normalized).isEqualTo("https://localhost/")
        assertThat(UrlParser.isValidServerUrl("localhost")).isTrue()
    }

    @Test
    fun `preserves explicit http on localhost`() {
        val normalized = UrlParser.normalizeServerUrl("http://localhost:8080/path")
        assertThat(normalized).isEqualTo("http://localhost:8080/")
        assertThat(UrlParser.isValidServerUrl("http://localhost:8080")).isTrue()
    }

    @Test
    fun `handles invalid input gracefully`() {
        val normalized = UrlParser.normalizeServerUrl("::")
        assertThat(normalized).isEqualTo("")
        assertThat(UrlParser.isValidServerUrl("::")).isFalse()
    }
}

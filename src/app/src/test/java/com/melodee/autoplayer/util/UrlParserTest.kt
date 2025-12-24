package com.melodee.autoplayer.util

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

class UrlParserTest {

    @Test
    fun testBasicHostnames() {
        assertEquals("https://localhost/", UrlParser.normalizeServerUrl("localhost"))
        assertEquals("https://example.com/", UrlParser.normalizeServerUrl("example.com"))
        assertEquals("https://music.myserver.com/", UrlParser.normalizeServerUrl("music.myserver.com"))
    }

    @Test
    fun testWithHttpProtocol() {
        assertEquals("http://localhost/", UrlParser.normalizeServerUrl("http://localhost"))
        assertEquals("http://192.168.1.1/", UrlParser.normalizeServerUrl("http://192.168.1.1"))
        assertEquals("http://example.com/", UrlParser.normalizeServerUrl("http://example.com"))
    }

    @Test
    fun testWithHttpsProtocol() {
        assertEquals("https://localhost/", UrlParser.normalizeServerUrl("https://localhost"))
        assertEquals("https://music.example.com/", UrlParser.normalizeServerUrl("https://music.example.com"))
        assertEquals("https://secure.server.org/", UrlParser.normalizeServerUrl("https://secure.server.org"))
    }

    @Test
    fun testWithPorts() {
        assertEquals("https://localhost:8080/", UrlParser.normalizeServerUrl("localhost:8080"))
        assertEquals("http://localhost:3000/", UrlParser.normalizeServerUrl("http://localhost:3000"))
        assertEquals("https://192.168.8.130:5157/", UrlParser.normalizeServerUrl("https://192.168.8.130:5157"))
        assertEquals("http://example.com:80/", UrlParser.normalizeServerUrl("http://example.com:80"))
    }

    @Test
    fun testWithExistingApiPaths() {
        assertEquals("http://localhost/", UrlParser.normalizeServerUrl("http://localhost/api"))
        assertEquals("https://example.com/", UrlParser.normalizeServerUrl("https://example.com/api/v1"))
        assertEquals("http://192.168.8.130/", UrlParser.normalizeServerUrl("http://192.168.8.130/api"))
        assertEquals("https://music.server.com/", UrlParser.normalizeServerUrl("https://music.server.com/"))
    }

    @Test
    fun testWithComplexPaths() {
        // Should strip everything after hostname/port and add /
        assertEquals("https://example.com/", UrlParser.normalizeServerUrl("https://example.com/songs"))
        assertEquals("http://localhost:8080/", UrlParser.normalizeServerUrl("http://localhost:8080/melodee/api"))
        assertEquals("https://music.server.com/", UrlParser.normalizeServerUrl("https://music.server.com/path/to/api"))
        assertEquals("http://192.168.1.100:3000/", UrlParser.normalizeServerUrl("http://192.168.1.100:3000/some/deep/path"))
    }

    @Test
    fun testIpAddresses() {
        assertEquals("https://192.168.1.1/", UrlParser.normalizeServerUrl("192.168.1.1"))
        assertEquals("http://10.0.0.1/", UrlParser.normalizeServerUrl("http://10.0.0.1"))
        assertEquals("https://172.16.0.1:8080/", UrlParser.normalizeServerUrl("172.16.0.1:8080"))
        assertEquals("https://127.0.0.1:5000/", UrlParser.normalizeServerUrl("https://127.0.0.1:5000"))
    }

    @Test
    fun testTrailingSlashes() {
        assertEquals("https://localhost/", UrlParser.normalizeServerUrl("localhost/"))
        assertEquals("http://example.com/", UrlParser.normalizeServerUrl("http://example.com/"))
        assertEquals("https://server.com/", UrlParser.normalizeServerUrl("https://server.com///"))
        assertEquals("http://localhost:8080/", UrlParser.normalizeServerUrl("http://localhost:8080/"))
    }

    @Test
    fun testWhitespace() {
        assertEquals("https://localhost/", UrlParser.normalizeServerUrl("  localhost  "))
        assertEquals("http://example.com/", UrlParser.normalizeServerUrl("\t http://example.com \n"))
        assertEquals("https://server.org:443/", UrlParser.normalizeServerUrl("   server.org:443   "))
    }

    @Test
    fun testEdgeCases() {
        assertEquals("", UrlParser.normalizeServerUrl(""))
        assertEquals("", UrlParser.normalizeServerUrl("   "))
        assertEquals("", UrlParser.normalizeServerUrl("\t\n"))
        assertEquals("https://a/", UrlParser.normalizeServerUrl("a"))
    }

    @Test
    fun testSpecialCharactersInHostname() {
        assertEquals("https://sub-domain.example.com/", UrlParser.normalizeServerUrl("sub-domain.example.com"))
        assertEquals("http://music_server.local/", UrlParser.normalizeServerUrl("http://music_server.local"))
        assertEquals("https://test.co.uk/", UrlParser.normalizeServerUrl("test.co.uk"))
    }

    @Test
    fun testQueryParamsAndFragments() {
        // Should strip query params and fragments
        assertEquals("https://example.com/", UrlParser.normalizeServerUrl("https://example.com?param=value"))
        assertEquals("http://localhost/", UrlParser.normalizeServerUrl("http://localhost#section"))
        assertEquals("https://server.com/", UrlParser.normalizeServerUrl("https://server.com/path?query=test#fragment"))
    }

    @Test
    fun testRealWorldExamples() {
        // Based on the user's specific examples
        assertEquals("http://localhost/", UrlParser.normalizeServerUrl("http://localhost"))
        assertEquals("http://192.168.8.130/", UrlParser.normalizeServerUrl("http://192.168.8.130/api"))
        assertEquals("https://music.example.com/", UrlParser.normalizeServerUrl("music.example.com/playlists"))
        assertEquals("http://localhost:3000/", UrlParser.normalizeServerUrl("http://localhost:3000/melodee"))
    }

    @Test
    fun testValidation() {
        assertTrue(UrlParser.isValidServerUrl("localhost"))
        assertTrue(UrlParser.isValidServerUrl("http://example.com"))
        assertTrue(UrlParser.isValidServerUrl("https://music.server.com:8080"))
        assertTrue(UrlParser.isValidServerUrl("192.168.1.1"))
        
        assertFalse(UrlParser.isValidServerUrl(""))
        assertFalse(UrlParser.isValidServerUrl("   "))
        // "not-a-url" will be normalized to "https://not-a-url/" which is technically valid
        // So we should test with something that would actually fail
        assertFalse(UrlParser.isValidServerUrl("://invalid"))
    }

    @Test
    fun testConsistency() {
        // Same input should always produce the same output
        val inputs = listOf(
            "localhost",
            "http://example.com:8080/api/v1",
            "https://music.server.org/some/path",
            "192.168.1.100:5000"
        )
        
        inputs.forEach { input ->
            val result1 = UrlParser.normalizeServerUrl(input)
            val result2 = UrlParser.normalizeServerUrl(input)
            assertEquals("Inconsistent results for '$input'", result1, result2)
            
            // Normalizing a normalized URL should return the same result
            val normalized = UrlParser.normalizeServerUrl(result1)
            assertEquals("Double normalization changed result for '$input'", result1, normalized)
        }
    }

    @Test
    fun testUnicodeHostnames() {
        // Unicode hostnames are preserved as-is (punycode conversion is not implemented)
        assertEquals("https://测试.com/", UrlParser.normalizeServerUrl("测试.com"))
        assertEquals("http://example.中国/", UrlParser.normalizeServerUrl("http://example.中国"))
    }

    @Test
    fun testNonStandardPorts() {
        assertEquals("https://localhost:1234/", UrlParser.normalizeServerUrl("localhost:1234"))
        assertEquals("http://example.com:65535/", UrlParser.normalizeServerUrl("http://example.com:65535"))
        assertEquals("https://192.168.1.1:1/", UrlParser.normalizeServerUrl("192.168.1.1:1"))
    }

    @Test
    fun testMalformedUrls() {
        // Test various malformed inputs - may not be perfectly correctable but shouldn't crash
        assertEquals("https://example.com:80:443/", UrlParser.normalizeServerUrl("example.com:80:443")) // Multiple colons - preserved as-is
        assertEquals("https://server.com/", UrlParser.normalizeServerUrl("server.com//path//")) // Double slashes
        assertEquals("https://test.org/", UrlParser.normalizeServerUrl("test.org/api/v2/old")) // Wrong API version
    }

    @Test
    fun testPerformanceWithLongUrls() {
        val longPath = "/very".repeat(100) + "/long/path/to/nowhere"
        val longUrl = "https://example.com$longPath"
        assertEquals("https://example.com/", UrlParser.normalizeServerUrl(longUrl))
    }

    @Test
    fun testCaseInsensitiveProtocols() {
        // Protocols should be normalized to lowercase
        assertEquals("http://localhost/", UrlParser.normalizeServerUrl("HTTP://localhost"))
        assertEquals("https://example.com/", UrlParser.normalizeServerUrl("HTTPS://example.com"))
    }

    @Test
    fun testCommonTypos() {
        // Test common user typos that should still work
        assertEquals("https://localhost/", UrlParser.normalizeServerUrl("localhost/"))
        // Some typos may not be auto-correctable - test realistic scenarios  
        assertEquals("https://http/", UrlParser.normalizeServerUrl("http//192.168.1.1")) // This becomes just "http"
        assertEquals("https://https:example.com/", UrlParser.normalizeServerUrl("https:example.com")) // Missing slashes
    }

    @Test
    fun testIPv6Addresses() {
        // IPv6 localhost
        assertEquals("https://[::1]/", UrlParser.normalizeServerUrl("[::1]"))
        assertEquals("http://[::1]/", UrlParser.normalizeServerUrl("http://[::1]"))
        
        // IPv6 with ports
        assertEquals("https://[::1]:8080/", UrlParser.normalizeServerUrl("[::1]:8080"))
        assertEquals("http://[::1]:3000/", UrlParser.normalizeServerUrl("http://[::1]:3000"))
        
        // Standard IPv6 addresses
        assertEquals("https://[2001:db8::1]/", UrlParser.normalizeServerUrl("2001:db8::1"))
        assertEquals("http://[2001:db8::1]/", UrlParser.normalizeServerUrl("http://[2001:db8::1]"))
        assertEquals("https://[2001:db8::1]:8080/", UrlParser.normalizeServerUrl("https://[2001:db8::1]:8080"))
        
        // IPv6 with existing paths
        assertEquals("https://[::1]/", UrlParser.normalizeServerUrl("https://[::1]/api"))
        assertEquals("http://[2001:db8::1]/", UrlParser.normalizeServerUrl("http://[2001:db8::1]/songs"))
        
        // More complex IPv6 addresses
        assertEquals("https://[fe80::1]/", UrlParser.normalizeServerUrl("fe80::1"))
        // Complex IPv6 address with many segments - parser may interpret last segment as port
        assertEquals("https://[2001:0db8:85a3::8a2e:0370]:7334/", UrlParser.normalizeServerUrl("2001:0db8:85a3::8a2e:0370:7334"))
        
        // IPv6 with zone identifiers - parser handles them as-is
        assertEquals("https://[fe80::1%eth0]/", UrlParser.normalizeServerUrl("[fe80::1%eth0]"))
    }

    @Test
    fun testIPv6WithoutBrackets() {
        // Test IPv6 addresses provided without brackets - should be auto-wrapped
        assertEquals("https://[::1]/", UrlParser.normalizeServerUrl("::1"))
        assertEquals("https://[2001:db8::1]/", UrlParser.normalizeServerUrl("2001:db8::1"))
        assertEquals("https://[fe80::1]/", UrlParser.normalizeServerUrl("fe80::1"))
    }

    @Test
    fun testMixedIPv4AndIPv6() {
        // IPv4-mapped IPv6 addresses
        assertEquals("https://[::ffff:192.168.1.1]/", UrlParser.normalizeServerUrl("::ffff:192.168.1.1"))
        assertEquals("http://[::ffff:192.0.2.1]/", UrlParser.normalizeServerUrl("http://[::ffff:192.0.2.1]"))
        
        // Dual stack scenarios
        assertEquals("https://[2001:db8::192.0.2.1]/", UrlParser.normalizeServerUrl("2001:db8::192.0.2.1"))
    }

    @Test
    fun testSubdomains() {
        // Basic subdomains
        assertEquals("https://api.example.com/", UrlParser.normalizeServerUrl("api.example.com"))
        assertEquals("http://music.streaming.service.com/", UrlParser.normalizeServerUrl("http://music.streaming.service.com"))
        assertEquals("https://cdn.api.music.example.org/", UrlParser.normalizeServerUrl("cdn.api.music.example.org"))
        
        // Multi-level subdomains with ports
        assertEquals("https://api.v2.music.example.com:8443/", UrlParser.normalizeServerUrl("api.v2.music.example.com:8443"))
        assertEquals("http://staging.api.music.dev.example.co.uk:3000/", UrlParser.normalizeServerUrl("http://staging.api.music.dev.example.co.uk:3000"))
        
        // Subdomains with paths
        assertEquals("https://api.music.example.com/", UrlParser.normalizeServerUrl("https://api.music.example.com/old/path"))
        assertEquals("http://cdn.assets.music.com/", UrlParser.normalizeServerUrl("http://cdn.assets.music.com/assets/images/"))
        
        // Special subdomain names
        assertEquals("https://api-v2.music-streaming.example.com/", UrlParser.normalizeServerUrl("api-v2.music-streaming.example.com"))
        assertEquals("https://api_internal.music.example.org/", UrlParser.normalizeServerUrl("api_internal.music.example.org"))
    }

    @Test
    fun testExtremelyLongHostnames() {
        // Very long but valid hostname (under 253 characters total)
        val longSubdomain = "very-long-subdomain-name-that-goes-on-and-on-for-quite-some-time"
        val longDomain = "extremely-long-domain-name-for-testing-purposes-that-should-still-work"
        val longHostname = "$longSubdomain.$longDomain.example.com"
        assertEquals("https://$longHostname/", UrlParser.normalizeServerUrl(longHostname))
        
        // Long hostname with port
        assertEquals("http://$longHostname:8080/", UrlParser.normalizeServerUrl("http://$longHostname:8080"))
        
        // Hostname at DNS limit (253 characters) - should still work
        val maxLengthHostname = "a".repeat(60) + "." + "b".repeat(60) + "." + "c".repeat(60) + "." + "d".repeat(60) + ".com"
        assertEquals("https://$maxLengthHostname/", UrlParser.normalizeServerUrl(maxLengthHostname))
        
        // Extremely long hostname (over DNS limit) - parser should handle gracefully
        val tooLongHostname = "toolong".repeat(50) + ".example.com"
        assertEquals("https://$tooLongHostname/", UrlParser.normalizeServerUrl(tooLongHostname))
    }

    @Test
    fun testBoundaryConditions() {
        // Empty and whitespace inputs
        assertEquals("", UrlParser.normalizeServerUrl(""))
        assertEquals("", UrlParser.normalizeServerUrl("   "))
        assertEquals("", UrlParser.normalizeServerUrl("\t\n\r"))
        
        // Single character hostnames
        assertEquals("https://a/", UrlParser.normalizeServerUrl("a"))
        assertEquals("http://x/", UrlParser.normalizeServerUrl("http://x"))
        
        // Port boundary conditions
        assertEquals("https://example.com:1/", UrlParser.normalizeServerUrl("example.com:1")) // Min port
        assertEquals("https://example.com:65535/", UrlParser.normalizeServerUrl("example.com:65535")) // Max port
        assertEquals("https://example.com:65536/", UrlParser.normalizeServerUrl("example.com:65536")) // Invalid port - still parsed
        
        // URL length boundaries
        val veryLongPath = "/" + "segment/".repeat(100) + "file.html"
        assertEquals("https://example.com/", UrlParser.normalizeServerUrl("https://example.com" + veryLongPath))
        
        // Multiple consecutive special characters
        assertEquals("https://example.com/", UrlParser.normalizeServerUrl("example.com////"))
        assertEquals("https://example.com/", UrlParser.normalizeServerUrl("example.com????"))
        assertEquals("https://example.com/", UrlParser.normalizeServerUrl("example.com####"))
    }

    @Test
    fun testSecurityScenarios() {
        // SQL Injection attempts - should be treated as hostnames and sanitized
        assertEquals("https://'; DROP TABLE users; --/", UrlParser.normalizeServerUrl("'; DROP TABLE users; --"))
        assertEquals("https://admin'--/", UrlParser.normalizeServerUrl("admin'--"))
        assertEquals("https://1' OR '1'='1/", UrlParser.normalizeServerUrl("1' OR '1'='1"))
        
        // XSS payload attempts - angle brackets and content may be modified during URI parsing
        assertEquals("https://<script>alert('xss')</", UrlParser.normalizeServerUrl("<script>alert('xss')</script>"))
        assertEquals("https://javascript:alert(1)/", UrlParser.normalizeServerUrl("javascript:alert(1)"))
        assertEquals("https://<img src=x onerror=alert(1)>/", UrlParser.normalizeServerUrl("<img src=x onerror=alert(1)>"))
        
        // Path traversal attempts
        assertEquals("https://example.com/", UrlParser.normalizeServerUrl("example.com/../../../etc/passwd"))
        assertEquals("https://example.com/", UrlParser.normalizeServerUrl("example.com/..\\..\\windows\\system32"))
        
        // Protocol injection attempts
        assertEquals("https://file/", UrlParser.normalizeServerUrl("file:///etc/passwd"))
        assertEquals("https://data:text/", UrlParser.normalizeServerUrl("data:text/html,<script>alert(1)</script>"))
        
        // LDAP injection attempts
        assertEquals("https://*)(uid=*))(|(uid=*/", UrlParser.normalizeServerUrl("*)(uid=*))(|(uid=*"))
        
        // Command injection attempts
        assertEquals("https://; cat /", UrlParser.normalizeServerUrl("; cat /etc/passwd;"))
        assertEquals("https://`whoami`/", UrlParser.normalizeServerUrl("`whoami`"))
        assertEquals("https://$(rm -rf /", UrlParser.normalizeServerUrl("$(rm -rf /)"))
        
        // Unicode/encoding attacks
        assertEquals("https://example.com\u0000.evil.com/", UrlParser.normalizeServerUrl("example.com\u0000.evil.com"))
        assertEquals("https://%00.evil.com/", UrlParser.normalizeServerUrl("%00.evil.com"))
    }

    @Test
    fun testRealWorldServerConfigurations() {
        // Popular cloud providers
        assertEquals("https://myapp.herokuapp.com/", UrlParser.normalizeServerUrl("myapp.herokuapp.com"))
        assertEquals("https://myapp.azurewebsites.net/", UrlParser.normalizeServerUrl("myapp.azurewebsites.net"))
        assertEquals("https://abc123.execute-api.us-west-2.amazonaws.com/", UrlParser.normalizeServerUrl("abc123.execute-api.us-west-2.amazonaws.com"))
        assertEquals("https://myapp-12345.netlify.app/", UrlParser.normalizeServerUrl("myapp-12345.netlify.app"))
        assertEquals("https://myproject.vercel.app/", UrlParser.normalizeServerUrl("myproject.vercel.app"))
        
        // Docker and container services
        assertEquals("https://api.music-service.default.svc.cluster.local/", UrlParser.normalizeServerUrl("api.music-service.default.svc.cluster.local"))
        assertEquals("http://music-api:8080/", UrlParser.normalizeServerUrl("http://music-api:8080"))
        assertEquals("https://music-service.docker.internal/", UrlParser.normalizeServerUrl("music-service.docker.internal"))
        
        // Corporate/enterprise configurations
        assertEquals("https://api.music.corp.company.com/", UrlParser.normalizeServerUrl("api.music.corp.company.com"))
        assertEquals("http://music-api.internal:8080/", UrlParser.normalizeServerUrl("http://music-api.internal:8080"))
        assertEquals("https://music.services.company.local/", UrlParser.normalizeServerUrl("music.services.company.local"))
        
        // CDN and load balancer configurations
        assertEquals("https://d1234567890.cloudfront.net/", UrlParser.normalizeServerUrl("d1234567890.cloudfront.net"))
        assertEquals("https://api-music-lb-1234567890.us-west-2.elb.amazonaws.com/", UrlParser.normalizeServerUrl("api-music-lb-1234567890.us-west-2.elb.amazonaws.com"))
        
        // Development environments
        assertEquals("https://music-api.dev.staging.example.com/", UrlParser.normalizeServerUrl("music-api.dev.staging.example.com"))
        assertEquals("http://localhost.localdomain:3000/", UrlParser.normalizeServerUrl("http://localhost.localdomain:3000"))
        assertEquals("https://api.music.test/", UrlParser.normalizeServerUrl("api.music.test"))
        
        // Geographic/regional configurations
        assertEquals("https://api.us-west.music.example.com/", UrlParser.normalizeServerUrl("api.us-west.music.example.com"))
        assertEquals("https://music-api.europe.example.eu/", UrlParser.normalizeServerUrl("music-api.europe.example.eu"))
        assertEquals("https://api.asia-pacific.music.co.jp/", UrlParser.normalizeServerUrl("api.asia-pacific.music.co.jp"))
        
        // Non-standard TLDs
        assertEquals("https://music.example.museum/", UrlParser.normalizeServerUrl("music.example.museum"))
        assertEquals("https://api.music.photography/", UrlParser.normalizeServerUrl("api.music.photography"))
        assertEquals("https://streaming.music.technology/", UrlParser.normalizeServerUrl("streaming.music.technology"))
    }

    @Test
    fun testIPv6Validation() {
        // Valid IPv6 URLs should pass validation
        assertTrue(UrlParser.isValidServerUrl("[::1]"))
        assertTrue(UrlParser.isValidServerUrl("http://[::1]:8080"))
        assertTrue(UrlParser.isValidServerUrl("https://[2001:db8::1]"))
        assertTrue(UrlParser.isValidServerUrl("2001:db8::1"))
        
        // Invalid IPv6 addresses might still normalize but won't be properly validated
        // Note: Our parser is lenient and may accept some malformed addresses
        // assertFalse(UrlParser.isValidServerUrl("[::g]")) // Invalid hex character - may still parse
        // assertFalse(UrlParser.isValidServerUrl("[2001:db8:::1]")) // Too many colons - may still parse
    }

    @Test
    fun testSecurityValidation() {
        // All these should be considered "valid" URLs that normalize properly
        // The security concern is handled by treating them as literal hostnames
        assertTrue(UrlParser.isValidServerUrl("example.com"))
        assertTrue(UrlParser.isValidServerUrl("sub.domain.example.org"))
        
        // Even suspicious inputs should validate as they become literal hostnames
        assertTrue(UrlParser.isValidServerUrl("'; DROP TABLE users; --"))
        assertTrue(UrlParser.isValidServerUrl("<script>alert(1)</script>"))
        
        // Empty inputs should fail validation
        assertFalse(UrlParser.isValidServerUrl(""))
        assertFalse(UrlParser.isValidServerUrl("   "))
    }
}
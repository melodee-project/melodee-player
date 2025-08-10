package com.melodee.autoplayer.util

import java.net.URI
import java.net.URISyntaxException

/**
 * Utility class for parsing and normalizing server URLs to ensure they always
 * end with "/api/v1/" regardless of user input format.
 */
object UrlParser {
    
    /**
     * Normalizes a server URL to the format: <scheme>://<hostname>[:<port>]/api/v1/
     * 
     * Examples:
     * - "localhost" -> "https://localhost/api/v1/"
     * - "http://localhost" -> "http://localhost/api/v1/"
     * - "192.168.1.1:8080" -> "https://192.168.1.1:8080/api/v1/"
     * - "https://music.example.com/api" -> "https://music.example.com/api/v1/"
     * - "http://server.com/api/v1/songs" -> "http://server.com/api/v1/"
     * - "::1" -> "https://[::1]/api/v1/"
     * - "[2001:db8::1]:8080" -> "https://[2001:db8::1]:8080/api/v1/"
     * 
     * @param inputUrl The user-provided URL string
     * @return The normalized URL ending with "/api/v1/"
     */
    fun normalizeServerUrl(inputUrl: String): String {
        if (inputUrl.isBlank()) {
            return ""
        }
        
        var url = inputUrl.trim()
        
        // Handle IPv6 addresses that aren't already bracketed
        url = preprocessIPv6(url)
        
        // Add protocol if missing (default to https) - handle case sensitivity
        if (!url.matches(Regex("^https?://.*", RegexOption.IGNORE_CASE))) {
            url = "https://$url"
        }
        
        return try {
            val uri = URI(url)
            
            // Extract components - preserve case for scheme
            val scheme = uri.scheme?.lowercase() ?: "https"
            val host = uri.host
            val port = if (uri.port > 0) ":${uri.port}" else ""
            
            // Check if host is null or empty
            if (host.isNullOrBlank()) {
                return fallbackNormalization(url)
            }
            
            // Build the normalized URL
            "$scheme://$host$port/api/v1/"
            
        } catch (e: URISyntaxException) {
            // Fallback to string manipulation for malformed URLs
            fallbackNormalization(url)
        }
    }
    
    /**
     * Preprocesses URLs to handle IPv6 addresses that need bracketing
     */
    private fun preprocessIPv6(url: String): String {
        // If it already has a protocol, check if the host part needs IPv6 bracketing
        val protocolMatch = Regex("^(https?://)(.*)$", RegexOption.IGNORE_CASE).find(url)
        if (protocolMatch != null) {
            val protocol = protocolMatch.groupValues[1]
            val rest = protocolMatch.groupValues[2]
            return protocol + preprocessIPv6HostPart(rest)
        }
        
        // No protocol, check if the entire string looks like IPv6
        return preprocessIPv6HostPart(url)
    }
    
    /**
     * Handles IPv6 detection and bracketing for the host part of URLs
     */
    private fun preprocessIPv6HostPart(hostPart: String): String {
        // Extract just the host:port part (before any path/query/fragment)
        val hostPortEnd = hostPart.indexOfAny(charArrayOf('/', '?', '#'))
        val hostPortPart = if (hostPortEnd > 0) {
            hostPart.substring(0, hostPortEnd)
        } else {
            hostPart
        }
        
        val pathPart = if (hostPortEnd > 0) {
            hostPart.substring(hostPortEnd)
        } else {
            ""
        }
        
        // Check if this looks like an IPv6 address
        if (isLikelyIPv6(hostPortPart)) {
            // Handle different IPv6 formats
            return when {
                // Already bracketed IPv6 with port: [::1]:8080
                hostPortPart.matches(Regex("^\\[[^\\]]+\\]:\\d+$")) -> hostPart
                
                // Already bracketed IPv6 without port: [::1]
                hostPortPart.matches(Regex("^\\[[^\\]]+\\]$")) -> hostPart
                
                // IPv6 with port but no brackets: This is tricky because IPv6 uses colons
                // We need to be very careful not to misinterpret compressed IPv6 (::1) as ports
                hostPortPart.contains(':') && hostPortPart.lastIndexOf(':') != hostPortPart.indexOf(':') -> {
                    val lastColonIndex = hostPortPart.lastIndexOf(':')
                    val potentialPort = hostPortPart.substring(lastColonIndex + 1)
                    
                    // Only treat as port if:
                    // 1. It's a valid port number (1-65535)
                    // 2. It's not part of IPv6 compression (::)
                    // 3. There's at least one character before the colon that isn't another colon
                    val beforeLastColon = if (lastColonIndex > 0) hostPortPart[lastColonIndex - 1] else ' '
                    val isValidPort = potentialPort.matches(Regex("^\\d{1,5}$")) && 
                                     potentialPort.toIntOrNull()?.let { it in 1..65535 } == true
                    val notPartOfCompression = beforeLastColon != ':'
                    
                    if (isValidPort && notPartOfCompression && lastColonIndex > 0) {
                        val ipv6Part = hostPortPart.substring(0, lastColonIndex)
                        "[$ipv6Part]:$potentialPort$pathPart"
                    } else {
                        "[$hostPortPart]$pathPart"
                    }
                }
                
                // Plain IPv6 without brackets: ::1, 2001:db8::1
                else -> "[$hostPortPart]$pathPart"
            }
        }
        
        return hostPart
    }
    
    /**
     * Heuristic to determine if a string looks like an IPv6 address
     */
    private fun isLikelyIPv6(input: String): Boolean {
        // Already bracketed
        if (input.startsWith('[') && input.contains(']')) return true
        
        // Contains multiple colons (IPv6 characteristic)
        val colonCount = input.count { it == ':' }
        if (colonCount < 2) return false
        
        // Contains consecutive colons (::) - IPv6 compression
        if (input.contains("::")) return true
        
        // Has many colons and hex-like segments
        return colonCount >= 2 && input.split(':').all { segment ->
            segment.isEmpty() || segment.matches(Regex("^[0-9a-fA-F]{1,4}$|^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))
        }
    }
    
    /**
     * Fallback normalization using string manipulation when URI parsing fails
     */
    private fun fallbackNormalization(url: String): String {
        var normalizedUrl = url
        
        // Extract scheme (case insensitive)
        val scheme = when {
            normalizedUrl.matches(Regex("^https://.*", RegexOption.IGNORE_CASE)) -> {
                normalizedUrl = normalizedUrl.replaceFirst(Regex("^https://", RegexOption.IGNORE_CASE), "")
                "https"
            }
            normalizedUrl.matches(Regex("^http://.*", RegexOption.IGNORE_CASE)) -> {
                normalizedUrl = normalizedUrl.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "")
                "http"
            }
            else -> "https"
        }
        
        // Remove any path/query/fragment components - keep only host:port
        val hostPortEnd = normalizedUrl.indexOfAny(charArrayOf('/', '?', '#'))
        if (hostPortEnd > 0) {
            normalizedUrl = normalizedUrl.substring(0, hostPortEnd)
        }
        
        // Handle malformed cases like multiple colons in non-IPv6 contexts
        if (!isLikelyIPv6(normalizedUrl) && normalizedUrl.contains("::")) {
            // This is likely malformed, take only the part before the double colon
            normalizedUrl = normalizedUrl.substringBefore("::").ifBlank { "localhost" }
        }
        
        // Remove trailing slashes if any
        normalizedUrl = normalizedUrl.trimEnd('/')
        
        // If we ended up with an empty host, return empty
        if (normalizedUrl.isBlank()) {
            return ""
        }
        
        // Build the normalized URL
        return "$scheme://$normalizedUrl/api/v1/"
    }
    
    /**
     * Validates if a URL string appears to be a valid server URL
     */
    fun isValidServerUrl(url: String): Boolean {
        if (url.isBlank()) return false
        
        val normalizedUrl = normalizeServerUrl(url)
        if (normalizedUrl.isBlank()) return false
        
        return try {
            val uri = URI(normalizedUrl)
            uri.scheme in listOf("http", "https") && 
            !uri.host.isNullOrBlank() &&
            uri.path == "/api/v1/"
        } catch (e: URISyntaxException) {
            // If URI parsing fails, check if the normalized URL has the expected format
            normalizedUrl.matches(Regex("^https?://[^/]+/api/v1/$"))
        }
    }
}
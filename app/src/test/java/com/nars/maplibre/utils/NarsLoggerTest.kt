package com.nars.maplibre.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NarsLoggerTest {
    private val jwtHeader = "eyJhbGciOiJIUzI1NiJ9"
    private val jwtPayload = "eyJzdWIiOiIxIn0"
    private val jwt = "$jwtHeader.$jwtPayload.abc123def"

    @Test
    fun `redacts password in JSON login body`() {
        val sanitized = NarsLogger.sanitizeMessage("""{"username":"field1","password":"S3cret!Pass"}""")
        assertFalse(sanitized.contains("S3cret!Pass"))
        assertTrue(sanitized.contains("""password":"[REDACTED]"""))
    }

    @Test
    fun `redacts password containing commas and braces in JSON`() {
        val sanitized = NarsLogger.sanitizeMessage("""{"password":"a,b}c","username":"u"}""")
        assertFalse(sanitized.contains("a,b}c"))
        assertTrue(sanitized.contains("""password":"[REDACTED]"""))
    }

    @Test
    fun `redacts password with escaped quote in JSON`() {
        val sanitized = NarsLogger.sanitizeMessage("""{"password":"ab\"cd"}""")
        assertFalse(sanitized.contains("ab"))
        assertTrue(sanitized.contains("""password":"[REDACTED]"""))
    }

    @Test
    fun `redacts full JWT in login response body`() {
        val body = """{"success":true,"token":"$jwt","user":{"id":"1"}}"""
        val sanitized = NarsLogger.sanitizeMessage(body)
        assertFalse(sanitized.contains(jwtHeader))
        assertFalse(sanitized.contains(jwtPayload))
        assertFalse(sanitized.contains("abc123def"))
        assertTrue(sanitized.contains("""token":"[REDACTED]"""))
    }

    @Test
    fun `redacts accessToken and refreshToken JSON keys`() {
        val body = """{"accessToken":"$jwt","refreshToken":"other-value","user":{"id":"1"}}"""
        val sanitized = NarsLogger.sanitizeMessage(body)
        assertFalse(sanitized.contains(jwt))
        assertFalse(sanitized.contains("other-value"))
        assertTrue(sanitized.contains("""accessToken":"[REDACTED]"""))
        assertTrue(sanitized.contains("""refreshToken":"[REDACTED]"""))
    }

    @Test
    fun `redacts apiKey and session id in JSON`() {
        val body = """{"apiKey":"ak-123456","sessionId":"sess-99"}"""
        val sanitized = NarsLogger.sanitizeMessage(body)
        assertFalse(sanitized.contains("ak-123456"))
        assertFalse(sanitized.contains("sess-99"))
    }

    @Test
    fun `redacts full JWT in Bearer authorization header`() {
        val sanitized = NarsLogger.sanitizeMessage("Authorization: Bearer $jwt")
        assertFalse(sanitized.contains(jwtHeader))
        assertFalse(sanitized.contains(jwtPayload))
        assertFalse(sanitized.contains("abc123def"))
        assertTrue(sanitized.contains("Bearer [REDACTED]"))
    }

    @Test
    fun `redacts opaque bearer token`() {
        val sanitized = NarsLogger.sanitizeMessage("Authorization: Bearer opaqueToken123")
        assertFalse(sanitized.contains("opaqueToken123"))
        assertTrue(sanitized.contains("Bearer [REDACTED]"))
    }

    @Test
    fun `redacts tokens in cookie header`() {
        val sanitized = NarsLogger.sanitizeMessage("Cookie: access_token=$jwt; refresh_token=other")
        assertFalse(sanitized.contains(jwtHeader))
        assertFalse(sanitized.contains(jwtPayload))
        assertFalse(sanitized.contains("refresh_token=other"))
        assertTrue(sanitized.contains("cookie=[REDACTED]", ignoreCase = true))
        assertTrue(sanitized.contains("refresh_token=[REDACTED]"))
    }

    @Test
    fun `redacts unquoted form values`() {
        val sanitized = NarsLogger.sanitizeMessage("username=field1&password=Str0ngPass")
        assertFalse(sanitized.contains("Str0ngPass"))
        assertTrue(sanitized.contains("password=[REDACTED]"))
    }

    @Test
    fun `leaves non-sensitive content untouched`() {
        val body = """{"type":"road","label":"Rue de la Paix","coordinates":[{"lat":36.7,"lng":3.0}]}"""
        assertEquals(body, NarsLogger.sanitizeMessage(body))
    }

    @Test
    fun `does not redact token-like feature content`() {
        val body = """{"label":"hasToken true"}"""
        assertEquals(body, NarsLogger.sanitizeMessage(body))
    }
}

package com.nars.maplibre.security

import android.content.SharedPreferences
import com.nars.maplibre.data.model.User
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Reversible fake so JVM tests can observe the encrypt/decrypt boundary. */
private object FakeCipher : ValueCipher {
    const val UNREADABLE_PAYLOAD = "!!unreadable!!"

    override fun encrypt(plainText: String): String = plainText.reversed()

    override fun decrypt(payload: String): String = if (payload == UNREADABLE_PAYLOAD) {
        throw IllegalStateException("simulated keystore failure")
    } else {
        payload.reversed()
    }
}

class SecurePreferencesTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setup() {
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        every { prefs.edit() } returns editor
    }

    private fun createSecurePrefs() = SecurePreferences(prefs, FakeCipher)

    @Test
    fun `saveAuthToken writes ENCRYPTED token under the auth key`() {
        createSecurePrefs().saveAuthToken("tok-123")

        verify { editor.putString("auth_token", FakeCipher.encrypt("tok-123")) }
        verify { editor.apply() }
    }

    @Test
    fun `getAuthToken decrypts the stored value`() {
        every { prefs.getString("auth_token", null) } returns FakeCipher.encrypt("tok-123")

        assertEquals("tok-123", createSecurePrefs().getAuthToken())
    }

    @Test
    fun `getAuthToken returns null when absent`() {
        every { prefs.getString("auth_token", null) } returns null

        assertNull(createSecurePrefs().getAuthToken())
    }

    @Test
    fun `getAuthToken degrades to null when decryption fails instead of crashing`() {
        every { prefs.getString("auth_token", null) } returns FakeCipher.UNREADABLE_PAYLOAD

        assertNull(createSecurePrefs().getAuthToken())
    }

    @Test
    fun `refresh token is encrypted under its own key`() {
        createSecurePrefs().saveRefreshToken("ref-1")

        verify { editor.putString("refresh_token", FakeCipher.encrypt("ref-1")) }

        every { prefs.getString("refresh_token", null) } returns FakeCipher.encrypt("ref-1")
        assertEquals("ref-1", createSecurePrefs().getRefreshToken())
    }

    @Test
    fun `clearAuthToken removes only the auth key`() {
        createSecurePrefs().clearAuthToken()

        verify(exactly = 1) { editor.remove(any()) }
        verify { editor.remove("auth_token") }
    }

    @Test
    fun `saveUser serializes and encrypts user json`() {
        val user =
            User(
                id = "u1",
                username = "ali",
                name = "Ali",
                communeLatitude = 36.8,
                communeLongitude = 3.05,
            )

        createSecurePrefs().saveUser(user)

        val expectedJson = """{"id":"u1","username":"ali","name":"Ali""""
        verify {
            editor.putString(
                "user",
                match { payload ->
                    val decrypted = FakeCipher.decrypt(payload)
                    decrypted.contains(expectedJson)
                },
            )
        }
    }

    @Test
    fun `getUser decrypts and deserializes stored user`() {
        val storedJson = """{"id":"u1","username":"ali","name":"Ali","role":"commune_user"}"""
        every { prefs.getString("user", null) } returns FakeCipher.encrypt(storedJson)

        val user = createSecurePrefs().getUser()

        assertEquals("ali", user!!.username)
        assertEquals("u1", user.id)
    }

    @Test
    fun `getUser tolerates unknown json fields`() {
        val storedJson = """{"id":"u2","username":"sara","name":"Sara","futureField":42}"""
        every { prefs.getString("user", null) } returns FakeCipher.encrypt(storedJson)

        assertEquals("sara", createSecurePrefs().getUser()!!.username)
    }

    @Test
    fun `getUser returns null on corrupted json instead of crashing`() {
        every { prefs.getString("user", null) } returns FakeCipher.encrypt("{not-json")

        assertNull(createSecurePrefs().getUser())
    }

    @Test
    fun `getUser returns null when decryption fails`() {
        every { prefs.getString("user", null) } returns FakeCipher.UNREADABLE_PAYLOAD

        assertNull(createSecurePrefs().getUser())
    }

    @Test
    fun `getUser returns null when key absent`() {
        every { prefs.getString("user", null) } returns null

        assertNull(createSecurePrefs().getUser())
    }

    @Test
    fun `hasUser reflects presence not content`() {
        every { prefs.contains("user") } returns true
        assertTrue(createSecurePrefs().hasUser())

        every { prefs.contains("user") } returns false
        assertFalse(createSecurePrefs().hasUser())
    }

    @Test
    fun `municipality is encrypted under its own key`() {
        createSecurePrefs().saveMunicipalityName("Bab El Oued")
        verify { editor.putString("municipality", FakeCipher.encrypt("Bab El Oued")) }

        every { prefs.getString("municipality", null) } returns FakeCipher.encrypt("Bab El Oued")
        assertEquals("Bab El Oued", createSecurePrefs().getMunicipalityName())
    }

    @Test
    fun `clearAll removes exactly the four session keys`() {
        createSecurePrefs().clearAll()

        verify(exactly = 4) { editor.remove(any()) }
        listOf("auth_token", "refresh_token", "user", "municipality").forEach { key ->
            verify { editor.remove(key) }
        }
    }

    @Test
    fun `passthrough cipher is an identity function`() {
        // Documents the fallback contract: memory-only store never persists,
        // so identity encryption is acceptable there.
        assertEquals("secret", PassthroughCipher.encrypt("secret"))
        assertEquals("secret", PassthroughCipher.decrypt("secret"))
    }
}

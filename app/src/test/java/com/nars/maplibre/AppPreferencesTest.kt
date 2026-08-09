package com.nars.maplibre

import android.content.SharedPreferences
import com.nars.maplibre.data.model.BaseLayerType
import com.nars.maplibre.data.model.User
import com.nars.maplibre.security.SecurePreferences
import com.nars.maplibre.ui.theme.ThemeMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppPreferencesTest {
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val securePrefs: SecurePreferences = mockk(relaxed = true)
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setUp() {
        appPreferences = AppPreferences(prefs, securePrefs)
    }

    @Test
    fun `themeMode defaults to AUTO when unset`() {
        every { prefs.getString("theme", ThemeMode.AUTO.name) } returns null

        assertEquals(ThemeMode.AUTO, appPreferences.themeMode)
    }

    @Test
    fun `themeMode falls back to AUTO on invalid stored value`() {
        every { prefs.getString("theme", ThemeMode.AUTO.name) } returns "NEON"

        assertEquals(ThemeMode.AUTO, appPreferences.themeMode)
    }

    @Test
    fun `themeMode reads stored value and updates flow on set`() {
        every { prefs.getString("theme", ThemeMode.AUTO.name) } returns ThemeMode.DARK.name

        assertEquals(ThemeMode.DARK, appPreferences.themeMode)

        appPreferences.themeMode = ThemeMode.LIGHT

        assertEquals(ThemeMode.LIGHT, appPreferences.themeModeFlow.value)
        verify { prefs.edit() }
    }

    @Test
    fun `baseLayer reads stored value and falls back to SATELLITE`() {
        every { prefs.getString("base_layer", BaseLayerType.SATELLITE.name) } returns
            BaseLayerType.STREET.name

        assertEquals(BaseLayerType.STREET, appPreferences.baseLayer)

        every { prefs.getString("base_layer", BaseLayerType.SATELLITE.name) } returns "MERCATOR"
        assertEquals(BaseLayerType.SATELLITE, appPreferences.baseLayer)

        appPreferences.baseLayer = BaseLayerType.LIGHT
        verify { prefs.edit() }
    }

    @Test
    fun `currentPhase round-trips`() {
        every { prefs.getString("current_phase", null) } returns "phase-1"

        assertEquals("phase-1", appPreferences.currentPhase)

        appPreferences.currentPhase = "phase-2"
        every { prefs.getString("current_phase", null) } returns "phase-2"
        assertEquals("phase-2", appPreferences.currentPhase)
    }

    @Test
    fun `authToken delegates to secure preferences`() {
        every { securePrefs.getAuthToken() } returns "token-1"

        assertEquals("token-1", appPreferences.authToken)

        appPreferences.authToken = "token-2"
        verify { securePrefs.saveAuthToken("token-2") }

        appPreferences.authToken = null
        verify { securePrefs.clearAuthToken() }
    }

    @Test
    fun `refreshToken delegates to secure preferences`() {
        every { securePrefs.getRefreshToken() } returns "refresh-1"

        assertEquals("refresh-1", appPreferences.refreshToken)

        appPreferences.refreshToken = "refresh-2"
        verify { securePrefs.saveRefreshToken("refresh-2") }

        appPreferences.refreshToken = null
        verify { securePrefs.clearRefreshToken() }
    }

    @Test
    fun `user delegates to secure preferences`() {
        val user = User(username = "user-1", name = "User One")
        every { securePrefs.getUser() } returns user

        assertEquals(user, appPreferences.user)

        val other = User(username = "user-2", name = "User Two")
        appPreferences.user = other
        verify { securePrefs.saveUser(other) }

        appPreferences.user = null
        verify { securePrefs.clearUser() }
    }

    @Test
    fun `municipalityName delegates to secure preferences`() {
        every { securePrefs.getMunicipalityName() } returns "Algiers"

        assertEquals("Algiers", appPreferences.municipalityName)

        appPreferences.municipalityName = "Oran"
        verify { securePrefs.saveMunicipalityName("Oran") }

        appPreferences.municipalityName = null
        verify { securePrefs.clearMunicipalityName() }
    }

    @Test
    fun `isLoggedIn requires a stored user and an auth token`() {
        every { securePrefs.hasUser() } returns true
        every { securePrefs.getAuthToken() } returns "token-1"

        assertTrue(appPreferences.isLoggedIn)

        every { securePrefs.getAuthToken() } returns null
        assertFalse(appPreferences.isLoggedIn)

        every { securePrefs.hasUser() } returns false
        assertFalse(appPreferences.isLoggedIn)
    }

    @Test
    fun `new instance has no current phase by default`() {
        every { prefs.getString("current_phase", null) } returns null

        assertNull(appPreferences.currentPhase)
    }
}

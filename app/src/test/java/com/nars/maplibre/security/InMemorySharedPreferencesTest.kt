package com.nars.maplibre.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InMemorySharedPreferencesTest {
    private lateinit var prefs: InMemorySharedPreferences

    @Before
    fun setup() {
        prefs = InMemorySharedPreferences()
    }

    @Test
    fun `string round trip`() {
        prefs.edit().putString("k", "v").apply()

        assertEquals("v", prefs.getString("k", null))
        assertTrue(prefs.contains("k"))
    }

    @Test
    fun `missing key returns default`() {
        assertNull(prefs.getString("missing", null))
        assertEquals("def", prefs.getString("missing", "def"))
        assertEquals(7, prefs.getInt("missing", 7))
        assertEquals(8L, prefs.getLong("missing", 8L))
        assertTrue(prefs.getBoolean("missing", true))
        assertFalse(prefs.contains("missing"))
    }

    @Test
    fun `remove deletes the entry`() {
        prefs.edit().putString("k", "v").apply()
        prefs.edit().remove("k").apply()

        assertNull(prefs.getString("k", null))
        assertFalse(prefs.contains("k"))
    }

    @Test
    fun `commit persists like apply`() {
        val committed = prefs.edit().putBoolean("flag", true).commit()

        assertTrue(committed)
        assertTrue(prefs.getBoolean("flag", false))
    }

    @Test
    fun `batched editor applies all operations atomically in order`() {
        prefs.edit().apply {
            putString("a", "1")
            remove("a")
            putString("b", "2")
        }.apply()

        assertFalse(prefs.contains("a"))
        assertEquals("2", prefs.getString("b", null))
    }

    @Test
    fun `clear wipes every entry`() {
        prefs.edit().putString("a", "1").putString("b", "2").apply()
        prefs.edit().clear().apply()

        assertFalse(prefs.contains("a"))
        assertFalse(prefs.contains("b"))
    }

    @Test
    fun `getAll returns a defensive copy`() {
        prefs.edit().putString("a", "1").apply()

        val all = prefs.all
        assertEquals("1", all["a"])

        prefs.edit().remove("a").apply()
        assertEquals("1", all["a"])
    }
}

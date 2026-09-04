package com.nars.maplibre.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ─── User helper functions ─────────────────────────────────────────────

    @Test
    fun `getInitials uppercases first character of username`() {
        assertEquals("J", User(username = "john_doe", name = "John").getInitials())
        assertEquals("A", User(username = "alice", name = "Alice").getInitials())
        assertEquals("B", User(username = "b", name = "Bob").getInitials())
    }

    @Test
    fun `getInitials falls back to U for empty username`() {
        assertEquals("U", User(username = "", name = "Empty").getInitials())
    }

    @Test
    fun `hasCommuneLocation true only when both lat and lng present`() {
        assertTrue(User(username = "u", name = "U", communeLatitude = 1.0, communeLongitude = 2.0).hasCommuneLocation())
        assertFalse(User(username = "u", name = "U", communeLatitude = 1.0).hasCommuneLocation())
        assertFalse(User(username = "u", name = "U", communeLongitude = 2.0).hasCommuneLocation())
        assertFalse(User(username = "u", name = "U").hasCommuneLocation())
    }

    @Test
    fun `User defaults to commune_user role and empty id`() {
        val user = User(username = "u", name = "U")
        assertEquals("", user.id)
        assertEquals("commune_user", user.role)
        assertNull(user.email)
        assertNull(user.communeName)
    }

    // ─── LoginResponse / LoginApiResponse serialization ────────────────────

    @Test
    fun `LoginResponse serializes and deserializes`() {
        val user = User(id = "1", username = "u", name = "U", email = "u@x.com")
        val response = LoginResponse(user = user, token = "tok", municipalityName = "City")

        val decoded =
            json.decodeFromString(
                LoginResponse.serializer(),
                json.encodeToString(LoginResponse.serializer(), response),
            )
        assertEquals(user, decoded.user)
        assertEquals("tok", decoded.token)
        assertEquals("City", decoded.municipalityName)
    }

    @Test
    fun `LoginApiResponse defaults success to true`() {
        val apiUser = LoginApiUser(username = "u", name = "U")
        val response = LoginApiResponse(user = apiUser)
        assertTrue(response.success)
        val decoded =
            json.decodeFromString(
                LoginApiResponse.serializer(),
                """{"user":{"username":"u","name":"U"}}""",
            )
        assertTrue(decoded.success)
    }

    @Test
    fun `LoginApiResponse carries access token and message`() {
        val apiUser = LoginApiUser(username = "u", name = "U")
        val response = LoginApiResponse(user = apiUser, accessToken = "acc", message = "hi")
        assertEquals("acc", response.accessToken)
        assertEquals("hi", response.message)
    }

    @Test
    fun `LoginApiUser defaults id email and role`() {
        val apiUser = LoginApiUser(username = "u", name = "U")
        assertEquals("", apiUser.id)
        assertEquals("commune_user", apiUser.role)
        assertNull(apiUser.email)
        assertNull(apiUser.commune)
    }

    // ─── LoginApiCommune ───────────────────────────────────────────────────

    @Test
    fun `LoginApiCommune deserializes name_fr serial name`() {
        val decoded =
            json.decodeFromString(
                LoginApiCommune.serializer(),
                """{"latitude":1.5,"longitude":2.5,"name_fr":"Bruxelles"}""",
            )
        assertEquals(1.5, decoded.latitude!!, 0.0001)
        assertEquals(2.5, decoded.longitude!!, 0.0001)
        assertEquals("Bruxelles", decoded.nameFr)
    }

    @Test
    fun `toUserFields maps commune fields into a User`() {
        val apiUser = LoginApiUser(id = "9", username = "u", name = "U", email = "e@x.com", role = "field_worker")
        val commune = LoginApiCommune(latitude = 33.5, longitude = -7.6, nameFr = "Casablanca")

        val user = commune.toUserFields(apiUser)

        assertEquals("9", user.id)
        assertEquals("u", user.username)
        assertEquals("U", user.name)
        assertEquals("e@x.com", user.email)
        assertEquals("field_worker", user.role)
        assertEquals(33.5, user.communeLatitude!!, 0.0001)
        assertEquals(-7.6, user.communeLongitude!!, 0.0001)
        assertEquals("Casablanca", user.communeName)
    }

    @Test
    fun `toUserFields with null commune yields null location fields`() {
        val apiUser = LoginApiUser(username = "u", name = "U")
        val user = LoginApiCommune().toUserFields(apiUser)

        assertNull(user.communeLatitude)
        assertNull(user.communeLongitude)
        assertNull(user.communeName)
        assertFalse(user.hasCommuneLocation())
    }
}

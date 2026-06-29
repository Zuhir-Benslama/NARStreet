package com.nars.maplibre.security

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nars.maplibre.data.model.User
import com.nars.maplibre.utils.NarsLogger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SecurePreferences(context: Context) {
    @Suppress("DEPRECATION")
    private val masterKey: MasterKey by lazy {
        MasterKey
            .Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    @Suppress("DEPRECATION")
    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun saveAuthToken(token: String) {
        encryptedPrefs.edit {
            putString(KEY_AUTH_TOKEN, token)
        }
    }

    fun getAuthToken(): String? = encryptedPrefs.getString(KEY_AUTH_TOKEN, null)

    fun clearAuthToken() {
        encryptedPrefs.edit {
            remove(KEY_AUTH_TOKEN)
        }
    }

    fun saveCookie(cookie: String) {
        encryptedPrefs.edit {
            putString(KEY_COOKIE, cookie)
        }
    }

    fun getCookie(): String? = encryptedPrefs.getString(KEY_COOKIE, null)

    fun clearCookie() {
        encryptedPrefs.edit {
            remove(KEY_COOKIE)
        }
    }

    fun saveUser(user: User) {
        val userJson = json.encodeToString(user)
        encryptedPrefs.edit {
            putString(KEY_USER, userJson)
        }
    }

    fun getUser(): User? {
        val userJson = encryptedPrefs.getString(KEY_USER, null) ?: return null
        return try {
            json.decodeFromString(User.serializer(), userJson)
        } catch (e: kotlinx.serialization.SerializationException) {
            NarsLogger.w("SecurePreferences", "Failed to deserialize user", e)
            null
        }
    }

    fun clearUser() {
        encryptedPrefs.edit {
            remove(KEY_USER)
        }
    }

    fun saveMunicipalityName(name: String) {
        encryptedPrefs.edit {
            putString(KEY_MUNICIPALITY, name)
        }
    }

    fun getMunicipalityName(): String? = encryptedPrefs.getString(KEY_MUNICIPALITY, null)

    fun clearMunicipalityName() {
        encryptedPrefs.edit {
            remove(KEY_MUNICIPALITY)
        }
    }

    fun clearAll() {
        encryptedPrefs.edit {
            remove(KEY_AUTH_TOKEN)
            remove(KEY_COOKIE)
            remove(KEY_USER)
            remove(KEY_MUNICIPALITY)
        }
    }

    companion object {
        private const val PREFS_NAME = "nars_secure_prefs"

        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_COOKIE = "session_cookie"
        private const val KEY_USER = "user"
        private const val KEY_MUNICIPALITY = "municipality"
    }
}

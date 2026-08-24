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
import java.io.File

/**
 * Credential storage: sensitive strings are encrypted with a Keystore-backed
 * AEAD cipher ([AndroidKeystoreAeadCipher]) before being written to an ordinary
 * [SharedPreferences] file. This replaces the deprecated
 * androidx.security.crypto.EncryptedSharedPreferences while keeping the same
 * security properties (hardware key, AES-256-GCM, per-value random IV).
 *
 * Resilience policy (unchanged in spirit from the previous implementation):
 * - A corrupt/unreadable value decrypts to "absent" (logged), never crashes.
 * - If the Keystore itself is unavailable on a device, the store degrades to
 *   memory-only so nothing is ever persisted in the clear; the app stays
 *   usable for the session.
 *
 * The underlying collaborators are injectable for JVM unit tests.
 */
@Suppress("TooManyFunctions")
class SecurePreferences internal constructor(
    private val prefs: SharedPreferences,
    private val cipher: ValueCipher,
) {
    constructor(context: Context) : this(createSecureStore(context))

    internal constructor(store: SecureStore) : this(store.prefs, store.cipher)

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val lock = Any()

    fun saveAuthToken(token: String) = putEncrypted(KEY_AUTH_TOKEN, token)

    fun getAuthToken(): String? = getDecrypted(KEY_AUTH_TOKEN)

    fun clearAuthToken() = removeKey(KEY_AUTH_TOKEN)

    fun saveRefreshToken(token: String) = putEncrypted(KEY_REFRESH_TOKEN, token)

    fun getRefreshToken(): String? = getDecrypted(KEY_REFRESH_TOKEN)

    fun clearRefreshToken() = removeKey(KEY_REFRESH_TOKEN)

    fun saveUser(user: User) = synchronized(lock) {
        val userJson = json.encodeToString(user)
        prefs.edit { putString(KEY_USER, cipher.encrypt(userJson)) }
    }

    fun getUser(): User? = synchronized(lock) {
        val storedValue = prefs.getString(KEY_USER, null) ?: return null
        val userJson =
            try {
                cipher.decrypt(storedValue)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                NarsLogger.w(TAG, "User entry unreadable — treating as absent", e)
                return null
            }
        try {
            json.decodeFromString(User.serializer(), userJson)
        } catch (e: kotlinx.serialization.SerializationException) {
            NarsLogger.w(TAG, "Failed to deserialize user", e)
            null
        }
    }

    fun clearUser() = removeKey(KEY_USER)

    fun hasUser(): Boolean = synchronized(lock) {
        prefs.contains(KEY_USER)
    }

    fun saveMunicipalityName(name: String) = putEncrypted(KEY_MUNICIPALITY, name)

    fun getMunicipalityName(): String? = getDecrypted(KEY_MUNICIPALITY)

    fun clearMunicipalityName() = removeKey(KEY_MUNICIPALITY)

    fun clearAll() = synchronized(lock) {
        prefs.edit {
            remove(KEY_AUTH_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_USER)
            remove(KEY_MUNICIPALITY)
        }
    }

    private fun putEncrypted(key: String, plainText: String) = synchronized(lock) {
        prefs.edit { putString(key, cipher.encrypt(plainText)) }
    }

    /**
     * Reads and decrypts a value. Any decryption failure (tampered data,
     * keystore key rotated/lost after a device restore) degrades to null with
     * a warning instead of crashing — callers treat it as logged-out.
     */
    private fun getDecrypted(key: String): String? = synchronized(lock) {
        val storedValue = prefs.getString(key, null) ?: return null
        try {
            cipher.decrypt(storedValue)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            NarsLogger.w(TAG, "Entry '$key' unreadable — treating as absent", e)
            null
        }
    }

    private fun removeKey(key: String) = synchronized(lock) {
        prefs.edit { remove(key) }
    }

    /**
     * One-time upgrade path from the deprecated EncryptedSharedPreferences
     * store. Copies any surviving credentials into the new encrypted store
     * (without clobbering newer values), then deletes the legacy file. An
     * unreadable legacy store is discarded — the user simply logs in again.
     */
    @Suppress("DEPRECATION")
    private fun migrateFromLegacyStore(context: Context) {
        val legacyFile = File(context.applicationInfo.dataDir, LEGACY_PREFS_FILE)
        if (!legacyFile.exists()) return

        NarsLogger.i(TAG, "Migrating credentials from deprecated encrypted store")
        try {
            val masterKey =
                MasterKey
                    .Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            val legacyPrefs =
                EncryptedSharedPreferences.create(
                    context,
                    LEGACY_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            copyIfTargetMissing(legacyPrefs, KEY_AUTH_TOKEN, ::getAuthToken, ::saveAuthToken)
            copyIfTargetMissing(legacyPrefs, KEY_REFRESH_TOKEN, ::getRefreshToken, ::saveRefreshToken)
            copyIfTargetMissing(legacyPrefs, KEY_MUNICIPALITY, ::getMunicipalityName, ::saveMunicipalityName)
            if (!hasUser()) {
                legacyPrefs.getString(KEY_USER, null)?.let { legacyJson ->
                    runCatching { json.decodeFromString(User.serializer(), legacyJson) }
                        .getOrNull()
                        ?.let(::saveUser)
                }
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            NarsLogger.w(TAG, "Legacy credential store unreadable — discarding it", e)
        }
        // The legacy store has been superseded either way.
        context.deleteSharedPreferences(LEGACY_PREFS_NAME)
    }

    private fun copyIfTargetMissing(
        legacyPrefs: SharedPreferences,
        key: String,
        current: () -> String?,
        save: (String) -> Unit,
    ) {
        if (current() != null) return
        legacyPrefs.getString(key, null)?.let(save)
    }

    companion object {
        private const val TAG = "SecurePreferences"

        /** New store: ordinary prefs whose values are ciphertext. */
        private const val PREFS_NAME = "nars_secure_prefs_v2"
        private const val LEGACY_PREFS_NAME = "nars_secure_prefs"
        private const val LEGACY_PREFS_FILE = "shared_prefs/$LEGACY_PREFS_NAME.xml"

        private const val KEY_ALIAS = "nars_credentials_key"

        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER = "user"
        private const val KEY_MUNICIPALITY = "municipality"

        internal fun createSecureStore(context: Context): SecureStore {
            val cipher =
                try {
                    AndroidKeystoreAeadCipher(KEY_ALIAS).apply { initialize() }
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    NarsLogger.e(
                        TAG,
                        "Keystore unavailable on this device — using memory-only credential " +
                            "storage (credentials will not persist)",
                        e,
                    )
                    return SecureStore(InMemorySharedPreferences(), PassthroughCipher)
                }
            return SecureStore(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), cipher)
                .also { SecurePreferences(it).migrateFromLegacyStore(context) }
        }
    }
}

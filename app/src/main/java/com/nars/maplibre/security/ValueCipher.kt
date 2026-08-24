package com.nars.maplibre.security

/**
 * Symmetric string encryption used by [SecurePreferences] to protect
 * credentials at rest.
 *
 * Implementations must be safe for concurrent use. Production uses
 * [AndroidKeystoreAeadCipher]; JVM tests substitute a reversible fake.
 */
internal interface ValueCipher {
    /** Returns an opaque payload that [decrypt] can reverse. */
    fun encrypt(plainText: String): String

    /**
     * Reverses [encrypt]. Throws on tampered/unreadable input or when the
     * underlying key is unavailable — callers decide how to degrade.
     */
    fun decrypt(payload: String): String
}

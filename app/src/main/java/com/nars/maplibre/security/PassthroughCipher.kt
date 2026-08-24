package com.nars.maplibre.security

/**
 * Identity cipher used ONLY as a last-resort pairing with
 * [InMemorySharedPreferences] when the Keystore is unavailable: values stay in
 * RAM for the session and are never written to disk, so storing them unsealed
 * never puts plaintext at rest.
 */
internal object PassthroughCipher : ValueCipher {
    override fun encrypt(plainText: String): String = plainText

    override fun decrypt(payload: String): String = payload
}

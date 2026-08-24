package com.nars.maplibre.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.nars.maplibre.utils.NarsLogger
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encryption with the key held in the hardware-backed Android
 * Keystore (non-exportable). Replaces the deprecated
 * androidx.security.crypto.EncryptedSharedPreferences: same primitives
 * (Keystore + AES/GCM), but built on stable public APIs.
 *
 * Payload format: Base64(IV[12] || ciphertext+tag). A fresh random IV is used
 * per encryption, so identical plaintexts never produce identical payloads.
 */
internal class AndroidKeystoreAeadCipher(private val keyAlias: String) : ValueCipher {
    companion object {
        private const val TAG = "KeystoreAeadCipher"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val KEY_SIZE_BITS = 256
    }

    /** Loaded lazily — Keystore access can be slow (~ms) and must stay off the UI thread. */
    private val key: SecretKey by lazy { loadOrGenerateKey() }

    /**
     * Forces key material creation so Keystore failures surface deterministically
     * at construction time (see SecurePreferences.createSecureStore) instead of
     * on the first read/write.
     */
    fun initialize() {
        key
    }

    private fun loadOrGenerateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing =
            (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing

        NarsLogger.d(TAG, "Generating new Keystore AES-GCM key")
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val sealed = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + sealed, Base64.NO_WRAP)
    }

    override fun decrypt(payload: String): String {
        val data = Base64.decode(payload, Base64.NO_WRAP)
        require(data.size > IV_SIZE_BYTES) { "Ciphertext too short" }
        val iv = data.copyOfRange(0, IV_SIZE_BYTES)
        val sealed = data.copyOfRange(IV_SIZE_BYTES, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(sealed), Charsets.UTF_8)
    }
}

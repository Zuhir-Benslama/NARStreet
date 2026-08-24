package com.nars.maplibre.security

import android.content.SharedPreferences

/**
 * The two collaborators a [SecurePreferences] instance is built from.
 * Exists so the context-based factory can hand both over through a single
 * secondary-constructor delegation expression.
 */
internal class SecureStore(val prefs: SharedPreferences, val cipher: ValueCipher)

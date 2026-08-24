package com.nars.maplibre.security

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

/**
 * Non-persisting [SharedPreferences] used as a last-resort fallback when the
 * encrypted store is unrecoverable (see [SecurePreferences]): credentials stay
 * valid for the current process only and never touch disk.
 *
 * Implements only what SecurePreferences needs plus sane defaults for the
 * remaining accessors.
 */
internal class InMemorySharedPreferences : SharedPreferences {
    private val values = ConcurrentHashMap<String, Any?>()

    private inner class EditorImpl : SharedPreferences.Editor {
        private val operations = mutableListOf<(MutableMap<String, Any?>) -> Unit>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply {
            operations += { it[key] = value }
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            operations += { it[key] = values }
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply {
            operations += { it[key] = value }
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply {
            operations += { it[key] = value }
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply {
            operations += { it[key] = value }
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply {
            operations += { it[key] = value }
        }

        override fun remove(key: String): SharedPreferences.Editor = apply {
            operations += { it.remove(key) }
        }

        override fun clear(): SharedPreferences.Editor = apply {
            operations += { it.clear() }
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            synchronized(this@InMemorySharedPreferences) {
                operations.forEach { it(values) }
            }
        }
    }

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun getAll(): MutableMap<String, *> = synchronized(this) { HashMap(values) }

    override fun getString(key: String, defValue: String?): String? =
        synchronized(this) { values[key] as? String ?: defValue }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        synchronized(this) { values[key] as? MutableSet<String> ?: defValues }

    override fun getInt(key: String, defValue: Int): Int = synchronized(this) { values[key] as? Int ?: defValue }

    override fun getLong(key: String, defValue: Long): Long = synchronized(this) { values[key] as? Long ?: defValue }

    override fun getFloat(key: String, defValue: Float): Float =
        synchronized(this) { values[key] as? Float ?: defValue }

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        synchronized(this) { values[key] as? Boolean ?: defValue }

    override fun contains(key: String): Boolean = synchronized(this) { values.containsKey(key) }

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit
}

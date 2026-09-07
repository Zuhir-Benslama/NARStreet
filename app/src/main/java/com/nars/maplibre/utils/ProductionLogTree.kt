package com.nars.maplibre.utils

import android.annotation.SuppressLint
import android.util.Log
import timber.log.Timber

/**
 * Release-build log sink. Only warnings, errors and wtf reach logcat — the
 * severity floor documented by [NarsLogger]. Messages are already sanitized
 * (tokens, passwords, cookies redacted) before they arrive here.
 */
@SuppressLint("LogNotTimber")
internal class ProductionLogTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

    override fun log(priority: Int, t: String?, message: String, throwable: Throwable?) {
        if (!isLoggable(t, priority)) return
        val logTag = t ?: NarsLogger.DEFAULT_TAG
        when (priority) {
            Log.ASSERT -> Log.wtf(logTag, message, throwable)
            Log.ERROR -> Log.e(logTag, message, throwable)
            Log.WARN -> Log.w(logTag, message, throwable)
            else -> Unit
        }
    }
}

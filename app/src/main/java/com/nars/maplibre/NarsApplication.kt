package com.nars.maplibre

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.geoman.maplibre.geoman.GeomanLogger
import com.nars.maplibre.data.api.SessionTokens
import com.nars.maplibre.di.appModule
import com.nars.maplibre.utils.NarsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import timber.log.Timber

class NarsApplication :
    Application(),
    KoinComponent {
    companion object {
        private const val TAG = "NarsApplication"
        private const val SESSION_RESTORE_MAX_RETRIES = 5
        private const val SESSION_RESTORE_RETRY_BASE_DELAY_MS = 100L
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Monotonic counter bumped on every foreground/background transition. A
     * token restore launched for one transition must never apply tokens after
     * a newer transition has cleared them — a fast background→foreground→
     * background sequence would otherwise leave tokens resident in memory
     * while the app sits in the background, defeating the whole point of the
     * clearing policy.
     */
    @Volatile private var lifecycleEpoch = 0

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        GeomanLogger.delegate =
            object : GeomanLogger.Delegate {
                override fun d(tag: String, message: String) {
                    NarsLogger.d(tag, message)
                }

                override fun e(tag: String, message: String, throwable: Throwable?) {
                    NarsLogger.e(tag, message, throwable)
                }

                override fun w(tag: String, message: String, throwable: Throwable?) {
                    NarsLogger.w(tag, message, throwable)
                }
            }
        startKoin {
            androidContext(this@NarsApplication)
            modules(appModule)
        }
        MapLibre.getInstance(this, null, WellKnownTileServer.MapTiler)

        applicationScope.launch {
            repeat(SESSION_RESTORE_MAX_RETRIES) { attempt ->
                try {
                    val prefs: AppPreferences = get()
                    val tokens: SessionTokens = get()
                    prefs.authToken?.let { token ->
                        tokens.setSessionToken(token)
                        NarsLogger.d(TAG, "User session found on startup")
                    }
                    prefs.refreshToken?.let { token ->
                        tokens.setRefreshToken(token)
                    }
                    return@launch
                } catch (e: IllegalStateException) {
                    NarsLogger.w(TAG, "Session check attempt ${attempt + 1} failed: Koin not ready", e)
                    if (attempt < SESSION_RESTORE_MAX_RETRIES - 1) {
                        kotlinx.coroutines.delay(SESSION_RESTORE_RETRY_BASE_DELAY_MS * (attempt + 1))
                    }
                }
            }
            NarsLogger.w(TAG, "Session restoration failed after $SESSION_RESTORE_MAX_RETRIES attempts")
        }

        registerTokenClearingOnBackground()
    }

    private fun registerTokenClearingOnBackground() {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> onAppBackgrounded()
                Lifecycle.Event.ON_START -> onAppForegrounded()
                else -> Unit
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
    }

    private fun onAppBackgrounded() {
        lifecycleEpoch++
        try {
            val tokens: SessionTokens = get()
            // clearInMemoryTokens takes SessionTokens' token lock, so the clear
            // is atomic against concurrent refresh/login cookie writes and can
            // never leave a half-cleared state.
            tokens.clearInMemoryTokens()
            NarsLogger.d(TAG, "In-memory tokens cleared (app backgrounded)")
        } catch (e: IllegalStateException) {
            NarsLogger.w(TAG, "Token clearing skipped: Koin not ready", e)
        }
    }

    private fun onAppForegrounded() {
        val epoch = ++lifecycleEpoch
        applicationScope.launch {
            try {
                val tokens: SessionTokens = get()
                val prefs: AppPreferences = get()
                // Slow part first: EncryptedSharedPreferences decrypts lazily
                // (~100ms). Read everything before touching the token fields.
                val authToken = prefs.authToken
                val refreshToken = prefs.refreshToken
                if (epoch != lifecycleEpoch) {
                    NarsLogger.d(TAG, "Token restore skipped: lifecycle moved on")
                    return@launch
                }
                authToken?.let { token ->
                    tokens.setSessionToken(token)
                    NarsLogger.d(TAG, "In-memory tokens restored (app foregrounded)")
                }
                refreshToken?.let { token -> tokens.setRefreshToken(token) }
                if (epoch != lifecycleEpoch) {
                    // A backgrounding landed while we were applying — undo so
                    // the final in-memory state matches the newest transition.
                    tokens.clearInMemoryTokens()
                }
            } catch (e: IllegalStateException) {
                NarsLogger.w(TAG, "Token restore skipped: Koin not ready", e)
            }
        }
    }
}

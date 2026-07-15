package com.nars.maplibre

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.nars.maplibre.data.api.ApiService
import com.nars.maplibre.di.appModule
import com.nars.maplibre.utils.NarsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        startKoin {
            androidContext(this@NarsApplication)
            modules(appModule)
        }
        MapLibre.getInstance(this, null, WellKnownTileServer.MapTiler)

        applicationScope.launch {
            try {
                val prefs: AppPreferences = get()
                val apiService: ApiService = get()
                prefs.authToken?.let { token ->
                    apiService.setSessionToken(token)
                    NarsLogger.d("NarsApplication", "User session found on startup")
                }
            } catch (e: IllegalStateException) {
                NarsLogger.w("NarsApplication", "Session check skipped: Koin not ready", e)
            }
        }

        registerTokenClearingOnBackground()
    }

    private fun registerTokenClearingOnBackground() {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                try {
                    val apiService: ApiService = get()
                    apiService.setSessionToken(null)
                    NarsLogger.d("NarsApplication", "In-memory tokens cleared (app backgrounded)")
                } catch (e: IllegalStateException) {
                    NarsLogger.w("NarsApplication", "Token clearing skipped: Koin not ready", e)
                }
            } else if (event == Lifecycle.Event.ON_START) {
                try {
                    val apiService: ApiService = get()
                    val prefs: AppPreferences = get()
                    prefs.authToken?.let { token ->
                        apiService.setSessionToken(token)
                        NarsLogger.d("NarsApplication", "In-memory tokens restored (app foregrounded)")
                    }
                } catch (e: IllegalStateException) {
                    NarsLogger.w("NarsApplication", "Token restore skipped: Koin not ready", e)
                }
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
    }
}

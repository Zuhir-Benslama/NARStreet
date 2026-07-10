package com.nars.maplibre

import android.app.Application
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
                prefs.authToken?.let { jwtToken ->
                    NarsLogger.d("NarsApplication", "User session found on startup")
                }
            } catch (ignored: RuntimeException) {
                NarsLogger.w("NarsApplication", "Session check skipped", ignored)
            }
        }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }
}

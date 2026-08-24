package com.nars.maplibre

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.nars.maplibre.ui.navigation.NarsNavHost
import com.nars.maplibre.ui.theme.NARSTheme
import com.nars.maplibre.ui.theme.ThemeMode
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    // Lazy property injection (resolved on first access in onCreate) — keeps
    // this activity free of manual service-locator lookups.
    private val appPreferences: AppPreferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by appPreferences.themeModeFlow.collectAsState(initial = appPreferences.themeMode)
            NARSTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    NarsNavHost()
                }
            }
        }
    }
}

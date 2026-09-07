package com.nars.maplibre.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nars.maplibre.data.api.ApiService
import com.nars.maplibre.data.store.FeatureStoreInterface
import com.nars.maplibre.ui.screens.LoginScreen
import com.nars.maplibre.ui.screens.MapScreen
import com.nars.maplibre.ui.screens.SettingsScreen
import com.nars.maplibre.utils.NarsLogger
import org.koin.compose.koinInject

@Composable
fun NarsNavHost() {
    val navController = rememberNavController()
    val apiService = koinInject<ApiService>()
    val featureStore = koinInject<FeatureStoreInterface>()

    // Session expiry can fire while any screen is showing (a rejected refresh
    // token), not just the map. Observe it here at the nav-graph level so the
    // logout navigation is never dropped because the current screen happens not
    // to be collecting the event.
    LaunchedEffect(apiService, featureStore) {
        apiService.sessionExpired.collect {
            NarsLogger.w("NarsNavHost", "Session expired — clearing local state and returning to login")
            // The FeatureStore is a process-wide singleton, so it must be reset
            // here even though the map's ViewModel dies with its backstack entry.
            featureStore.clearAll()
            if (navController.currentDestination?.route != Routes.LOGIN) {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.MAP) {
            MapScreen(
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLogout = {
                    navController.popBackStack()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}

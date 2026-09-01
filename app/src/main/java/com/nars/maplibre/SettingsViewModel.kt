package com.nars.maplibre

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nars.maplibre.data.api.SessionManager
import com.nars.maplibre.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val appPreferences: AppPreferences, private val sessionManager: SessionManager) :
    ViewModel() {
    private val _themeMode = MutableStateFlow(appPreferences.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        appPreferences.themeMode = mode
    }

    /**
     * Logs the user out. Navigation runs first (synchronously) so it can never
     * be skipped by a cancelled [viewModelScope]; server-side session
     * revocation then runs in the VM's scope and completes even if the VM is
     * cleared mid-flight (SessionManager runs it non-cancellable).
     */
    fun logout(onLogout: () -> Unit) {
        onLogout()
        viewModelScope.launch {
            sessionManager.logout()
        }
    }
}

package com.nars.maplibre

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nars.maplibre.data.api.SessionManager
import com.nars.maplibre.utils.NarsLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Owns the login form state and the authentication call. Previously this logic
 * lived directly in LoginScreen's composition on a rememberCoroutineScope —
 * an in-flight login was silently cancelled by rotation/navigation.
 */
class LoginViewModel(
    application: Application,
    private val sessionManager: SessionManager,
) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "LoginViewModel"
    }

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val canSubmit: Boolean get() = _username.value.isNotBlank() && _password.value.isNotBlank()

    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    fun onUsernameChange(value: String) {
        _username.value = value
        clearError()
    }

    fun onPasswordChange(value: String) {
        _password.value = value
        clearError()
    }

    fun login(onLoginSuccess: () -> Unit) {
        if (_isLoading.value || !canSubmit) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                sessionManager.login(_username.value.trim(), _password.value)
                    .onSuccess { onLoginSuccess() }
                    .onFailure { error ->
                        _errorMessage.value = "${appString(R.string.login_failed)}: ${error.message}"
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                NarsLogger.e(TAG, "Login failed", e)
                _errorMessage.value = "${appString(R.string.login_error)}: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun clearError() {
        if (_errorMessage.value != null) _errorMessage.value = null
    }

    private fun appString(resId: Int): String = getApplication<Application>().getString(resId)
}

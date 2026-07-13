package com.nars.maplibre.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nars.maplibre.R
import com.nars.maplibre.data.api.SessionManager
import com.nars.maplibre.ui.theme.DangerColor
import com.nars.maplibre.ui.theme.GlassBackground
import com.nars.maplibre.ui.theme.PrimaryColor
import com.nars.maplibre.ui.theme.PrimaryGradientEnd
import com.nars.maplibre.ui.theme.PrimaryGradientStart
import com.nars.maplibre.utils.NarsLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val FORM_WIDTH_FRACTION = 0.85f
private const val FOCUS_LABEL_ALPHA = 0.85f

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val sessionManager: SessionManager = koinInject()

    LaunchedEffect(Unit) {
        if (sessionManager.isLoggedIn()) onLoginSuccess()
    }

    val loginFailed = stringResource(R.string.login_failed)
    val loginError = stringResource(R.string.login_error)

    fun performLogin() {
        if (!isLoading && username.isNotBlank() && password.isNotBlank()) {
            val user = username
            val pass = password
            scope.launch {
                isLoading = true
                errorMessage = null
                try {
                    val result = sessionManager.login(user, pass)
                    result.onSuccess { onLoginSuccess() }
                    result.onFailure { error -> errorMessage = "$loginFailed: ${error.message}" }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: java.io.IOException) {
                    NarsLogger.e("LoginScreen", "Login failed", e)
                    errorMessage = "$loginError: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(colors = listOf(GlassBackground, GlassBackground.copy(alpha = 0.8f))),
        ),
        contentAlignment = Alignment.Center,
    ) {
        LoginForm(
            username = username,
            onUsernameChange = {
                username = it
                errorMessage = null
            },
            password = password,
            onPasswordChange = {
                password = it
                errorMessage = null
            },
            isLoading = isLoading,
            errorMessage = errorMessage,
            onLogin = { performLogin() },
        )
    }
}

@Composable
private fun LoginForm(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(FORM_WIDTH_FRACTION)
            .clip(RoundedCornerShape(20.dp))
            .background(GlassBackground.copy(alpha = 0.88f))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoginAppLogo()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.login_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.login_subtitle),
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(32.dp))

        LoginCredentialsForm(
            username = username,
            onUsernameChange = onUsernameChange,
            password = password,
            onPasswordChange = onPasswordChange,
            onLogin = onLogin,
        )

        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = error, fontSize = 13.sp, color = DangerColor, modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(24.dp))
        LoginSignInButton(isLoading = isLoading, onClick = onLogin)
    }
}

@Composable
private fun LoginAppLogo() {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(colors = listOf(PrimaryGradientStart, PrimaryGradientEnd))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name).first().uppercase(),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun LoginCredentialsForm(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
) {
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text(stringResource(R.string.login_username)) },
        leadingIcon = {
            Icon(
                Icons.Default.Person,
                contentDescription = "Person",
                tint = Color.White.copy(alpha = 0.6f),
            )
        },
        modifier = Modifier.fillMaxWidth(),
        colors = loginFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
        singleLine = true,
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.login_password)) },
        leadingIcon = {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Lock",
                tint = Color.White.copy(alpha = 0.6f),
            )
        },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        colors = loginFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onLogin() }),
        singleLine = true,
    )
}

@Composable
private fun LoginSignInButton(isLoading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryColor,
            disabledContainerColor = PrimaryColor.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = stringResource(R.string.login_sign_in),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.White.copy(alpha = 0.55f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
    focusedLabelColor = Color.White.copy(alpha = FOCUS_LABEL_ALPHA),
    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Color.White,
)

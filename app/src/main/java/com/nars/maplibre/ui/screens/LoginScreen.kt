package com.nars.maplibre.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.nars.maplibre.LoginViewModel
import com.nars.maplibre.R
import com.nars.maplibre.ui.theme.DangerColor
import com.nars.maplibre.ui.theme.GlassBackground
import com.nars.maplibre.ui.theme.PrimaryColor
import com.nars.maplibre.ui.theme.PrimaryGradientEnd
import com.nars.maplibre.ui.theme.PrimaryGradientStart
import org.koin.androidx.compose.koinViewModel

private const val FORM_WIDTH_FRACTION = 0.85f
private const val FOCUS_LABEL_ALPHA = 0.85f

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: LoginViewModel = koinViewModel()

    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(Unit) {
        if (viewModel.isLoggedIn()) onLoginSuccess()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Lift content above the keyboard so the login form stays visible
            // when the IME opens.
            .imePadding()
            .background(
                Brush.verticalGradient(colors = listOf(GlassBackground, GlassBackground.copy(alpha = 0.8f))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        LoginForm(
            username = username,
            onUsernameChange = viewModel::onUsernameChange,
            password = password,
            onPasswordChange = viewModel::onPasswordChange,
            isLoading = isLoading,
            errorMessage = errorMessage,
            canSubmit = username.isNotBlank() && password.isNotBlank(),
            onLogin = { viewModel.login(onLoginSuccess) },
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
    canSubmit: Boolean,
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
        LoginSignInButton(isLoading = isLoading, enabled = canSubmit, onClick = onLogin)
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
                contentDescription = stringResource(R.string.login_username_icon),
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
                contentDescription = stringResource(R.string.login_password_icon),
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
private fun LoginSignInButton(isLoading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        enabled = !isLoading && enabled,
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

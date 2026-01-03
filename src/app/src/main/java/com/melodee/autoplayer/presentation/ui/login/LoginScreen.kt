package com.melodee.autoplayer.presentation.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.melodee.autoplayer.domain.model.AuthResponse
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.melodee.autoplayer.R
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.content.pm.PackageManager
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import com.melodee.autoplayer.util.UrlParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: (AuthResponse) -> Unit
) {
    var emailOrUsername by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf(viewModel.serverUrl) }
    var hasHandledSuccess by remember { mutableStateOf(false) }
    
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Get version info
    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    val versionName = packageInfo?.versionName ?: "Unknown"

    // Function to handle login
    val handleLogin = {
        keyboardController?.hide()
        hasHandledSuccess = false  // Reset flag for new login attempt
        viewModel.login(emailOrUsername, password, serverUrl)
    }

    // Handle login success only once per successful login
    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success && !hasHandledSuccess) {
            hasHandledSuccess = true
            onLoginSuccess((loginState as LoginState.Success).response)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = stringResource(R.string.app_logo),
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 8.dp),
            contentScale = ContentScale.Fit
        )
    
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Version text
        Text(
            text = stringResource(R.string.version, versionName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { 
                serverUrl = it
                // Clear error when user starts typing
                if (loginState is LoginState.Error) {
                    viewModel.clearError()
                }
            },
            label = { Text(stringResource(R.string.server_url)) },
            placeholder = { Text("https://music.example.com") },
            supportingText = { 
                if (serverUrl.isNotBlank()) {
                    // Show how the URL will be normalized using the new parser
                    val normalizedUrl = UrlParser.normalizeServerUrl(serverUrl)
                    if (normalizedUrl.isNotBlank()) {
                        Text(
                            text = "Will use: $normalizedUrl",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "Invalid URL format",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text(
                        text = "Enter your server URL (with or without /api/v1/)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true,
            enabled = !isLoading,
            isError = loginState is LoginState.Error,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next
            )
        )

        OutlinedTextField(
            value = emailOrUsername,
            onValueChange = { 
                emailOrUsername = it
                // Clear error when user starts typing
                if (loginState is LoginState.Error) {
                    viewModel.clearError()
                }
            },
            label = { Text("Email or Username") },
            placeholder = { Text("Enter your email or username") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true,
            enabled = !isLoading,
            isError = loginState is LoginState.Error,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
        )

        OutlinedTextField(
            value = password,
            onValueChange = { 
                password = it
                // Clear error when user starts typing
                if (loginState is LoginState.Error) {
                    viewModel.clearError()
                }
            },
            label = { Text(stringResource(R.string.password)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            singleLine = true,
            enabled = !isLoading,
            isError = loginState is LoginState.Error,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (!isLoading) {
                        handleLogin()
                    }
                }
            )
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Button(
                onClick = handleLogin,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.login))
            }
        }

        if (loginState is LoginState.Error) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = (loginState as LoginState.Error).message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

// Note: URL normalization is now handled by UrlParser.normalizeServerUrl() 
package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.VpnViewModel

@Composable
fun AuthScreen(
    viewModel: VpnViewModel,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    var isSignUpMode by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Shield VPN",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Log in or sign up for Premium access,\nor continue as a Free user.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (authError != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = authError!!, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; viewModel.clearAuthError() },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; viewModel.clearAuthError() },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { 
                if (isSignUpMode) {
                    viewModel.signup(email, password)
                } else {
                    viewModel.login(email, password)
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(if (isSignUpMode) "Sign Up" else "Log In")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(
            onClick = { isSignUpMode = !isSignUpMode },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isSignUpMode) "Already have an account? Log In" else "Don't have an account? Sign Up")
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        TextButton(
            onClick = { viewModel.continueAsGuest() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue as Free User")
        }
    }
}

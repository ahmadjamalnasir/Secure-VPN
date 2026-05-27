package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.ConnectionState
import com.example.ui.viewmodel.VpnViewModel

@Composable
fun ConnectScreen(
    viewModel: VpnViewModel,
    onNavigateToServers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        val statusText = when (connectionState) {
            ConnectionState.DISCONNECTED -> "Disconnected"
            ConnectionState.CONNECTING -> "Connecting..."
            ConnectionState.CONNECTED -> "Protected"
            ConnectionState.ERROR -> "Connection Failed"
        }

        val statusColor by animateColorAsState(
            targetValue = when (connectionState) {
                ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                ConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiary
                ConnectionState.ERROR -> MaterialTheme.colorScheme.error
                ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
            }, label = "statusColor"
        )

        Text(
            text = statusText,
            style = MaterialTheme.typography.headlineMedium,
            color = statusColor
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Big Power Button
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable { viewModel.toggleConnection() },
            contentAlignment = Alignment.Center
        ) {
            // Nested rings for the design
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Toggle VPN",
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Selected Server Row
        Card(
            onClick = onNavigateToServers,
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Current Location", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedServer?.let { "${it.city}, ${it.country}" } ?: "Select a Server",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = "Change",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { viewModel.logout() }) {
            Text("Log Out / Change User")
        }
    }
}

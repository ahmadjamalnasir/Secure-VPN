package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.VpnServer
import com.example.ui.viewmodel.VpnViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    viewModel: VpnViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val isPremium by viewModel.isUserPremium.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Server") }
            )
        }
    ) { padding ->
        if (servers.isEmpty()) {
            Box(
                modifier = modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No servers found.\nPlease check your connection.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                contentPadding = padding,
                modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)
            ) {
                items(servers) { server ->
                    ServerItem(
                        server = server,
                        isSelected = server.id == selectedServer?.id,
                        isUserPremium = isPremium,
                        onClick = {
                            viewModel.selectServer(server)
                            if (!server.is_premium || isPremium) {
                                onNavigateBack()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun ServerItem(
    server: VpnServer,
    isSelected: Boolean,
    isUserPremium: Boolean,
    onClick: () -> Unit
) {
    val isLocked = server.is_premium && !isUserPremium

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "${server.city}, ${server.country}", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = if (server.status == "online") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    Text(text = if (server.status == "online") "Online" else "Offline", color = statusColor, style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Load: ${server.load_percent}%", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (isLocked) {
                Icon(Icons.Default.Lock, contentDescription = "Premium Required", tint = MaterialTheme.colorScheme.outline)
            } else if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

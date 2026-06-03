package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.ConnectionState
import com.example.ui.viewmodel.VpnViewModel

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.net.VpnService
import android.content.Intent
import android.app.Activity
import com.example.service.WireGuardVpnService

@Composable
fun ConnectScreen(
    viewModel: VpnViewModel,
    onNavigateToServers: () -> Unit,
    onNavigateToPremium: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isUserPremium by viewModel.isUserPremium.collectAsStateWithLifecycle()
    val durationSeconds by viewModel.durationSeconds.collectAsStateWithLifecycle()
    val downloadSpeedBytes by viewModel.currentDownloadSpeedBytes.collectAsStateWithLifecycle()
    val uploadSpeedBytes by viewModel.currentUploadSpeedBytes.collectAsStateWithLifecycle()
    val totalDownloadedBytes by viewModel.totalDownloadedBytes.collectAsStateWithLifecycle()
    val totalUploadedBytes by viewModel.totalUploadedBytes.collectAsStateWithLifecycle()
    val isBackendOffline by viewModel.isBackendOffline.collectAsStateWithLifecycle()
    
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            kotlinx.coroutines.delay(4000)
            viewModel.clearError()
        }
    }
    
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val vpnResultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val server = selectedServer ?: return@rememberLauncherForActivityResult
                val intent = Intent(context, WireGuardVpnService::class.java).apply {
                    putExtra("SERVER_ID", server.id)
                    putExtra("SERVER_IP", server.ip_address)
                    putExtra("SERVER_WG_KEY", server.wg_public_key)
                    putExtra("SERVER_WG_ENDPOINT", server.wg_endpoint)
                    putExtra("SERVER_DNS", server.dns)
                    putExtra("SERVER_KEEPALIVE", server.keepalive ?: 25)
                }
                context.startService(intent)
                viewModel.setConnectedState()
            } catch (e: Exception) {
                e.printStackTrace()
                viewModel.disconnect()
            }
        } else {
            viewModel.disconnect()
        }
    }

    val toggleVpnAndRequestPermission = {
        if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR) {
            if (isBackendOffline) {
                viewModel.setErrorStateOffline()
            } else {
                val server = selectedServer
                if (server == null) {
                    viewModel.toggleConnection() // will set error
                } else {
                    viewModel.connect(server) // Sets state to CONNECTING
                    try {
                        val intent = VpnService.prepare(context)
                        if (intent != null) {
                            vpnResultLauncher.launch(intent)
                        } else {
                            // Already have permission
                            val serviceIntent = Intent(context, WireGuardVpnService::class.java).apply {
                                putExtra("SERVER_ID", server.id)
                                putExtra("SERVER_IP", server.ip_address)
                                putExtra("SERVER_WG_KEY", server.wg_public_key)
                                putExtra("SERVER_WG_ENDPOINT", server.wg_endpoint)
                                putExtra("SERVER_DNS", server.dns)
                                putExtra("SERVER_KEEPALIVE", server.keepalive ?: 25)
                            }
                            context.startService(serviceIntent)
                            viewModel.setConnectedState()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        viewModel.disconnect()
                    }
                }
            }
        } else {
            viewModel.toggleConnection() // disconnect
            try {
                val stopIntent = Intent(context, WireGuardVpnService::class.java)
                context.stopService(stopIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.height(32.dp))

        // Big Power Button
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable { toggleVpnAndRequestPermission() },
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

        // Connection Stats Card with smooth transition
        androidx.compose.animation.AnimatedVisibility(
            visible = connectionState == ConnectionState.CONNECTED,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
        ) {
            ConnectionStatsCard(
                duration = durationSeconds,
                downloadSpeed = downloadSpeedBytes,
                uploadSpeed = uploadSpeedBytes,
                totalDownloaded = totalDownloadedBytes,
                totalUploaded = totalUploadedBytes,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

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

        Spacer(modifier = Modifier.height(24.dp))

        // Premium Section prompting the user to upgrade, shown ONLY if they are not premium
        if (!isUserPremium) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToPremium() },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF6366F1), // Indigo
                                    Color(0xFF4F46E5), // Deep Indigo
                                    Color(0xFF312E81)  // Dark Indigo
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Premium Icon",
                                    tint = Color(0xFFFBBF24), // Gold Yellow
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Upgrade to Prime Premium",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Get access to ultra-fast servers, unlimited bandwidth, and premium WireGuard configurations.",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Features grid in premium banner
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, "Checked", tint = Color.Green, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("50+ high-speed secure servers", color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, "Checked", tint = Color.Green, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Military-grade encryption keys", color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onNavigateToPremium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF4F46E5)
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Unlock Premium Now", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            // If they are premium, show a nice Active Subscription card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Active Premium",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Prime Premium Active",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Enjoy unlimited secure connections",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Action button to manage user account
        TextButton(onClick = { viewModel.logout() }) {
            Text("Log Out / Change User")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ConnectionStatsCard(
    duration: Long,
    downloadSpeed: Long,
    uploadSpeed: Long,
    totalDownloaded: Long,
    totalUploaded: Long,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = "Stats",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Live Connection Metrics",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stat Grid Row 1: Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Duration",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connection Time",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Stat Grid Row 2: Speeds Side by Side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Download Speed
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Download Status",
                            tint = Color(0xFF10B981), // Emerald/Green
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Download",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatSpeed(downloadSpeed),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        .align(Alignment.CenterVertically)
                )

                // Upload Speed
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Upload Status",
                            tint = Color(0xFF3B82F6), // Blue
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Upload",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatSpeed(uploadSpeed),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Stat Grid Row 3: Total Data side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Total Downloaded
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total Download",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatDataSize(totalDownloaded),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        .align(Alignment.CenterVertically)
                )

                // Total Uploaded
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    Text(
                        text = "Total Upload",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatDataSize(totalUploaded),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

private fun formatSpeed(bytesPerSec: Long): String {
    val bitsPerSec = bytesPerSec * 8
    return when {
        bitsPerSec >= 1_000_000 -> String.format("%.1f Mbps", bitsPerSec / 1_000_000.0)
        bitsPerSec >= 1_000 -> String.format("%.0f Kbps", bitsPerSec / 1_000.0)
        else -> "$bitsPerSec bps"
    }
}

private fun formatDataSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> String.format("%.2f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format("%.2f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

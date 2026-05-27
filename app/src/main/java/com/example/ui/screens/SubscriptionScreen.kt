package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.VpnViewModel

@Composable
fun SubscriptionScreen(
    viewModel: VpnViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Upgrade to Shield Premium",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Unlock all server locations, higher speeds, and advanced privacy features.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        FeatureRow("Access to 50+ Premium Locations")
        FeatureRow("High Speed Connected Nodes")
        FeatureRow("Strict No-Logs Policy Handshake")
        FeatureRow("Ad-Blocker Included")

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { viewModel.upgradeToPremium() },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Subscribe for $9.99/month")
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = { /* Restore purchases */ }) {
            Text("Restore Purchases")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "By subscribing, you agree to our Privacy Policy and Terms of Service. Manage subscriptions in Google Play.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun FeatureRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

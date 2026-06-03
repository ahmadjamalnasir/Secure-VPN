package com.example.service

import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.remote.ApiClient
import com.example.data.repository.VpnRepository
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.Tunnel.State
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.config.InetNetwork
import com.wireguard.config.InetEndpoint
import com.wireguard.crypto.Key
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.InetAddress

class WireGuardVpnService : android.app.Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var goBackendInstance: GoBackend? = null

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    private val shieldTunnel = object : Tunnel {
        override fun getName(): String = "wg-demo-1"
        override fun onStateChange(state: Tunnel.State) {
            Log.i("WireGuardVpnService", "Tunnel state changed to: $state")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("WireGuardVpnService", "onStartCommand: Starting dynamic WireGuard connection")

        val serverIp = intent?.getStringExtra("SERVER_IP")
        val serverWgKey = intent?.getStringExtra("SERVER_WG_KEY")
        val serverWgEndpoint = intent?.getStringExtra("SERVER_WG_ENDPOINT")
        val serverDns = intent?.getStringExtra("SERVER_DNS")
        val serverKeepalive = intent?.getIntExtra("SERVER_KEEPALIVE", 25) ?: 25

        serviceScope.launch {
            try {
                // Determine parameter sources: prefer passed extras, fallback to apiService or defaults
                var wgPublicKeyBase64 = serverWgKey
                var wgEndpointString = serverWgEndpoint
                var dnsIp = serverDns ?: "8.8.8.8"
                var keepaliveSecs = serverKeepalive

                if (wgPublicKeyBase64.isNullOrEmpty() || wgEndpointString.isNullOrEmpty()) {
                    try {
                        val activeServerId = intent?.getStringExtra("SERVER_ID") ?: "wg-demo-1"
                        Log.i("WireGuardVpnService", "Fetching live server config via getServer for $activeServerId")
                        val wgServer = ApiClient.apiService.getServer(activeServerId)
                        if (wgPublicKeyBase64.isNullOrEmpty()) {
                            wgPublicKeyBase64 = wgServer.wg_public_key
                        }
                        if (wgEndpointString.isNullOrEmpty()) {
                            wgEndpointString = wgServer.wg_endpoint
                        }
                        dnsIp = wgServer.dns ?: dnsIp
                        keepaliveSecs = wgServer.keepalive ?: keepaliveSecs
                    } catch (e: Exception) {
                        Log.w("WireGuardVpnService", "Failed fetching live server config from host: ${e.message}", e)
                    }
                }

                // Check if config is fully loaded
                if (wgPublicKeyBase64.isNullOrEmpty() || wgEndpointString.isNullOrEmpty()) {
                    Log.e("WireGuardVpnService", "Missing WireGuard configuration.")
                    val errorIntent = android.content.Intent("com.example.vpn.ERROR")
                    errorIntent.putExtra("message", "Unable to retrieve WireGuard configuration for this server.")
                    sendBroadcast(errorIntent)
                    stopSelf()
                    return@launch
                }

                // 2. Load/generate client keypair securely using EncryptedSharedPreferences
                val clientKeyPair = WireGuardManager.getOrGenerateClientKeyPair(this@WireGuardVpnService)
                val clientPrivateKey = clientKeyPair.privateKey

                Log.i("WireGuardVpnService", "Client private key loaded successfully. Building Config. Endpoint: $wgEndpointString, Key: $wgPublicKeyBase64")

                // 3. Build WireGuard Config object at runtime
                val interfaceConfig = Interface.Builder()
                    .addAddress(InetNetwork.parse("10.0.0.2/32"))
                    .addDnsServer(InetAddress.getByName(dnsIp))
                    .setKeyPair(clientKeyPair)
                    .build()

                val peerConfig = Peer.Builder()
                    .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
                    .setEndpoint(InetEndpoint.parse(wgEndpointString))
                    .setPublicKey(Key.fromBase64(wgPublicKeyBase64))
                    .setPersistentKeepalive(keepaliveSecs)
                    .build()

                val wireGuardConfig = Config.Builder()
                    .setInterface(interfaceConfig)
                    .addPeer(peerConfig)
                    .build()

                // 4. Pass config to GoBackend to establish tunnel
                val backend = GoBackend(this@WireGuardVpnService)
                goBackendInstance = backend
                
                Log.i("WireGuardVpnService", "Establishing WireGuard tunnel via GoBackend")
                backend.setState(shieldTunnel, Tunnel.State.UP, wireGuardConfig)
                
            } catch (e: Exception) {
                Log.e("WireGuardVpnService", "Failed to connect tunnel: ${e.message}", e)
                val errorIntent = android.content.Intent("com.example.vpn.ERROR")
                errorIntent.putExtra("message", "Connection failed: ${e.message ?: "Unknown error"}")
                sendBroadcast(errorIntent)
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("WireGuardVpnService", "onDestroy: cleaning up and tearing down the tunnel")
        serviceScope.launch {
            try {
                goBackendInstance?.setState(shieldTunnel, Tunnel.State.DOWN, null)
                goBackendInstance = null
            } catch (e: Exception) {
                Log.e("WireGuardVpnService", "Error shutting down WireGuard tunnel", e)
            }
        }
    }
}

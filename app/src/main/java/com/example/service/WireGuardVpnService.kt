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

class WireGuardVpnService : VpnService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var goBackendInstance: GoBackend? = null

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
                        Log.i("WireGuardVpnService", "Fetching live server config via getWgDemoServer")
                        val wgDemo = ApiClient.apiService.getWgDemoServer()
                        if (wgPublicKeyBase64.isNullOrEmpty()) {
                            wgPublicKeyBase64 = wgDemo.wg_public_key
                        }
                        if (wgEndpointString.isNullOrEmpty()) {
                            wgEndpointString = wgDemo.wg_endpoint
                        }
                        dnsIp = wgDemo.dns ?: dnsIp
                        keepaliveSecs = wgDemo.keepalive ?: keepaliveSecs
                    } catch (e: Exception) {
                        Log.w("WireGuardVpnService", "Failed fetching live server config from host: ${e.message}", e)
                    }
                }

                // Double check defaults
                if (wgPublicKeyBase64.isNullOrEmpty()) {
                    wgPublicKeyBase64 = "z1d6XOUyV2R0sEz211W5JpbFfsDc9wi7VCwvSgon+CA="
                }
                if (wgEndpointString.isNullOrEmpty()) {
                    wgEndpointString = "us1-wg.ssl-tun.xyz:2600"
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

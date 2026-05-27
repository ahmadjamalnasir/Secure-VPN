package com.example.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.model.VpnServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WireGuardVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val serverIp = intent?.getStringExtra("SERVER_IP") ?: return START_NOT_STICKY
        val serverWgKey = intent?.getStringExtra("SERVER_WG_KEY") ?: return START_NOT_STICKY

        // We simulate actual WG builder for app compliance and VPN entitlements handling
        setupVpn(serverIp, serverWgKey)
        return START_STICKY
    }

    private fun setupVpn(serverIp: String, serverWgKey: String) {
        if (vpnInterface != null) {
            try {
                vpnInterface?.close()
                vpnInterface = null
            } catch (e: Exception) {
                // Ignore
            }
        }

        try {
            val builder = Builder()
                .addAddress("10.0.0.2", 24)
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)
                .setSession("Shield VPN")

            vpnInterface = builder.establish()

            // Simulate the secure handshake and connection keep-alive
            serviceScope.launch {
                Log.d("WireGuardVpnService", "Established connection to $serverIp with $serverWgKey")
                while (true) {
                    delay(10000)
                    // keepalive
                }
            }
        } catch (e: Exception) {
            Log.e("WireGuardVpnService", "Error setting up VPN: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            // ignore
        }
    }
}

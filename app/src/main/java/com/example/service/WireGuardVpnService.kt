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
        val serverIp = intent?.getStringExtra("SERVER_IP")
        val serverWgKey = intent?.getStringExtra("SERVER_WG_KEY")

        if (serverIp == null || serverWgKey == null) {
            Log.e("WireGuardVpnService", "Missing configuration, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.i("WireGuardVpnService", "Starting VPN service for IP: $serverIp")
        setupVpn(serverIp, serverWgKey)
        return START_STICKY
    }

    private fun setupVpn(serverIp: String, serverWgKey: String) {
        if (vpnInterface != null) {
            try {
                vpnInterface?.close()
                vpnInterface = null
            } catch (e: Exception) {
                Log.e("WireGuardVpnService", "Error closing existing VPN interface", e)
            }
        }

        try {
            val builder = Builder()
            
            // Try to add address safely
            try {
                builder.addAddress("10.0.0.2", 24)
            } catch (e: IllegalArgumentException) {
                Log.e("WireGuardVpnService", "Invalid address format", e)
            }
            
            builder.addDnsServer("1.1.1.1")
            
            try {
                builder.addRoute("0.0.0.0", 0)
            } catch (e: IllegalArgumentException) {
                Log.e("WireGuardVpnService", "Invalid route format", e)
            }
                
            builder.setSession("Shield VPN")

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
            Log.e("WireGuardVpnService", "Error setting up VPN: ${e.message}", e)
            stopSelf()
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

package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.VpnRepository
import com.example.model.VpnServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

class VpnViewModel(private val repository: VpnRepository) : ViewModel() {

    val servers = repository.servers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _selectedServer = MutableStateFlow<VpnServer?>(null)
    val selectedServer: StateFlow<VpnServer?> = _selectedServer.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _isUserPremium = MutableStateFlow(false)
    val isUserPremium: StateFlow<Boolean> = _isUserPremium.asStateFlow()
    
    // We start as true by default so the home screen is the landing page.
    private val _isAuthenticated = MutableStateFlow(true)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _userEmail = MutableStateFlow<String?>("Guest User")
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // VPN Settings states as requested
    private val _isKillSwitchEnabled = MutableStateFlow(false)
    val isKillSwitchEnabled: StateFlow<Boolean> = _isKillSwitchEnabled.asStateFlow()

    private val _selectedBypassApps = MutableStateFlow<Set<String>>(emptySet())
    val selectedBypassApps: StateFlow<Set<String>> = _selectedBypassApps.asStateFlow()

    private val _selectedProtocol = MutableStateFlow("WireGuard (UDP)")
    val selectedProtocol: StateFlow<String> = _selectedProtocol.asStateFlow()

    private val _dnsServer = MutableStateFlow("1.1.1.1 (Cloudflare)")
    val dnsServer: StateFlow<String> = _dnsServer.asStateFlow()

    private val _isBackendOffline = MutableStateFlow(false)
    val isBackendOffline: StateFlow<Boolean> = _isBackendOffline.asStateFlow()

    // Connection statistics
    private var statsJob: kotlinx.coroutines.Job? = null

    private val _durationSeconds = MutableStateFlow(0L)
    val durationSeconds: StateFlow<Long> = _durationSeconds.asStateFlow()

    private val _currentDownloadSpeedBytes = MutableStateFlow(0L)
    val currentDownloadSpeedBytes: StateFlow<Long> = _currentDownloadSpeedBytes.asStateFlow()

    private val _currentUploadSpeedBytes = MutableStateFlow(0L)
    val currentUploadSpeedBytes: StateFlow<Long> = _currentUploadSpeedBytes.asStateFlow()

    private val _totalDownloadedBytes = MutableStateFlow(0L)
    val totalDownloadedBytes: StateFlow<Long> = _totalDownloadedBytes.asStateFlow()

    private val _totalUploadedBytes = MutableStateFlow(0L)
    val totalUploadedBytes: StateFlow<Long> = _totalUploadedBytes.asStateFlow()

    init {
        // Fetch/refresh servers from the API silently on initialization
        refreshServers(isSilent = true)

        // Automatically select the first available server when the server list is populated
        viewModelScope.launch {
            servers.collect { serverList ->
                if (_selectedServer.value == null && serverList.isNotEmpty()) {
                    // Try to find the first free online server, or just the first server
                    val defaultServer = serverList.firstOrNull { !it.is_premium && it.status == "online" }
                        ?: serverList.firstOrNull { it.status == "online" }
                        ?: serverList.firstOrNull()
                    _selectedServer.value = defaultServer
                }
            }
        }
    }

    fun toggleKillSwitch() {
        _isKillSwitchEnabled.update { !it }
    }

    fun toggleBypassApp(packageName: String) {
        _selectedBypassApps.update { current ->
            if (current.contains(packageName)) current - packageName else current + packageName
        }
    }

    fun setProtocol(protocol: String) {
        _selectedProtocol.value = protocol
    }

    fun setDnsServer(dns: String) {
        _dnsServer.value = dns
    }

    fun startStatsTracking() {
        statsJob?.cancel()
        _durationSeconds.value = 0L
        _currentDownloadSpeedBytes.value = 0L
        _currentUploadSpeedBytes.value = 0L
        _totalDownloadedBytes.value = 0L
        _totalUploadedBytes.value = 0L

        statsJob = viewModelScope.launch {
            val initialRx = android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid())
            val initialTx = android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid())

            val unsupportedLong = android.net.TrafficStats.UNSUPPORTED.toLong()

            var prevRx = if (initialRx != unsupportedLong) initialRx else 0L
            var prevTx = if (initialTx != unsupportedLong) initialTx else 0L

            var elapsed = 0L

            while (true) {
                delay(1000)
                elapsed++
                _durationSeconds.value = elapsed

                val currentRx = android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid())
                val currentTx = android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid())

                val rxMatched = if (currentRx != unsupportedLong) currentRx else 0L
                val txMatched = if (currentTx != unsupportedLong) currentTx else 0L

                // Change since previous sample (1 second ago)
                val rxSpeed = (rxMatched - prevRx).coerceAtLeast(0L)
                val txSpeed = (txMatched - prevTx).coerceAtLeast(0L)

                _currentDownloadSpeedBytes.value = rxSpeed
                _currentUploadSpeedBytes.value = txSpeed

                // Total since tracking started
                val totalRx = (rxMatched - (if (initialRx != unsupportedLong) initialRx else 0L)).coerceAtLeast(0L)
                val totalTx = (txMatched - (if (initialTx != unsupportedLong) initialTx else 0L)).coerceAtLeast(0L)

                _totalDownloadedBytes.value = totalRx
                _totalUploadedBytes.value = totalTx

                prevRx = rxMatched
                prevTx = txMatched
            }
        }
    }

    fun stopStatsTracking() {
        statsJob?.cancel()
        statsJob = null
        _durationSeconds.value = 0L
        _currentDownloadSpeedBytes.value = 0L
        _currentUploadSpeedBytes.value = 0L
        _totalDownloadedBytes.value = 0L
        _totalUploadedBytes.value = 0L
    }

    fun login(email: String, pass: String) {
        android.util.Log.i("ShieldVPN", "Auth Flow: Starting login for $email")
        viewModelScope.launch {
            _authError.value = null
            val result = repository.login(email, pass)
            result.onSuccess { res ->
                android.util.Log.i("ShieldVPN", "Auth Flow: Login successful, premium=${res.is_premium}")
                _isAuthenticated.value = true
                _userEmail.value = email
                _isUserPremium.value = res.is_premium
                _isBackendOffline.value = false
                refreshServers()
            }.onFailure {
                android.util.Log.e("ShieldVPN", "Auth Flow: Login failed", it)
                _authError.value = "Login failed: Invalid credentials or offline"
                _isBackendOffline.value = true
            }
        }
    }

    fun signup(email: String, pass: String) {
        android.util.Log.i("ShieldVPN", "Auth Flow: Starting signup for $email")
        viewModelScope.launch {
            _authError.value = null
            val result = repository.signup(email, pass)
            result.onSuccess { res ->
                android.util.Log.i("ShieldVPN", "Auth Flow: Signup successful")
                _isAuthenticated.value = true
                _userEmail.value = email
                _isUserPremium.value = res.is_premium
                _isBackendOffline.value = false
                refreshServers()
            }.onFailure {
                android.util.Log.e("ShieldVPN", "Auth Flow: Signup failed", it)
                _authError.value = "Sign up failed: Email may be in use."
                _isBackendOffline.value = true
            }
        }
    }

    fun continueAsGuest() {
        android.util.Log.i("ShieldVPN", "Auth Flow: Continuing as Free User")
        _isAuthenticated.value = true
        _userEmail.value = "Guest User"
        _isUserPremium.value = false
        refreshServers()
    }
    
    fun logout() {
        android.util.Log.i("ShieldVPN", "Auth Flow: Logging out")
        // Instead of hard-locking them on an auth screen, they revert back to guest mode seamlessly!
        _userEmail.value = "Guest User"
        _isUserPremium.value = false
        disconnect()
    }

    fun refreshServers(isSilent: Boolean = false) {
        android.util.Log.i("ShieldVPN", "Backend Connectivity: Refreshing servers via repository (silent=$isSilent)")
        viewModelScope.launch {
            _isRefreshing.value = true
            if (!isSilent) {
                _errorMessage.value = null
            }
            val result = repository.refreshServers()
            result.onSuccess {
                android.util.Log.i("ShieldVPN", "Backend Connectivity: Successfully fetched servers")
                _isBackendOffline.value = false
            }
            result.onFailure {
                android.util.Log.e("ShieldVPN", "Backend Connectivity: Failed mapping or fetching servers (silent=$isSilent)", it)
                if (!isSilent) {
                    _errorMessage.value = "Backend service is currently offline or unavailable."
                }
                _isBackendOffline.value = true
                if (_selectedServer.value != null && servers.value.isEmpty()) {
                     _selectedServer.value = null
                }
            }
            _isRefreshing.value = false
        }
    }

    fun setErrorStateOffline() {
        _errorMessage.value = "Backend service is currently offline or unavailable."
        _connectionState.value = ConnectionState.ERROR
    }

    fun selectServer(server: VpnServer) {
        if (server.is_premium && !_isUserPremium.value) {
            _errorMessage.value = "Premium subscription required for this server."
            return
        }
        if (_connectionState.value != ConnectionState.DISCONNECTED) {
            disconnect()
        }
        _selectedServer.value = server
    }

    fun toggleConnection() {
        if (_isBackendOffline.value) {
            setErrorStateOffline()
            return
        }
        val server = _selectedServer.value
        if (server == null) {
            _errorMessage.value = "Please select a server first."
            return
        }

        when (_connectionState.value) {
            ConnectionState.DISCONNECTED -> connect(server)
            ConnectionState.CONNECTING -> disconnect()
            ConnectionState.CONNECTED -> disconnect()
            ConnectionState.ERROR -> connect(server)
        }
    }

    fun connect(server: VpnServer) {
        if (_isBackendOffline.value) {
            setErrorStateOffline()
            return
        }
        if (server.status != "online") {
            _errorMessage.value = "Server is currently offline. Please choose another."
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        _errorMessage.value = null
        
        // The actual VPN Service Intent will be started from the UI layer (ConnectScreen)
        // because it requires Activity context and VpnService.prepare() permissions.
    }
    
    fun setConnectedState() {
        _connectionState.value = ConnectionState.CONNECTED
        startStatsTracking()
    }

    fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTED
        stopStatsTracking()
        // UI layer will stop the service
    }

    override fun onCleared() {
        super.onCleared()
        stopStatsTracking()
    }

    fun clearError() {
        _errorMessage.value = null
    }
    
    fun setErrorMessage(message: String) {
        _errorMessage.value = message
        _connectionState.value = ConnectionState.ERROR
    }
    
    fun clearAuthError() {
        _authError.value = null
    }
    
    fun upgradeToPremium() {
        _isUserPremium.value = true
    }
}

class VpnViewModelFactory(private val repository: VpnRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VpnViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VpnViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


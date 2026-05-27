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
    
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _authError.value = null
            val result = repository.login(email, pass)
            result.onSuccess { res ->
                _isAuthenticated.value = true
                _isUserPremium.value = res.is_premium
                refreshServers()
            }.onFailure {
                _authError.value = "Login failed: Invalid credentials or offline"
            }
        }
    }

    fun continueAsGuest() {
        _isAuthenticated.value = true
        _isUserPremium.value = false
        refreshServers()
    }
    
    fun logout() {
        _isAuthenticated.value = false
        _isUserPremium.value = false
        disconnect()
    }

    fun refreshServers() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            val result = repository.refreshServers()
            result.onFailure {
                _errorMessage.value = "Backend unavailable. Please check your network."
                if (_selectedServer.value != null && servers.value.isEmpty()) {
                     _selectedServer.value = null
                }
            }
            _isRefreshing.value = false
        }
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
        when (_connectionState.value) {
            ConnectionState.DISCONNECTED -> connect()
            ConnectionState.CONNECTING -> disconnect()
            ConnectionState.CONNECTED -> disconnect()
            ConnectionState.ERROR -> connect()
        }
    }

    private fun connect() {
        val server = _selectedServer.value
        if (server == null) {
            _errorMessage.value = "Please select a server first."
            return
        }
        if (server.status != "online") {
            _errorMessage.value = "Server is currently offline. Please choose another."
            return
        }

        viewModelScope.launch {
            _connectionState.value = ConnectionState.CONNECTING
            _errorMessage.value = null
            
            // Simulating real VPN handshake
            delay(1500)
            
            // NOTE: In a real app, VpnService intent is generated and launched via Context. 
            // VpnService.prepare(context) followed by context.startService()
            // We simulate successful connection from service here:
            
            if (server.wg_endpoint == null && server.wg_public_key == null) {
                // If it's a real server without config, it might fail in reality. But this is simulating.
                _connectionState.value = ConnectionState.CONNECTED
            } else {
                _connectionState.value = ConnectionState.CONNECTED
            }
        }
    }

    private fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun clearError() {
        _errorMessage.value = null
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


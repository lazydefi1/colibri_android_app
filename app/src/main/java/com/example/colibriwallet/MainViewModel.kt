package com.example.colibriwallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.colibriwallet.ble.BleRepository
import com.example.colibriwallet.ble.BleDevice
import com.example.colibriwallet.ble.ConnectionState

// Navigation states
sealed class Screen {
    object DeviceList : Screen()
    data class Connection(val device: BleDevice) : Screen()
    data class Methods(val device: BleDevice, val response: String) : Screen()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: BleRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
    val messages = repository.messages
    val discoveredDevices = repository.discoveredDevices
    val bondedDevices = repository.bondedDevices
    val isScanning = repository.isScanning

    // Navigation state
    private val _currentScreen = MutableStateFlow<Screen>(Screen.DeviceList)
    val currentScreen: StateFlow<Screen> = _currentScreen

    // RPC response state
    private val _lastRpcResponse = MutableStateFlow("")
    val lastRpcResponse: StateFlow<String> = _lastRpcResponse

    fun getBondedDevices() {
        repository.getBondedDevices()
    }

    fun startDeviceDiscovery() {
        viewModelScope.launch {
            repository.startDeviceDiscovery()
        }
    }

    fun connectAndSendListMethods() {
        viewModelScope.launch {
            repository.connectAndSendListMethods()
        }
    }

    fun connectToDevice(device: BleDevice) {
        // Navigate to connection screen
        _currentScreen.value = Screen.Connection(device)
        // Start connection process
        viewModelScope.launch {
            repository.connectToSpecificDevice(device)
        }
    }

            fun sendListMethods() {
        // Navigate to methods screen immediately when button is tapped
        val current = _currentScreen.value
        if (current is Screen.Connection) {
            _currentScreen.value = Screen.Methods(current.device, "")
        }

        // Then send the RPC command in the background
        viewModelScope.launch {
            val response = repository.sendRpc("""{"method":"listMethods"}""")
            _lastRpcResponse.value = response

            // Update the methods screen with the response
            val currentScreen = _currentScreen.value
            if (currentScreen is Screen.Methods) {
                _currentScreen.value = Screen.Methods(currentScreen.device, response)
            }
        }
    }

    fun sendGetStatus() {
        // Navigate to methods screen immediately when button is tapped
        val current = _currentScreen.value
        if (current is Screen.Connection) {
            _currentScreen.value = Screen.Methods(current.device, "")
        }

        // Then send the RPC command in the background
        viewModelScope.launch {
            val response = repository.sendRpc("""{"method":"getStatus"}""")
            _lastRpcResponse.value = response

            // Update the methods screen with the response
            val currentScreen = _currentScreen.value
            if (currentScreen is Screen.Methods) {
                _currentScreen.value = Screen.Methods(currentScreen.device, response)
            }
        }
    }

    fun sendCustomRpc(rpcCommand: String) {
        viewModelScope.launch {
            val response = repository.sendRpc(rpcCommand)
            _lastRpcResponse.value = response

            // Update methods screen if we're currently on it
            val current = _currentScreen.value
            if (current is Screen.Methods) {
                _currentScreen.value = Screen.Methods(current.device, response)
            }
        }
    }

    fun navigateToDeviceList() {
        _currentScreen.value = Screen.DeviceList
    }

    fun navigateToConnection(device: BleDevice) {
        _currentScreen.value = Screen.Connection(device)
    }

    fun navigateToMethods(device: BleDevice, response: String) {
        _currentScreen.value = Screen.Methods(device, response)
    }

    fun navigateBackFromMethods() {
        val current = _currentScreen.value
        if (current is Screen.Methods) {
            _currentScreen.value = Screen.Connection(current.device)
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.close()
    }
}

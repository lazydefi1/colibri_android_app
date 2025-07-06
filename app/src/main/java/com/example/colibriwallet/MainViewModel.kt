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
        viewModelScope.launch {
            val response = repository.sendRpc("""{"method":"listMethods"}""")
            // Message will be automatically added to the messages flow
        }
    }

    fun navigateToDeviceList() {
        _currentScreen.value = Screen.DeviceList
    }

    fun navigateToConnection(device: BleDevice) {
        _currentScreen.value = Screen.Connection(device)
    }

    override fun onCleared() {
        super.onCleared()
        repository.close()
    }
}

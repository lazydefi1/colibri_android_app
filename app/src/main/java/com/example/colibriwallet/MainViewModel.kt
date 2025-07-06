package com.example.colibriwallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.colibriwallet.ble.BleRepository
import com.example.colibriwallet.ble.ConnectionState

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: BleRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
    val messages = repository.messages
    val discoveredDevices = repository.discoveredDevices
    val bondedDevices = repository.bondedDevices
    val isScanning = repository.isScanning

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

    override fun onCleared() {
        super.onCleared()
        repository.close()
    }
}

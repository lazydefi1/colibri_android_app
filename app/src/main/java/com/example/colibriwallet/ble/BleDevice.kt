package com.example.colibriwallet.ble

import android.bluetooth.BluetoothDevice

data class BleDevice(
    val device: BluetoothDevice,
    val name: String?,
    val address: String,
    val rssi: Int,
    val serviceUuids: List<String> = emptyList(),
    val isConnectable: Boolean = true
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: "Unknown Device"

    val shortAddress: String
        get() = address.takeLast(8) // Show last 8 characters of MAC address

    val signalStrength: String
        get() = when {
            rssi >= -50 -> "Excellent"
            rssi >= -60 -> "Good"
            rssi >= -70 -> "Fair"
            rssi >= -80 -> "Weak"
            else -> "Very Weak"
        }

    val hasKnownServices: Boolean
        get() = serviceUuids.any { uuid ->
            knownServices.containsKey(uuid.uppercase())
        }

    val knownServiceNames: List<String>
        get() = serviceUuids.mapNotNull { uuid ->
            knownServices[uuid.uppercase()]
        }

    companion object {
        private val knownServices = mapOf(
            "0000180F-0000-1000-8000-00805F9B34FB" to "Battery Service",
            "0000180A-0000-1000-8000-00805F9B34FB" to "Device Information",
            "00001800-0000-1000-8000-00805F9B34FB" to "Generic Access",
            "00001801-0000-1000-8000-00805F9B34FB" to "Generic Attribute",
            "0000180D-0000-1000-8000-00805F9B34FB" to "Heart Rate",
            "00001812-0000-1000-8000-00805F9B34FB" to "Human Interface Device",
            "31415926-5358-9793-2384-626433832795" to "Colibri Wallet",
            "6E400001-B5A3-F393-E0A9-E50E24DCCA9E" to "Nordic UART Service"
        )
    }
}
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
            rssi >= BleConstants.RssiThresholds.EXCELLENT -> "Excellent"
            rssi >= BleConstants.RssiThresholds.GOOD -> "Good"
            rssi >= BleConstants.RssiThresholds.FAIR -> "Fair"
            rssi >= BleConstants.RssiThresholds.WEAK -> "Weak"
            else -> "Very Weak"
        }

        val hasKnownServices: Boolean
        get() = serviceUuids.any { uuid ->
            BleConstants.KNOWN_SERVICES.containsKey(uuid.uppercase())
        }

    val knownServiceNames: List<String>
        get() = serviceUuids.mapNotNull { uuid ->
            BleConstants.KNOWN_SERVICES[uuid.uppercase()]
        }
}
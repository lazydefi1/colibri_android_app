package com.example.colibriwallet.ble

import java.util.UUID

object BleConstants {

    // Colibri Wallet Service UUIDs
    val COLIBRI_SERVICE_UUID: UUID = UUID.fromString("31415926-5358-9793-2384-626433832795")
    val COLIBRI_WRITE_CHARACTERISTIC_UUID: UUID = shortUuidTo128(0xC001)
    val COLIBRI_NOTIFY_CHARACTERISTIC_UUID: UUID = shortUuidTo128(0xC000)

    // Standard BLE Service UUIDs
    val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    val DEVICE_INFORMATION_SERVICE_UUID: UUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
    val GENERIC_ACCESS_SERVICE_UUID: UUID = UUID.fromString("00001800-0000-1000-8000-00805F9B34FB")
    val GENERIC_ATTRIBUTE_SERVICE_UUID: UUID = UUID.fromString("00001801-0000-1000-8000-00805F9B34FB")
    val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
    val HID_SERVICE_UUID: UUID = UUID.fromString("00001812-0000-1000-8000-00805F9B34FB")
    val NORDIC_UART_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")

    // Known Service Names Mapping
    val KNOWN_SERVICES = mapOf(
        BATTERY_SERVICE_UUID.toString().uppercase() to "Battery Service",
        DEVICE_INFORMATION_SERVICE_UUID.toString().uppercase() to "Device Information",
        GENERIC_ACCESS_SERVICE_UUID.toString().uppercase() to "Generic Access",
        GENERIC_ATTRIBUTE_SERVICE_UUID.toString().uppercase() to "Generic Attribute",
        HEART_RATE_SERVICE_UUID.toString().uppercase() to "Heart Rate",
        HID_SERVICE_UUID.toString().uppercase() to "Human Interface Device",
        COLIBRI_SERVICE_UUID.toString().uppercase() to "Colibri Wallet",
        NORDIC_UART_SERVICE_UUID.toString().uppercase() to "Nordic UART Service"
    )

    // Manufacturer Company IDs
    object ManufacturerIds {
        const val APPLE = 0x004C
        const val MICROSOFT = 0x0006
        const val GOOGLE = 0x00E0
        const val SAMSUNG = 0x0075
        const val BROADCOM = 0x000F
        const val GARMIN = 0x0087
        const val QUALCOMM = 0x01D7
        const val ESPRESSIF = 0x02E5 // ESP32 manufacturer
        const val NORDIC = 0x0059
        const val TEXAS_INSTRUMENTS = 0x000D
        const val INTEL = 0x0002
    }

    // Manufacturer Names Mapping
    val MANUFACTURER_NAMES = mapOf(
        ManufacturerIds.APPLE to "Apple",
        ManufacturerIds.MICROSOFT to "Microsoft",
        ManufacturerIds.GOOGLE to "Google",
        ManufacturerIds.SAMSUNG to "Samsung",
        ManufacturerIds.BROADCOM to "Broadcom",
        ManufacturerIds.GARMIN to "Garmin",
        ManufacturerIds.QUALCOMM to "Qualcomm",
        ManufacturerIds.ESPRESSIF to "Espressif",
        ManufacturerIds.NORDIC to "Nordic Semiconductor",
        ManufacturerIds.TEXAS_INSTRUMENTS to "Texas Instruments",
        ManufacturerIds.INTEL to "Intel"
    )

    // Scan Settings
    object ScanSettings {
        const val SCAN_DURATION_MS = 10000L // 10 seconds
        const val DISCOVERY_TIMEOUT_MS = 15000L // 15 seconds for device discovery
    }

    // Connection Settings
    object ConnectionSettings {
        const val CONNECTION_TIMEOUT_MS = 15000L // 15 seconds
        const val DEFAULT_MTU = 247
        const val RECONNECT_ATTEMPTS = 3
        const val RECONNECT_DELAY_MS = 1000L
        const val RPC_TIMEOUT_MS = 30000L // 30 seconds for RPC calls
        const val MAX_RESPONSE_SIZE = 8192 // 8KB max response size
    }

    // RSSI Thresholds for Signal Strength
    object RssiThresholds {
        const val EXCELLENT = -50
        const val GOOD = -60
        const val FAIR = -70
        const val WEAK = -80
    }

    // Default values for bonded devices
    object BondedDeviceDefaults {
        const val DEFAULT_RSSI = -50
        const val UNKNOWN_RSSI_TEXT = "Bonded device (RSSI unknown)"
    }

    /**
     * Convert a 16-bit UUID to a full 128-bit UUID
     */
    private fun shortUuidTo128(shortUuid: Int): UUID {
        return UUID.fromString("0000${shortUuid.toString(16).padStart(4, '0')}-0000-1000-8000-00805f9b34fb")
    }
}
package com.example.colibriwallet.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// Import constants - using object reference for UUIDs

@Singleton
class BleRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val adapter: BluetoothAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var currentGattCallback: BluetoothGattCallback? = null

    // Response assembly for large responses
    private val responseBuffer = StringBuilder()
    private var expectedResponseEnd = "}"

    // Timestamp formatter for debug messages
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private fun logWithTimestamp(message: String) {
        val timestamp = timeFormat.format(Date())
        _messages.trySend("[$timestamp] $message")
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = flow { for (m in _messages) emit(m) }

    // Device discovery
    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices

    private val _bondedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val bondedDevices: StateFlow<List<BleDevice>> = _bondedDevices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    fun getBondedDevices() {
        try {
            val bondedDevices = adapter.bondedDevices
            val bleDevices = bondedDevices
                .filter { it.type == BluetoothDevice.DEVICE_TYPE_LE || it.type == BluetoothDevice.DEVICE_TYPE_DUAL }
                .map { device ->
                    BleDevice(
                        device = device,
                        name = device.name ?: "Bonded Device",
                        address = device.address,
                        rssi = BleConstants.BondedDeviceDefaults.DEFAULT_RSSI, // Unknown RSSI for bonded devices
                        serviceUuids = emptyList(), // Can't get services without scanning
                        isConnectable = true
                    )
                }
            _bondedDevices.value = bleDevices
            _messages.trySend("Found ${bleDevices.size} bonded BLE devices")
        } catch (e: Exception) {
            _messages.trySend("Error getting bonded devices: ${e.message}")
        }
    }

    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"])
    suspend fun startDeviceDiscovery() {
        if (_isScanning.value) return

        _isScanning.value = true
        _discoveredDevices.value = emptyList()
        _messages.send("Starting BLE device scan...")

        val scanner = adapter.bluetoothLeScanner
        val discoveredDevicesMap = mutableMapOf<String, BleDevice>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val scanRecord = result.scanRecord
                val serviceUuids = scanRecord?.serviceUuids?.map { it.toString() } ?: emptyList()

                                // Try to get device name from multiple sources
                val deviceName = device.name
                    ?: scanRecord?.deviceName
                    ?: getManufacturerName(scanRecord)
                    ?: "Unknown Device"

                // Debug: Log scan record info
                _messages.trySend("Device: ${device.address} | Name: ${device.name} | ScanRecord Name: ${scanRecord?.deviceName} | Manufacturer: ${getManufacturerName(scanRecord)}")

                val bleDevice = BleDevice(
                    device = device,
                    name = deviceName,
                    address = device.address,
                    rssi = result.rssi,
                    serviceUuids = serviceUuids,
                    isConnectable = result.isConnectable
                )

                // Update or add device (use address as key to avoid duplicates)
                discoveredDevicesMap[device.address] = bleDevice
                _discoveredDevices.value = discoveredDevicesMap.values.sortedByDescending { it.rssi }
            }

            override fun onScanFailed(errorCode: Int) {
                _isScanning.value = false
                _messages.trySend("Scan failed with error: $errorCode")
            }
        }

        try {
            scanner.startScan(callback)

            // Scan for configured duration
            kotlinx.coroutines.delay(BleConstants.ScanSettings.SCAN_DURATION_MS)

            scanner.stopScan(callback)
            _isScanning.value = false
            _messages.send("Scan completed. Found ${discoveredDevicesMap.size} devices.")

        } catch (e: Exception) {
            scanner.stopScan(callback)
            _isScanning.value = false
            _messages.send("Scan error: ${e.message}")
        }
    }

    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"])
    suspend fun connectAndSendListMethods() {
        if (!_connect()) return
        val response = sendRpc("""{"method":"listMethods"}""")
        _messages.send(response)
    }

    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"])
    suspend fun connectToSpecificDevice(device: BleDevice) {
        _messages.send("Starting connection to ${device.displayName} (${device.address})")

        if (gatt != null) {
            _messages.send("Already connected to a device, disconnecting first...")
            gatt?.close()
            gatt = null
        }

        _connectionState.value = ConnectionState.Connecting
        _messages.send("Initiating GATT connection...")

        val success = connectToDevice(device.device)
        if (success) {
            _messages.send("Successfully connected to ${device.displayName}")
            _messages.send("Connection established and services discovered")
        } else {
            _messages.send("Failed to connect to ${device.displayName}")
        }
    }

    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"])
    private suspend fun connectToDevice(device: BluetoothDevice): Boolean {
        _messages.send("Attempting to connect to device: ${device.address}")

        return suspendCancellableCoroutine { cont ->
            val connectionCallback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        when (newState) {
                            BluetoothGatt.STATE_CONNECTED -> {
                                _messages.trySend("GATT connection established, requesting larger MTU...")
                                // Request larger MTU to handle bigger responses
                                val mtuRequested = gatt.requestMtu(512)
                                if (!mtuRequested) {
                                    _messages.trySend("Failed to request MTU, proceeding with default")
                                    gatt.discoverServices()
                                }
                            }
                            BluetoothGatt.STATE_DISCONNECTED -> {
                                _messages.trySend("Device disconnected")
                                _connectionState.value = ConnectionState.Failed("Disconnected")
                                cont.resume(false) {}
                            }
                            BluetoothGatt.STATE_CONNECTING -> {
                                _messages.trySend("Connecting to GATT server...")
                            }
                        }
                    }

                    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            _messages.trySend("✅ MTU negotiated: $mtu bytes")
                            _messages.trySend("📡 Max notification size: ${mtu - 3} bytes")
                        } else {
                            _messages.trySend("❌ MTU negotiation failed (status: $status), using default 20 bytes")
                        }
                        _messages.trySend("Discovering services...")
                        gatt.discoverServices()
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        _messages.trySend("Services discovered, looking for Colibri service...")

                        var colibriServiceFound = false
                        for (service in gatt.services) {
                            _messages.trySend("Found service: ${service.uuid}")
                            if (service.uuid == BleConstants.COLIBRI_SERVICE_UUID) {
                                colibriServiceFound = true
                                _messages.trySend("Colibri service found! Looking for characteristics...")

                                for (ch in service.characteristics) {
                                    _messages.trySend("Found characteristic: ${ch.uuid}")
                                    when (ch.uuid) {
                                        BleConstants.COLIBRI_WRITE_CHARACTERISTIC_UUID -> {
                                            writeChar = ch
                                            _messages.trySend("Write characteristic configured")
                                        }
                                                                BleConstants.COLIBRI_NOTIFY_CHARACTERISTIC_UUID -> {
                            notifyChar = ch
                            _messages.trySend("Notify characteristic found, enabling notifications...")
                            gatt.setCharacteristicNotification(ch, true)

                            // Enable notifications on the descriptor
                            val descriptor = ch.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                            if (descriptor != null) {
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(descriptor)
                                _messages.trySend("Notification descriptor enabled")
                            } else {
                                _messages.trySend("Warning: Could not find notification descriptor")
                            }
                        }
                                    }
                                }
                            }
                        }

                        if (!colibriServiceFound) {
                            _messages.trySend("Colibri service not found on device")
                            _connectionState.value = ConnectionState.Failed("Colibri service not found")
                            cont.resume(false) {}
                            return
                        }

                        if (writeChar != null && notifyChar != null) {
                            _messages.trySend("All required characteristics found - connection ready!")
                            _connectionState.value = ConnectionState.Connected
                            cont.resume(true) {}
                        } else {
                            val missing = mutableListOf<String>()
                            if (writeChar == null) missing.add("write")
                            if (notifyChar == null) missing.add("notify")
                            _messages.trySend("Missing characteristics: ${missing.joinToString(", ")}")
                            _connectionState.value = ConnectionState.Failed("Missing characteristics: ${missing.joinToString(", ")}")
                            cont.resume(false) {}
                        }
                    }

                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    // Delegate to current RPC callback if available
                    currentGattCallback?.onCharacteristicChanged(gatt, characteristic)
                }

                override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                    // Delegate to current RPC callback if available
                    currentGattCallback?.onCharacteristicWrite(gatt, characteristic, status)
                }

                override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                    // Delegate to current RPC callback if available
                    currentGattCallback?.onDescriptorWrite(gatt, descriptor, status)
                }
            }

            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, connectionCallback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M)
            } else {
                device.connectGatt(context, false, connectionCallback)
            }
        }
    }

    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"])
    private suspend fun _connect(): Boolean {
        if (gatt != null) return true
        _connectionState.value = ConnectionState.Connecting
        val scanner = adapter.bluetoothLeScanner
        var device: BluetoothDevice? = null
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.scanRecord?.serviceUuids?.contains(ParcelUuid(BleConstants.COLIBRI_SERVICE_UUID)) == true) {
                    device = result.device
                }
            }
        }
        scanner.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(BleConstants.COLIBRI_SERVICE_UUID)).build()),
            ScanSettings.Builder().build(), callback)
        withTimeout(BleConstants.ConnectionSettings.CONNECTION_TIMEOUT_MS) {
            while (device == null) kotlinx.coroutines.delay(500)
        }
        scanner.stopScan(callback)
        val dev = device ?: return false.also {
            _connectionState.value = ConnectionState.Failed("Device not found")
        }
        return connectToDevice(dev)
    }

    suspend fun sendRpc(json: String): String {
        val char = writeChar ?: return """{"error": "No write characteristic available"}"""
        val g = gatt ?: return """{"error": "No GATT connection available"}"""

        logWithTimestamp("Sending RPC command: $json")

        val data = json.toByteArray(Charset.forName("UTF-8"))
        char.value = data

        // Clear any previous response buffer
        responseBuffer.clear()

        return try {
            withTimeout(30000) { // 30 second timeout for large responses
                suspendCancellableCoroutine { cont ->
                    // Create a new callback for this RPC call
                    currentGattCallback = object : BluetoothGattCallback() {
                                                    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                                if (characteristic.uuid == BleConstants.COLIBRI_NOTIFY_CHARACTERISTIC_UUID) {
                                    val chunk = String(characteristic.value, Charsets.UTF_8)
                                    logWithTimestamp("📥 Notification received: ${characteristic.value.size} bytes")
                                    logWithTimestamp("Raw bytes: ${characteristic.value.joinToString(" ") { "%02x".format(it) }}")
                                    logWithTimestamp("Chunk content: '$chunk'")

                                    responseBuffer.append(chunk)

                                    // Check if this appears to be the end of the response
                                    val currentResponse = responseBuffer.toString()
                                    logWithTimestamp("📊 Buffer now has ${currentResponse.length} bytes total")
                                    logWithTimestamp("Checking if response is complete...")
                                    if (isCompleteJsonResponse(currentResponse)) {
                                        logWithTimestamp("✅ Complete response detected! (${currentResponse.length} bytes)")
                                        logWithTimestamp("Full response: $currentResponse")
                                        cont.resume(currentResponse) {}
                                    } else {
                                        logWithTimestamp("⏳ Still incomplete: ${currentResponse.length} bytes")
                                        logWithTimestamp("⏳ Waiting for more notifications...")
                                    }
                                }
                            }

                        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                logWithTimestamp("Command written successfully, waiting for response...")
                            } else {
                                logWithTimestamp("Failed to write command (status: $status)")
                                cont.resume("""{"error": "Failed to write command", "status": $status}""") {}
                            }
                        }

                        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                _messages.trySend("Notifications properly enabled")
                            } else {
                                _messages.trySend("Failed to enable notifications (status: $status)")
                            }
                        }
                    }

                    logWithTimestamp("Writing command to device...")
                    val writeSuccess = g.writeCharacteristic(char)
                    if (!writeSuccess) {
                        logWithTimestamp("Failed to initiate write operation")
                        cont.resume("""{"error": "Failed to initiate write operation"}""") {}
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            val partialResponse = responseBuffer.toString()
            logWithTimestamp("⏰ TIMEOUT after 30s")
            logWithTimestamp("⏰ Partial response received: ${partialResponse.length} bytes")
            logWithTimestamp("⏰ Content: '$partialResponse'")
            responseBuffer.clear()
            """{"error": "RPC call timed out", "partial_response": "$partialResponse", "bytes_received": ${partialResponse.length}}"""
        } catch (e: Exception) {
            val partialResponse = responseBuffer.toString()
            logWithTimestamp("❌ RPC call failed: ${e.message}")
            logWithTimestamp("❌ Partial response received: ${partialResponse.length} bytes")
            responseBuffer.clear()
            """{"error": "RPC call failed", "message": "${e.message}", "partial_response": "$partialResponse"}"""
        }
    }

    private fun isCompleteJsonResponse(response: String): Boolean {
        if (response.isEmpty()) {
            logWithTimestamp("🔍 JSON check: Empty response")
            return false
        }

        try {
            // Enhanced JSON validation - count braces and brackets
            var braceCount = 0
            var bracketCount = 0
            var inString = false
            var escaped = false

            for (char in response) {
                when {
                    escaped -> escaped = false
                    char == '\\' && inString -> escaped = true
                    char == '"' && !escaped -> inString = !inString
                    !inString && char == '{' -> braceCount++
                    !inString && char == '}' -> braceCount--
                    !inString && char == '[' -> bracketCount++
                    !inString && char == ']' -> bracketCount--
                }
            }

            // Response is complete if all braces and brackets are balanced
            // and it ends with } or ]
            val trimmed = response.trim()
            val isBalanced = braceCount == 0 && bracketCount == 0
            val endsCorrectly = trimmed.endsWith('}') || trimmed.endsWith(']')

            logWithTimestamp("🔍 JSON check: braces=$braceCount, brackets=$bracketCount, balanced=$isBalanced, ends_correctly=$endsCorrectly")
            logWithTimestamp("🔍 Last 20 chars: '${response.takeLast(20)}'")

            return isBalanced && endsCorrectly && trimmed.isNotEmpty()
        } catch (e: Exception) {
            logWithTimestamp("🔍 JSON parsing error: ${e.message}")
            // If JSON parsing fails, assume it's incomplete
            return false
        }
    }

    private fun getManufacturerName(scanRecord: ScanRecord?): String? {
        scanRecord?.manufacturerSpecificData?.let { manufacturerData ->
            // Check if the SparseArray has any elements before accessing
            if (manufacturerData.size() > 0) {
                val companyId = manufacturerData.keyAt(0)
                return BleConstants.MANUFACTURER_NAMES[companyId]
                    ?: "Manufacturer ID: 0x${companyId.toString(16).uppercase()}"
            }
        }
        return null
    }

    fun close() {
        currentGattCallback = null
        responseBuffer.clear()
        gatt?.close()
        gatt = null
        writeChar = null
        notifyChar = null
        _connectionState.value = ConnectionState.Disconnected
    }
}

